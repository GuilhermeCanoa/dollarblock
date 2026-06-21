package com.dollarblock.service.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.dollarblock.data.local.prefs.BlockPreferences
import com.dollarblock.domain.repository.EventsRepository
import com.dollarblock.feature.blocking.BlockActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Detecta o app em primeiro plano. Se ele estiver bloqueado e fora de uma janela de
 * desbloqueio ativa, abre a [BlockActivity] do DollarBlock por cima.
 *
 * O bloqueio é re-afirmado a cada mudança de janela do app (sem debounce no lançamento)
 * e uma vez mais com atraso, para vencer a corrida com as transições de inicialização do
 * app (ex.: splash → tela inicial num cold start). O registro do evento é deduplicado.
 */
@AndroidEntryPoint
class DollarBlockAccessibilityService : AccessibilityService() {

    @Inject
    lateinit var blockPreferences: BlockPreferences

    @Inject
    lateinit var eventsRepository: EventsRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val handler = Handler(Looper.getMainLooper())

    private var lastRecordedPackage: String? = null
    private var lastRecordedAt = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "Service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val packageName = event.packageName?.toString() ?: return
        if (packageName == getPackageName()) return
        if (!blockPreferences.shouldBlock(packageName)) return

        val label = resolveLabel(packageName)

        // Re-afirma imediatamente (cobre o app assim que ele aparece).
        launchBlockScreen(packageName, label)
        recordBlockOnce(packageName, label)

        // Re-afirma após o app concluir suas transições de inicialização (cold start).
        handler.postDelayed({
            if (blockPreferences.shouldBlock(packageName)) {
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
    }
}
