package com.dollarblock.core.navigation

import android.content.Context
import android.content.Intent
import com.dollarblock.MainActivity

/**
 * Pedido de abertura do app vindo de fora (E27 — tela de bloqueio): qual aba mostrar, se o
 * Extrato abre já no dia de hoje e se o parabéns por recusar a fatura deve aparecer.
 */
data class AppLaunchRequest(
    val tab: TopLevelDestination,
    val openStatementToday: Boolean = false,
    /** Nome do app cuja fatura foi recusada; não nulo = mostrar o parabéns. */
    val celebrateAppLabel: String? = null,
) {
    companion object {
        private const val EXTRA_TAB = "com.dollarblock.extra.OPEN_TAB"
        private const val EXTRA_STATEMENT_TODAY = "com.dollarblock.extra.STATEMENT_TODAY"
        private const val EXTRA_CELEBRATE_APP = "com.dollarblock.extra.CELEBRATE_APP"

        /** Intent que abre o app numa tarefa limpa com este pedido. */
        fun intent(context: Context, request: AppLaunchRequest): Intent =
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                .putExtra(EXTRA_TAB, request.tab.name)
                .putExtra(EXTRA_STATEMENT_TODAY, request.openStatementToday)
                .apply { request.celebrateAppLabel?.let { putExtra(EXTRA_CELEBRATE_APP, it) } }

        /** O pedido contido em [intent], ou null numa abertura comum (launcher). */
        fun from(intent: Intent?): AppLaunchRequest? {
            val tabName = intent?.getStringExtra(EXTRA_TAB) ?: return null
            val tab = TopLevelDestination.entries.firstOrNull { it.name == tabName } ?: return null
            return AppLaunchRequest(
                tab = tab,
                openStatementToday = intent.getBooleanExtra(EXTRA_STATEMENT_TODAY, false),
                celebrateAppLabel = intent.getStringExtra(EXTRA_CELEBRATE_APP),
            )
        }
    }
}
