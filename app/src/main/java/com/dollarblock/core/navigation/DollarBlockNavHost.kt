package com.dollarblock.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import com.dollarblock.feature.apps.APPS_ROUTE
import com.dollarblock.feature.apps.appsScreen
import com.dollarblock.feature.home.HOME_ROUTE
import com.dollarblock.feature.home.homeScreen
import com.dollarblock.feature.profile.HISTORY_ROUTE
import com.dollarblock.feature.profile.historyScreen
import com.dollarblock.feature.profile.profileScreen
import com.dollarblock.feature.statistics.statisticsScreen

/**
 * Grafo de navegação principal do DollarBlock. Cada tela é registrada por uma extension
 * `NavGraphBuilder` declarada no próprio pacote da feature (`feature/<nome>/<Nome>Navigation.kt`),
 * para que adicionar/editar uma rota não exija mexer neste arquivo compartilhado.
 * Ver docs/MERGE_HOTSPOTS.md seção 5.
 */
@Composable
fun DollarBlockNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = HOME_ROUTE,
        modifier = modifier,
    ) {
        homeScreen(onNavigateToApps = {
            navController.navigate(APPS_ROUTE) {
                popUpTo(navController.graph.startDestinationId) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        })
        appsScreen()
        statisticsScreen()
        profileScreen(onOpenHistory = { navController.navigate(HISTORY_ROUTE) })
        historyScreen(onBack = { navController.popBackStack() })
    }
}
