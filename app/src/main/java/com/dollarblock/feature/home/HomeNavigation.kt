package com.dollarblock.feature.home

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

/** Rota da aba Home. Fonte única — referenciada pela bottom bar e pelo NavHost. */
const val HOME_ROUTE = "home"

/** Registra a tela Home no grafo. Adicione/edite aqui sem tocar no NavHost compartilhado. */
fun NavGraphBuilder.homeScreen(onNavigateToApps: () -> Unit) {
    composable(HOME_ROUTE) { HomeScreen(onNavigateToApps = onNavigateToApps) }
}
