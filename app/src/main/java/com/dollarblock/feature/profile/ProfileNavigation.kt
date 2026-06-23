package com.dollarblock.feature.profile

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

/** Rota da aba Profile. Fonte única — referenciada pela bottom bar e pelo NavHost. */
const val PROFILE_ROUTE = "profile"

/** Rota da tela de histórico (sub-tela do Profile, fora da bottom bar). */
const val HISTORY_ROUTE = "history"

/**
 * Registra a tela Profile no grafo. [onOpenHistory] navega para o histórico.
 * Adicione/edite aqui sem tocar no NavHost compartilhado.
 */
fun NavGraphBuilder.profileScreen(onOpenHistory: () -> Unit) {
    composable(PROFILE_ROUTE) { ProfileScreen(onOpenHistory = onOpenHistory) }
}

/** Registra a tela de histórico no grafo. [onBack] volta para o Profile. */
fun NavGraphBuilder.historyScreen(onBack: () -> Unit) {
    composable(HISTORY_ROUTE) { HistoryScreen(onBack = onBack) }
}
