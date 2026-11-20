package com.dollarblock.core.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.dollarblock.feature.apps.AppsScreen
import com.dollarblock.feature.home.HomeScreen
import com.dollarblock.feature.profile.HISTORY_ROUTE
import com.dollarblock.feature.profile.ProfileScreen
import com.dollarblock.feature.profile.historyScreen
import com.dollarblock.feature.statistics.StatisticsScreen
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Destino que hospeda as quatro abas lado a lado (pager com swipe horizontal). */
const val TABS_ROUTE = "tabs"

/**
 * Grafo de navegação principal do DollarBlock.
 *
 * As quatro abas de primeiro nível ([TopLevelDestination]) vivem num [HorizontalPager] dentro
 * de um único destino ([TABS_ROUTE]): o usuário arrasta para o lado e troca de aba, com a tela
 * acompanhando o dedo, além de poder tocar na bottom bar. Sub-telas (ex.: histórico) seguem
 * como destinos próprios do NavHost, por cima do pager — registradas pela extension
 * `NavGraphBuilder` da própria feature.
 *
 * Os ViewModels das abas ficam presos à entrada [TABS_ROUTE] e sobrevivem à troca de aba
 * (como antes com `saveState/restoreState`); o estado `rememberSaveable` de cada página é
 * guardado pelo próprio pager quando ela sai de cena.
 */
@Composable
fun DollarBlockNavHost(
    navController: NavHostController,
    pagerState: PagerState,
    modifier: Modifier = Modifier,
    statementDayRequest: Long? = null,
    onStatementDayHandled: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val toTab = { tab: TopLevelDestination -> scope.launch { pagerState.goToTab(tab) } }
    NavHost(
        navController = navController,
        startDestination = TABS_ROUTE,
        modifier = modifier,
    ) {
        composable(TABS_ROUTE) {
            // Voltar numa aba que não é a Home leva à Home (mesmo comportamento de antes).
            BackHandler(enabled = pagerState.currentPage != TopLevelDestination.HOME.ordinal) {
                toTab(TopLevelDestination.HOME)
            }
            HorizontalPager(
                state = pagerState,
                key = { TopLevelDestination.entries[it].route },
            ) { page ->
                when (TopLevelDestination.entries[page]) {
                    TopLevelDestination.HOME -> HomeScreen(
                        onNavigateToApps = { toTab(TopLevelDestination.APPS) },
                        onOpenStatement = { toTab(TopLevelDestination.STATISTICS) },
                        onOpenProfile = { toTab(TopLevelDestination.PROFILE) },
                    )
                    TopLevelDestination.APPS -> AppsScreen(onOpenProfile = { toTab(TopLevelDestination.PROFILE) })
                    TopLevelDestination.STATISTICS -> StatisticsScreen(
                        onOpenProfile = { toTab(TopLevelDestination.PROFILE) },
                        requestedDay = statementDayRequest,
                        onRequestedDayHandled = onStatementDayHandled,
                    )
                    TopLevelDestination.PROFILE -> ProfileScreen(onOpenHistory = { navController.navigate(HISTORY_ROUTE) })
                }
            }
        }
        historyScreen(onBack = { navController.popBackStack() })
    }
}

/**
 * Leva o pager até [tab]. Aba vizinha: anima direto. Mais longe: salta para a vizinha do
 * destino e anima só o último passo — não compõe (nem carrega) as abas do meio à toa.
 */
suspend fun PagerState.goToTab(tab: TopLevelDestination) {
    val target = tab.ordinal
    val distance = target - currentPage
    if (distance == 0) return
    if (abs(distance) > 1) scrollToPage(if (distance > 0) target - 1 else target + 1)
    animateScrollToPage(target)
}
