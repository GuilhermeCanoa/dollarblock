package com.dollarblock.service.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.dollarblock.data.local.db.MonitoredAppDao
import com.dollarblock.data.local.db.MonitoredAppEntity
import com.dollarblock.data.local.prefs.BlockPreferences
import com.dollarblock.data.usage.UsageStatsProvider
import com.dollarblock.domain.repository.EventsRepository
import com.dollarblock.domain.repository.MonitoredAppRepository
import com.dollarblock.feature.blocking.BlockActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Detecta o app em primeiro plano. Bloqueia (abre [BlockActivity] por cima) em dois casos:
 * 1. Bloqueio manual: app presente em [BlockPreferences] (toggle na Home), fora de uma
 *    janela de desbloqueio ativa — checagem síncrona, sem custo de IO.
 * 2. Limite diário atingido: app monitorado (`MonitoredAppEntity.isMonitored`) com
 *    `dailyLimitMinutes` definido e uso de hoje (`UsageStatsProvider`) ≥ limite —
 *    checagem assíncrona (Room + UsageStatsManager), também respeitando a janela de
 *    desbloqueio ativa (pagar libera os dois tipos de bloqueio igualmente).
 *
 * O bloqueio é re-afirmado a cada mudança de janela do app (sem debounce no lançamento)
 * e uma vez mais com atraso, para vencer a corrida com as transições de inicialização do
 * app (ex.: splash → tela inicial num cold start). O registro do evento é deduplicado.
 *
 * Enquanto um app **monitorado** permanece em foreground (sem trocar de janela — ex.:
 * rolando o feed de um app por minutos), nenhum [AccessibilityEvent] novo chega. Por isso
 * o serviço também mantém um polling a cada [POLL_INTERVAL_MS] enquanto esse app estiver
 * aberto: sincroniza o uso de hoje (`MonitoredAppRepository.syncTodayUsage`, que atualiza o
 * Room observado pela tela Apps) e reavalia o limite, bloqueando sem precisar fechar/reabrir.
 */
@AndroidEntryPoint
class DollarBlockAccessibilityService : AccessibilityService() {

    @Inject
    lateinit var blockPreferences: BlockPreferences

    @Inject
    lateinit var eventsRepository: EventsRepository

    @Inject
    lateinit var monitoredAppDao: MonitoredAppDao

    @Inject
    lateinit var usageStatsProvider: UsageStatsProvider

    @Inject
    lateinit var monitoredAppRepository: MonitoredAppRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val handler = Handler(Looper.getMainLooper())

    private var lastRecordedPackage: String? = null
    private var lastRecordedAt = 0L

    private var lastForegroundPackage: String? = null
    private var trackedPackage: String? = null
    private var trackingJob: Job? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "Service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val packageName = event.packageName?.toString() ?: return

        if (packageName == getPackageName()) return

        // Ignora mudanças internas dentro do mesmo app — só processa quando o package muda.
        val packageChanged = packageName != lastForegroundPackage
        lastForegroundPackage = packageName

        if (blockPreferences.shouldBlock(packageName)) {
            stopTracking()
            assertBlock(packageName) { blockPreferences.shouldBlock(packageName) }
            return
        }

        if (!packageChanged) return

        val enteredForegroundAt = System.currentTimeMillis()

        scope.launch {
            val app = monitoredAppDao.getByPackage(packageName)
            if (app == null || !app.isMonitored) {
                handler.post { stopTracking() }
                return@launch
            }

            val usedMillis = effectiveUsageMillis(app, enteredForegroundAt)
            val limitMillis = (app.dailyLimitMinutes ?: return@launch) * 60_000L

            if (usedMillis >= limitMillis && blockPreferences.isUnlockWindowExpired(packageName)) {
                handler.post {
                    stopTracking()
                    assertBlock(packageName) { blockPreferences.isUnlockWindowExpired(packageName) }
                }
            } else {
                val remainingMillis = if (usedMillis >= limitMillis) {
                    blockPreferences.unlockWindowRemainingMillis(packageName)
                } else {
                    limitMillis - usedMillis
                }.coerceAtLeast(MIN_CHECK_INTERVAL_MS)
                handler.post { scheduleTracking(packageName, enteredForegroundAt, remainingMillis) }
            }
        }
    }

    /**
     * Uso efetivo em millis = uso bruto via UsageEvents − baseline capturado na ativação.
     * [foregroundSinceMillis] inclui a sessão em andamento no cálculo.
     */
    private suspend fun effectiveUsageMillis(app: MonitoredAppEntity, foregroundSinceMillis: Long?): Long {
        val raw = usageStatsProvider.getTodayUsageMillisViaEvents(
            packageName = app.packageName,
            ongoingSessionSince = foregroundSinceMillis,
        )
        return (raw - app.usageBaselineMillis).coerceAtLeast(0L)
    }

    /**
     * Agenda uma verificação de limite daqui a [delayMillis].
     * Quando o timer dispara, recalcula o uso real via UsageEvents — se ainda não atingiu
     * o limite (ex.: usuário saiu do app durante o delay), reagenda pelo tempo restante.
     * Substitui o polling contínuo por um único disparo preciso.
     */
    private fun scheduleTracking(packageName: String, foregroundSinceMillis: Long, delayMillis: Long) {
        stopTracking()
        trackedPackage = packageName
        trackingJob = scope.launch {
            // Sincroniza Room para a UI enquanto espera o limite
            monitoredAppRepository.syncTodayUsage(packageName, foregroundSinceMillis)
            delay(delayMillis)
            if (!isActive) return@launch

            val app = monitoredAppDao.getByPackage(packageName) ?: return@launch
            if (!app.isMonitored) return@launch

            val usedMillis = effectiveUsageMillis(app, foregroundSinceMillis)
            val limitMillis = (app.dailyLimitMinutes ?: return@launch) * 60_000L

            if (usedMillis >= limitMillis && blockPreferences.isUnlockWindowExpired(packageName)) {
                monitoredAppRepository.syncTodayUsage(packageName, foregroundSinceMillis)
                handler.post {
                    assertBlock(packageName) { blockPreferences.isUnlockWindowExpired(packageName) }
                }
            } else if (usedMillis >= limitMillis) {
                // Acima do limite mas dentro da janela de desbloqueio — aguarda a janela expirar
                val windowRemaining = blockPreferences.unlockWindowRemainingMillis(packageName)
                    .coerceAtLeast(MIN_CHECK_INTERVAL_MS)
                handler.post { scheduleTracking(packageName, foregroundSinceMillis, windowRemaining) }
            } else {
                // Ainda não atingiu o limite — agenda para quando vai atingir
                val remaining = (limitMillis - usedMillis).coerceAtLeast(MIN_CHECK_INTERVAL_MS)
                handler.post { scheduleTracking(packageName, foregroundSinceMillis, remaining) }
            }
        }
    }

    private fun stopTracking() {
        trackingJob?.cancel()
        trackingJob = null
        trackedPackage = null
    }

    /**
     * Abre a tela de bloqueio e a re-afirma após [REASSERT_DELAY_MS]. [stillBlocked] decide
     * se a re-asserção ainda se aplica — cada chamador passa a checagem correspondente ao
     * motivo do bloqueio (manual ou limite diário), já que pagar libera a janela para ambos.
     */
    private fun assertBlock(packageName: String, stillBlocked: () -> Boolean) {
        val label = resolveLabel(packageName)

        // Re-afirma imediatamente (cobre o app assim que ele aparece).
        launchBlockScreen(packageName, label)
        recordBlockOnce(packageName, label)

        // Re-afirma após o app concluir suas transições de inicialização (cold start).
        handler.postDelayed({
            if (stillBlocked()) {
                launchBlockScreen(packageName, label)
            }
        }, REASSERT_DELAY_MS)
    }

    private fun launchBlockScreen(packageName: String, label: String) {
        val intent = Intent(this, BlockActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            putExtra(BlockActivity.EXTRA_LABEL, label)
            putExtra(BlockActivity.EXTRA_PACKAGE, packageName)
        }
        startActivity(intent)
    }

    private fun recordBlockOnce(packageName: String, label: String) {
        val now = SystemClock.elapsedRealtime()
        if (packageName == lastRecordedPackage && now - lastRecordedAt < RECORD_DEDUPE_MS) return
        lastRecordedPackage = packageName
        lastRecordedAt = now
        scope.launch { eventsRepository.recordBlock(packageName, label) }
        Log.i(TAG, "Blocked $packageName ($label)")
    }

    private fun resolveLabel(packageName: String): String = runCatching {
        packageManager.getApplicationLabel(
            packageManager.getApplicationInfo(packageName, 0),
        ).toString()
    }.getOrDefault(packageName)

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        scope.cancel()
    }

    private companion object {
        const val TAG = "DollarBlockA11y"
        const val REASSERT_DELAY_MS = 500L
        const val RECORD_DEDUPE_MS = 5_000L
        const val MIN_CHECK_INTERVAL_MS = 5_000L
    }
}
