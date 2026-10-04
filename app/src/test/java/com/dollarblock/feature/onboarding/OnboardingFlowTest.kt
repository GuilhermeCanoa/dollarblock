package com.dollarblock.feature.onboarding

import com.dollarblock.data.permissions.AppPermission
import com.dollarblock.data.permissions.PermissionsState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingFlowTest {

    private fun permissionsIn(pages: List<OnboardingPage>) =
        pages.filterIsInstance<OnboardingPage.Permission>().map { it.permission }

    @Test
    fun `uma pagina por permissao, na ordem obrigatorias antes das opcionais`() {
        val pages = onboardingPages(conceptCount = 3, askNotifications = true)
        assertEquals(
            listOf(
                AppPermission.USAGE_ACCESS,
                AppPermission.ACCESSIBILITY,
                AppPermission.OVERLAY,
                AppPermission.NOTIFICATIONS,
            ),
            permissionsIn(pages),
        )
    }

    @Test
    fun `resumo rapido vem logo depois do acesso de uso`() {
        val pages = onboardingPages(conceptCount = 3, askNotifications = true)
        val usage = pages.indexOf(OnboardingPage.Permission(AppPermission.USAGE_ACCESS))
        assertEquals(OnboardingPage.QuickSummary, pages[usage + 1])
    }

    @Test
    fun `conceito abre e controle fecha o fluxo`() {
        val pages = onboardingPages(conceptCount = 3, askNotifications = true)
        assertEquals(
            listOf(OnboardingPage.Concept(0), OnboardingPage.Concept(1), OnboardingPage.Concept(2)),
            pages.take(3),
        )
        assertEquals(OnboardingPage.Control, pages.last())
        assertEquals(9, pages.size)
    }

    @Test
    fun `abaixo do Android 13 nao ha pagina de notificacoes`() {
        val pages = onboardingPages(conceptCount = 3, askNotifications = false)
        assertFalse(AppPermission.NOTIFICATIONS in permissionsIn(pages))
        assertEquals(8, pages.size)
    }

    @Test
    fun `uso, acessibilidade e sobreposicao sao obrigatorias`() {
        assertEquals(
            listOf(AppPermission.USAGE_ACCESS, AppPermission.ACCESSIBILITY, AppPermission.OVERLAY),
            AppPermission.entries.filter { it.required },
        )
    }

    @Test
    fun `faltando so opcionais nao ha obrigatoria pendente`() {
        val state = PermissionsState(usageAccess = true, accessibility = true, overlay = true)
        assertTrue(missingRequiredPermissions(state).isEmpty())
    }

    @Test
    fun `lista as obrigatorias pendentes`() {
        val state = PermissionsState(usageAccess = true, overlay = true, notifications = true)
        assertEquals(listOf(AppPermission.ACCESSIBILITY), missingRequiredPermissions(state))
        assertEquals(
            listOf(AppPermission.USAGE_ACCESS, AppPermission.ACCESSIBILITY, AppPermission.OVERLAY),
            missingRequiredPermissions(PermissionsState()),
        )
    }
}
