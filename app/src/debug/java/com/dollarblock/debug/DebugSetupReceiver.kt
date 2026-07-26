package com.dollarblock.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.dollarblock.domain.repository.MonitoredAppRepository
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
 */
@AndroidEntryPoint
class DebugSetupReceiver : BroadcastReceiver() {

    @Inject lateinit var monitoredAppRepository: MonitoredAppRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SET_LIMIT) return
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

    private companion object {
        const val TAG = "DollarBlockDebug"
        const val ACTION_SET_LIMIT = "com.dollarblock.DEBUG_SET_LIMIT"
        const val EXTRA_PKG = "pkg"
        const val EXTRA_LIMIT = "limit"
    }
}
