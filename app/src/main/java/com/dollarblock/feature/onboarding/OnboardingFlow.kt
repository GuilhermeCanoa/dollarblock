package com.dollarblock.feature.onboarding

import com.dollarblock.data.permissions.AppPermission
import com.dollarblock.data.permissions.PermissionsState

/** Uma página do pager de onboarding. */
sealed interface OnboardingPage {
    data class Concept(val index: Int) : OnboardingPage
    data object QuickSummary : OnboardingPage
    data class Permission(val permission: AppPermission) : OnboardingPage
    data object Control : OnboardingPage
}

/**
 * Ordem do onboarding (E18): uma permissão por página, cada uma pedida quando o usuário já
 * entende para que serve. O Acesso de uso vem logo antes do Resumo rápido — a recompensa
 * imediata da permissão recém-concedida. Depois a Acessibilidade e a Sobreposição (a tranca
 * e o que garante que ela aparece por cima), e a opcional (notificações) por último.
 *
 * [askNotifications] é false abaixo do Android 13, onde não há permissão de runtime a pedir.
 */
fun onboardingPages(conceptCount: Int, askNotifications: Boolean): List<OnboardingPage> = buildList {
    repeat(conceptCount) { add(OnboardingPage.Concept(it)) }
    add(OnboardingPage.Permission(AppPermission.USAGE_ACCESS))
    add(OnboardingPage.QuickSummary)
    add(OnboardingPage.Permission(AppPermission.ACCESSIBILITY))
    add(OnboardingPage.Permission(AppPermission.OVERLAY))
    if (askNotifications) add(OnboardingPage.Permission(AppPermission.NOTIFICATIONS))
    add(OnboardingPage.Control)
}

/** Permissões obrigatórias ainda não concedidas — o que o app perde se o usuário seguir assim. */
fun missingRequiredPermissions(state: PermissionsState): List<AppPermission> =
    AppPermission.entries.filter { it.required && !state.isGranted(it) }
