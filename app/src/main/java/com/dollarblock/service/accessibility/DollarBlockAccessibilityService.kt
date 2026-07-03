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
import com.dollarblock.domain.model.BlockReason
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
 * Detecta o app em primeiro plano e bloqueia (abre [BlockActivity]) quando um app
 * monitorado atinge o limite diário de uso e não há passe do dia ativo.
 *
 * O passe do dia (pós-pagamento) expira por wall-clock na meia-noite local
 * ([BlockPreferences.UnlockGrant.unlockUntilMs]) — basta comparar com o relógio.
 */
@AndroidEntryPoint
class DollarBlockAccessibilityService : AccessibilityService() {

    @Inject lateinit var blockPreferences: BlockPreferences
    @Inject lateinit var eventsRepository: EventsRepository
    @Inject lateinit var monitoredAppDao: MonitoredAppDao
    @Inject lateinit var usageStatsProvider: UsageStatsProvider
    @Inject lateinit var monitoredAppRepository: MonitoredAppRepository
    @Inject lateinit var limitWarningNotifier: LimitWarningNotifier

    /** Último uso observado por app, para detectar o cruzamento do limiar de 5 min ([LimitWarningPolicy]). */
    private val lastUsedMillisByPackage = mutableMapOf<String, Long>()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val handler = Handler(Looper.getMainLooper())

    private var lastRecordedPackage: String? = null
    private var lastRecordedAt = 0L

    private var lastForegroundPackage: String? = null
    private var trackedPackage: String? = null
    private var trackingJob: Job? = null

    private val launcherPackage: String by lazy {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        packageManager.resolveActivity(home, 0)?.activityInfo?.packageName.orEmpty()
    }

    /**
     * Filtra eventos de overlays de sistema, IME e serviços que não são apps reais.
     * Sem esse filtro, qualquer TYPE_WINDOW_STATE_CHANGED de um serviço do sistema
     * corromperia lastForegroundPackage e impediria o bloqueio no tracking loop.
     */
    private fun isRealApp(packageName: String): Boolean {
        if (packageName == launcherPackage) return true
        return packageManager.getLaunchIntentForPackage(packageName) != null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "Service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val packageName = event.packageName?.toString() ?: return

        if (packageName == getPackageName()) {
            lastForegroundPackage = packageName
            stopTracking()
            return
        }

        // Ignora overlays, IME, painéis de notificação — não são apps reais e
        // corromperiam lastForegroundPackage, quebrando o check no tracking loop.
        if (!isRealApp(packageName)) return

        val packageChanged = packageName != lastForegroundPackage
        lastForegroundPackage = packageName

        if (!packageChanged) return

        val enteredForegroundAt = System.currentTimeMillis()

        scope.launch {
            val app = monitoredAppDao.getByPackage(packageName)
            if (app == null || !app.isMonitored) return@launch

            val usedMillis = effectiveUsageMillis(app, enteredForegroundAt)
            val limitMillis = (app.dailyLimitMinutes ?: return@launch) * 60_000L

            if (usedMillis >= limitMillis) {
                val unlockActive = isUnlockActive(packageName)
                handler.post {
                    if (!unlockActive) {
                        stopTracking()
                        assertBlock(packageName) { !isUnlockActiveSync(packageName) }
                    } else {
                        scheduleTracking(packageName, enteredForegroundAt)
                    }
                }
            } else {
                handler.post { scheduleTracking(packageName, enteredForegroundAt) }
            }
        }
    }

    /**
     * Polling adaptativo: sincroniza uso e avalia limite a cada iteração.
     * Quando o limite é atingido, verifica se há janela de desbloqueio ativa (via UsageStats).
     * O bloqueio só é disparado visualmente se o app ainda estiver em foreground (Bug 1 fix).
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

                val previousUsedMillis = lastUsedMillisByPackage[packageName] ?: 0L
                if (LimitWarningPolicy.shouldWarn(previousUsedMillis, usedMillis, limitMillis)) {
                    limitWarningNotifier.notifyLimitApproaching(resolveLabel(packageName))
                }
                lastUsedMillisByPackage[packageName] = usedMillis

                if (usedMillis >= limitMillis) {
                    val grant = blockPreferences.getUnlockGrant(packageName)
                    val now = System.currentTimeMillis()
                    if (grant == null || now >= grant.unlockUntilMs) {
                        // Sem passe do dia ativo → bloqueia se o app estiver em foreground.
                        // Não damos break: se o usuário fechar a BlockActivity e continuar no
                        // app monitorado, o loop precisa continuar reafirmando o bloqueio.
                        handler.post {
                            if (lastForegroundPackage == packageName) {
                                assertBlock(packageName) { !isUnlockActiveSync(packageName) }
                            }
                        }
                        delay(REASSERT_POLL_INTERVAL_MS)
                        continue
                    }

                    // Passe do dia ativo — dorme até perto da meia-noite
                    val remaining = grant.unlockUntilMs - now
                    delay(remaining.coerceIn(MIN_POLL_INTERVAL_MS, MAX_POLL_INTERVAL_MS))
                } else {
                    delay((limitMillis - usedMillis).coerceIn(MIN_POLL_INTERVAL_MS, MAX_POLL_INTERVAL_MS))
                }
            }
        }
    }

    /** Passe do dia ativo = ainda não chegou a meia-noite local do dia do pagamento. */
    private fun isUnlockActive(packageName: String): Boolean {
        val grant = blockPreferences.getUnlockGrant(packageName) ?: return false
        return System.currentTimeMillis() < grant.unlockUntilMs
    }

    /** Mantida como alias: a checagem já é síncrona (wall-clock puro). */
    private fun isUnlockActiveSync(packageName: String): Boolean = isUnlockActive(packageName)

    private fun stopTracking() {
        trackingJob?.cancel()
        trackingJob = null
        trackedPackage = null
    }

    /**
     * Uso do dia em millis, 100% via UsageEvents (sem baseline/ajuste).
     */
    private suspend fun effectiveUsageMillis(
        app: MonitoredAppEntity,
        foregroundSinceMillis: Long?,
    ): Long = usageStatsProvider.getTodayUsageMillisViaEvents(
        packageName = app.packageName,
        ongoingSessionSince = foregroundSinceMillis,
    )

    private fun assertBlock(packageName: String, stillBlocked: () -> Boolean) {
        val label = resolveLabel(packageName)
        launchBlockScreen(packageName, label)
        recordBlockOnce(packageName, label)
        handler.postDelayed({
            if (stillBlocked()) launchBlockScreen(packageName, label)
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
        scope.launch { eventsRepository.recordBlock(packageName, label, BlockReason.DAILY_LIMIT) }
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
        const val REASSERT_POLL_INTERVAL_MS = 2_000L
    }
}
