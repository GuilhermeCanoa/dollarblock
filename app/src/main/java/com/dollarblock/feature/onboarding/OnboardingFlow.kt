package com.dollarblock.feature.onboarding

import com.dollarblock.data.permissions.AppPermission
import com.dollarblock.data.permissions.PermissionsState
import com.dollarblock.feature.home.HomeMetrics

/** Uma página do pager de onboarding. */
sealed interface OnboardingPage {
    /** Entrada animada: a marca e a conta correndo desde que o app abriu. */
    data object Entry : OnboardingPage

    /** O contrato, com as cláusulas (incluindo a saída livre) e a assinatura. */
    data object Contract : OnboardingPage

    /** Acesso de uso; depois de concedido, a mesma página vira o resumo da semana. */
    data object Measurement : OnboardingPage

    /** Acessibilidade — a tranca. */
    data object Lock : OnboardingPage

    /** Sobreposição (obrigatória) e, no Android 13+, Notificações (opcional). Fecha o fluxo. */
    data class FinalSettings(val askNotifications: Boolean) : OnboardingPage
}

/**
 * Ordem do onboarding (E21): 5 páginas. O Acesso de uso vem primeiro porque a recompensa
 * (o resumo da semana) aparece na própria página; depois a tranca e os últimos ajustes.
 *
 * [askNotifications] é false abaixo do Android 13, onde não há permissão de runtime a pedir.
 */
fun onboardingPages(askNotifications: Boolean): List<OnboardingPage> = listOf(
    OnboardingPage.Entry,
    OnboardingPage.Contract,
    OnboardingPage.Measurement,
    OnboardingPage.Lock,
    OnboardingPage.FinalSettings(askNotifications),
)

/** Permissões pedidas em [page], na ordem em que aparecem nela. */
fun permissionsOn(page: OnboardingPage): List<AppPermission> = when (page) {
    OnboardingPage.Entry, OnboardingPage.Contract -> emptyList()
    OnboardingPage.Measurement -> listOf(AppPermission.USAGE_ACCESS)
    OnboardingPage.Lock -> listOf(AppPermission.ACCESSIBILITY)
    is OnboardingPage.FinalSettings ->
        if (page.askNotifications) listOf(AppPermission.OVERLAY, AppPermission.NOTIFICATIONS)
        else listOf(AppPermission.OVERLAY)
}

/** Permissões obrigatórias ainda não concedidas — o que o app perde se o usuário seguir assim. */
fun missingRequiredPermissions(state: PermissionsState): List<AppPermission> =
    AppPermission.entries.filter { it.required && !state.isGranted(it) }

/** A primeira obrigatória de [page] ainda pendente — sair da página sem ela pede confirmação. */
fun pendingRequiredOn(page: OnboardingPage, state: PermissionsState): AppPermission? =
    permissionsOn(page).firstOrNull { it.required && !state.isGranted(it) }

/**
 * Quanto custa [millis] de tela no salário de referência ([HomeMetrics.DEFAULT_MONTHLY_SALARY]),
 * em reais. Usado no contador da entrada e no resumo da semana — a mesma régua da Home.
 */
fun screenTimeCost(millis: Long, monthlySalary: Double = HomeMetrics.DEFAULT_MONTHLY_SALARY): Double =
    millis / 60_000.0 * HomeMetrics.perMinuteRate(monthlySalary)
