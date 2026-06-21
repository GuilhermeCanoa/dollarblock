package com.dollarblock.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.dollarblock.feature.apps.AppsScreen
import com.dollarblock.feature.home.HomeScreen
import com.dollarblock.feature.profile.ProfileScreen
import com.dollarblock.feature.statistics.StatisticsScreen

/**
 * Grafo de navegação principal do DollarBlock. As telas são placeholders no E0
 * e serão preenchidas com dados reais nos épicos seguintes.
 */
@Composable
fun DollarBlockNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = TopLevelDestination.HOME.route,
        modifier = modifier,
    ) {
        composable(TopLevelDestination.HOME.route) { HomeScreen() }
        composable(TopLevelDestination.APPS.route) { AppsScreen() }
        composable(TopLevelDestination.STATISTICS.route) { StatisticsScreen() }
        composable(TopLevelDestination.PROFILE.route) { ProfileScreen() }
    }
}
