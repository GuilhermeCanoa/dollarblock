package com.dollarblock.core.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.graphics.vector.ImageVector
import com.dollarblock.R

/**
 * Destinos de primeiro nível exibidos na Bottom Navigation do DollarBlock.
 */
enum class TopLevelDestination(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    HOME("home", R.string.nav_home, Icons.Filled.Home),
    APPS("apps", R.string.nav_apps, Icons.Filled.Apps),
    STATISTICS("statistics", R.string.nav_statistics, Icons.Filled.BarChart),
    PROFILE("profile", R.string.nav_profile, Icons.Filled.Person),
}
