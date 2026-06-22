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

    private var pollingPackage: String? = null
    private var pollingJob: Job? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "Service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val packageName = event.packageName?.toString() ?: return
        if (packageName == getPackageName()) {
            stopPolling()
            return
        }

        if (blockPreferences.shouldBlock(packageName)) {
            stopPolling()
            assertBlock(packageName) { blockPreferences.shouldBlock(packageName) }
            return
        }

        // Momento exato em que esta janela do app apareceu — início da sessão de foreground.
        val enteredForegroundAt = System.currentTimeMillis()

        // Limite diário: requer IO (Room + UsageStatsManager), checado de forma assíncrona.
        scope.launch {
            val app = monitoredAppDao.getByPackage(packageName)
            if (app == null || !app.isMonitored) {
                handler.post { stopPolling() }
                return@launch
            }

            if (isOverDailyLimit(app, enteredForegroundAt) && blockPreferences.isUnlockWindowExpired(packageName)) {
                handler.post {
                    stopPolling()
                    assertBlock(packageName) { blockPreferences.isUnlockWindowExpired(packageName) }
                }
            } else {
                // App monitorado, ainda dentro do limite (ou sem limite definido): mantém o
                // polling enquanto ele continuar em foreground, para pegar o momento exato
                // em que o limite for cruzado e manter o uso sincronizado em tempo real.
                handler.post { startPollingIfNeeded(packageName, enteredForegroundAt) }
            }
        }
    }

    /**
     * [foregroundSinceMillis] é o momento em que [app] entrou em foreground *nesta* sessão
     * (troca de janela mais recente) — usado para somar o tempo da sessão em andamento ao
     * uso já fechado em `UsageStatsManager`, que por si só fica defasado enquanto o app
     * permanece aberto sem trocar de tela.
     */
    private suspend fun isOverDailyLimit(app: MonitoredAppEntity, foregroundSinceMillis: Long?): Boolean {
        val limit = app.dailyLimitMinutes ?: return false
        val usedMinutes = usageStatsProvider.getTodayUsageMinutesIncludingOngoingSession(
            packageName = app.packageName,
            foregroundSinceMillis = foregroundSinceMillis,
        )
        return usedMinutes >= limit
    }

    /** Inicia o polling para [packageName] se ainda não estiver rodando para ele. */
    private fun startPollingIfNeeded(packageName: String, foregroundSinceMillis: Long) {
        if (pollingPackage == packageName && pollingJob?.isActive == true) return
        stopPolling()
        pollingPackage = packageName
        pollingJob = scope.launch {
            while (isActive) {
                delay(POLL_INTERVAL_MS)
                monitoredAppRepository.syncTodayUsage(packageName, foregroundSinceMillis)
                val app = monitoredAppDao.getByPackage(packageName)
                if (app != null && app.isMonitored &&
                    isOverDailyLimit(app, foregroundSinceMillis) &&
                    blockPreferences.isUnlockWindowExpired(packageName)
                ) {
                    handler.post {
                        assertBlock(packageName) { blockPreferences.isUnlockWindowExpired(packageName) }
                    }
                    break
                }
            }
        }
    }

    private fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
        pollingPackage = null
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
        const val POLL_INTERVAL_MS = 3_000L
    }
}
