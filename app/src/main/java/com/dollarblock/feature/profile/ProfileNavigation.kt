package com.dollarblock.feature.profile

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

/** Rota da aba Profile. Fonte única — chave da página no pager das abas (ver `core/navigation/DollarBlockNavHost`). */
const val PROFILE_ROUTE = "profile"

/** Rota da tela de histórico (sub-tela do Profile, fora da bottom bar). */
const val HISTORY_ROUTE = "history"

/** Registra a tela de histórico no grafo. [onBack] volta para o Profile. */
fun NavGraphBuilder.historyScreen(onBack: () -> Unit) {
    composable(HISTORY_ROUTE) { HistoryScreen(onBack = onBack) }
}
