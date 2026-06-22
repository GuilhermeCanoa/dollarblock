package com.dollarblock.core.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.graphics.vector.ImageVector
import com.dollarblock.R
import com.dollarblock.feature.apps.APPS_ROUTE
import com.dollarblock.feature.home.HOME_ROUTE
import com.dollarblock.feature.profile.PROFILE_ROUTE
import com.dollarblock.feature.statistics.STATISTICS_ROUTE

/**
 * Destinos de primeiro nível exibidos na Bottom Navigation do DollarBlock.
 * As rotas referenciam as constantes declaradas em cada feature (fonte única) —
 * edite este arquivo apenas ao adicionar/remover uma aba de primeiro nível.
 */
enum class TopLevelDestination(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    HOME(HOME_ROUTE, R.string.nav_home, Icons.Filled.Home),
    APPS(APPS_ROUTE, R.string.nav_apps, Icons.Filled.Apps),
    STATISTICS(STATISTICS_ROUTE, R.string.nav_statistics, Icons.Filled.BarChart),
    PROFILE(PROFILE_ROUTE, R.string.nav_profile, Icons.Filled.Person),
}
