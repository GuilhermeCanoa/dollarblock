package com.dollarblock.service.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
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
 */
@AndroidEntryPoint
class DollarBlockAccessibilityService : AccessibilityService() {

    @Inject
    lateinit var blockPreferences: BlockPreferences

    @Inject
    lateinit var eventsRepository: EventsRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var lastBlockedPackage: String? = null
    private var lastBlockAt = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "Service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val packageName = event.packageName?.toString() ?: return
        if (packageName == getPackageName()) return
        if (!blockPreferences.shouldBlock(packageName)) return

        // Evita relançar a tela de bloqueio repetidamente para o mesmo app.
        val now = SystemClock.elapsedRealtime()
        if (packageName == lastBlockedPackage && now - lastBlockAt < DEBOUNCE_MS) return
        lastBlockedPackage = packageName
        lastBlockAt = now

        val label = runCatching {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(packageName, 0),
            ).toString()
        }.getOrDefault(packageName)

        val intent = Intent(this, BlockActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            putExtra(BlockActivity.EXTRA_LABEL, label)
            putExtra(BlockActivity.EXTRA_PACKAGE, packageName)
        }
        startActivity(intent)
        scope.launch { eventsRepository.recordBlock(packageName, label) }
        Log.i(TAG, "Blocked $packageName ($label)")
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private companion object {
        const val TAG = "DollarBlockA11y"
        const val DEBOUNCE_MS = 1500L
    }
}
