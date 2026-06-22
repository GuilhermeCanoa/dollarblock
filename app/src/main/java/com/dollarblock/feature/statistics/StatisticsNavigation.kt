package com.dollarblock.feature.statistics

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

/** Rota da aba Statistics. Fonte única — referenciada pela bottom bar e pelo NavHost. */
const val STATISTICS_ROUTE = "statistics"

/** Registra a tela Statistics no grafo. Adicione/edite aqui sem tocar no NavHost compartilhado. */
fun NavGraphBuilder.statisticsScreen() {
    composable(STATISTICS_ROUTE) { StatisticsScreen() }
}
