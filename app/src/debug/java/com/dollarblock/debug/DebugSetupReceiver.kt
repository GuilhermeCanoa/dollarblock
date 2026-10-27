package com.dollarblock.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.dollarblock.data.local.prefs.NudgePreferences
import com.dollarblock.domain.repository.MonitoredAppRepository
import com.dollarblock.service.nudge.Nudge
import com.dollarblock.service.nudge.NudgeEngine
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Receiver **exclusivo de builds debug** (mora em `src/debug/`, não existe no release).
 * Permite ao script `scripts/smoke-test.ps1` montar um cenário de teste — marcar um app
 * como monitorado e definir um limite diário — via `adb am broadcast`, sem precisar
 * navegar pela UI.
 *
 * Uso:
 * ```
 * adb shell am broadcast -a com.dollarblock.DEBUG_SET_LIMIT \
 *     --es pkg com.android.chrome --ei limit 1 -p com.dollarblock
 * ```
 *
 * Também só no debug, `BlockActivity` é exportada (`src/debug/AndroidManifest.xml`) para
 * abrir a tela de bloqueio direto, sem estourar um limite de verdade:
 * ```
 * adb shell am start -n com.dollarblock/.feature.blocking.BlockActivity \
 *     --es extra_label Instagram --es extra_package com.instagram.android
 * ```
 */
@AndroidEntryPoint
class DebugSetupReceiver : BroadcastReceiver() {

    @Inject lateinit var monitoredAppRepository: MonitoredAppRepository
    @Inject lateinit var nudgeEngine: NudgeEngine
    @Inject lateinit var nudgePreferences: NudgePreferences

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_NUDGE -> return onNudge(intent)
            ACTION_NUDGE_SESSION -> return onNudgeSession(intent)
            ACTION_SET_LIMIT -> Unit
            else -> return
        }
        val pkg = intent.getStringExtra(EXTRA_PKG) ?: return
        val limit = intent.getIntExtra(EXTRA_LIMIT, -1).takeIf { it >= 0 }

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                monitoredAppRepository.setMonitored(pkg, pkg, isMonitored = true)
                monitoredAppRepository.setDailyLimit(pkg, limit)
                Log.i(TAG, "Debug setup: $pkg monitorado, limite=$limit min")
            } finally {
                pending.finish()
            }
        }
    }

    /** Posta a cobrança `--es nudge <id>` (ex.: A1_WALK, ou ALL) com os números reais de `--es pkg`. */
    private fun onNudge(intent: Intent) {
        val pkg = intent.getStringExtra(EXTRA_PKG) ?: return
        val label = intent.getStringExtra(EXTRA_LABEL) ?: pkg
        val which = intent.getStringExtra(EXTRA_NUDGE) ?: return
        val nudges = if (which == "ALL") Nudge.entries else listOfNotNull(Nudge.entries.firstOrNull { it.name == which })
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                nudges.forEach { nudgeEngine.debugPreview(it, pkg, label) }
                Log.i(TAG, "Debug nudge: ${nudges.map { it.name }}")
            } finally {
                pending.finish()
            }
        }
    }

    /**
     * Encurta a sessão longa para `--ei minutes N` (0/ausente volta aos 30 min) e zera o
     * estado das cobranças (teto do dia e "já disparou"), para testar o gatilho real.
     */
    private fun onNudgeSession(intent: Intent) {
        val minutes = intent.getIntExtra(EXTRA_MINUTES, 0).takeIf { it > 0 }
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                nudgePreferences.reset()
                nudgePreferences.setDebugSessionThresholdMinutes(minutes)
                Log.i(TAG, "Debug nudge session threshold: $minutes")
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "DollarBlockDebug"
        const val ACTION_SET_LIMIT = "com.dollarblock.DEBUG_SET_LIMIT"
        const val EXTRA_PKG = "pkg"
        const val EXTRA_LIMIT = "limit"
        const val ACTION_NUDGE = "com.dollarblock.DEBUG_NUDGE"
        const val ACTION_NUDGE_SESSION = "com.dollarblock.DEBUG_NUDGE_SESSION"
        const val EXTRA_NUDGE = "nudge"
        const val EXTRA_LABEL = "label"
        const val EXTRA_MINUTES = "minutes"
    }
}
