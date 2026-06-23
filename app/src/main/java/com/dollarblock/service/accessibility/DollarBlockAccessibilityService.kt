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

        if (packageName == getPackageName()) {
            // DollarBlock entrou em foreground — cancela rastreamento do app anterior
            // para não disparar bloqueio por cima do próprio app.
            lastForegroundPackage = packageName
            stopTracking()
            return
        }

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
                // Só cancela o rastreamento se o app monitorado foi realmente substituído
                // (não cancela por overlays/IME, que não disparam novo evento ao fechar).
                // scheduleTracking já cuida disso: se outro app monitorado entrar em foreground,
                // ele chama stopTracking() explicitamente.
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
                handler.post { scheduleTracking(packageName, enteredForegroundAt) }
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
     * Polling adaptativo: verifica uso e limite a cada [MIN_POLL_INTERVAL_MS]–[MAX_POLL_INTERVAL_MS].
     * Intervalo reduz conforme o limite se aproxima. Robusto contra overlays/IME que cancelavam
     * o timer one-shot anterior: mesmo que um evento de outro pacote cancele este job, o próximo
     * evento do app monitorado reinicia o rastreamento (via onAccessibilityEvent).
     */
    private fun scheduleTracking(packageName: String, foregroundSinceMillis: Long) {
        stopTracking()
        trackedPackage = packageName
        trackingJob = scope.launch {
            while (isActive) {
                val app = monitoredAppDao.getByPackage(packageName) ?: break
                if (!app.isMonitored) break
                val limitMillis = (app.dailyLimitMinutes ?: break) * 60_000L

                val usedMillis = effectiveUsageMillis(app, foregroundSinceMillis)
                monitoredAppRepository.syncTodayUsage(packageName, foregroundSinceMillis)

                if (usedMillis >= limitMillis && blockPreferences.isUnlockWindowExpired(packageName)) {
                    handler.post {
                        assertBlock(packageName) { blockPreferences.isUnlockWindowExpired(packageName) }
                    }
                    break
                }

                val sleepMs = when {
                    usedMillis >= limitMillis -> {
                        // Dentro da janela de desbloqueio — aguarda expirar
                        blockPreferences.unlockWindowRemainingMillis(packageName)
                            .coerceIn(MIN_POLL_INTERVAL_MS, MAX_POLL_INTERVAL_MS)
                    }
                    else -> (limitMillis - usedMillis).coerceIn(MIN_POLL_INTERVAL_MS, MAX_POLL_INTERVAL_MS)
                }
                delay(sleepMs)
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
        const val MIN_POLL_INTERVAL_MS = 3_000L
        const val MAX_POLL_INTERVAL_MS = 30_000L
    }
}
