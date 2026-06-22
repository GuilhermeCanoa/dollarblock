package com.dollarblock.feature.apps

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

/** Rota da aba Apps. Fonte única — referenciada pela bottom bar e pelo NavHost. */
const val APPS_ROUTE = "apps"

/** Registra a tela Apps no grafo. Adicione/edite aqui sem tocar no NavHost compartilhado. */
fun NavGraphBuilder.appsScreen() {
    composable(APPS_ROUTE) { AppsScreen() }
}
