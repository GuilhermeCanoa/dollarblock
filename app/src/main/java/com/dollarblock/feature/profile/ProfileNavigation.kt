package com.dollarblock.feature.profile

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

/** Rota da aba Profile. Fonte única — referenciada pela bottom bar e pelo NavHost. */
const val PROFILE_ROUTE = "profile"

/** Registra a tela Profile no grafo. Adicione/edite aqui sem tocar no NavHost compartilhado. */
fun NavGraphBuilder.profileScreen() {
    composable(PROFILE_ROUTE) { ProfileScreen() }
}
