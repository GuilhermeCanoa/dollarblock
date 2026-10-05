package com.dollarblock.feature.onboarding

import com.dollarblock.data.permissions.AppPermission
import com.dollarblock.data.permissions.PermissionsState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingFlowTest {

    @Test
    fun `cinco paginas - entrada, contrato, medicao, tranca e ultimos ajustes`() {
        assertEquals(
            listOf(
                OnboardingPage.Entry,
                OnboardingPage.Contract,
                OnboardingPage.Measurement,
                OnboardingPage.Lock,
                OnboardingPage.FinalSettings(askNotifications = true),
            ),
            onboardingPages(askNotifications = true),
        )
    }

    @Test
    fun `toda permissao aparece exatamente uma vez, obrigatorias antes das opcionais`() {
        val asked = onboardingPages(askNotifications = true).flatMap(::permissionsOn)
        assertEquals(
            listOf(
                AppPermission.USAGE_ACCESS,
                AppPermission.ACCESSIBILITY,
                AppPermission.OVERLAY,
                AppPermission.NOTIFICATIONS,
            ),
            asked,
        )
    }

    @Test
    fun `abaixo do Android 13 os ultimos ajustes so pedem a sobreposicao`() {
        val pages = onboardingPages(askNotifications = false)
        assertEquals(5, pages.size)
        assertEquals(listOf(AppPermission.OVERLAY), permissionsOn(pages.last()))
    }

    @Test
    fun `entrada e contrato nao pedem permissao`() {
        assertTrue(permissionsOn(OnboardingPage.Entry).isEmpty())
        assertTrue(permissionsOn(OnboardingPage.Contract).isEmpty())
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
        assertNull(pendingRequiredOn(OnboardingPage.FinalSettings(askNotifications = true), state))
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

    @Test
    fun `pendente da pagina considera so as permissoes dela`() {
        val nothing = PermissionsState()
        assertEquals(AppPermission.USAGE_ACCESS, pendingRequiredOn(OnboardingPage.Measurement, nothing))
        assertEquals(AppPermission.ACCESSIBILITY, pendingRequiredOn(OnboardingPage.Lock, nothing))
        assertEquals(
            AppPermission.OVERLAY,
            pendingRequiredOn(OnboardingPage.FinalSettings(askNotifications = true), nothing),
        )
        assertNull(pendingRequiredOn(OnboardingPage.Contract, nothing))
    }

    @Test
    fun `custo do tempo de tela usa a referencia de R$ 2000 por mes`() {
        // 43.200 min/mês → R$ 2.000 / 43.200 ≈ R$ 0,0463 por minuto.
        assertEquals(2000.0 / 43_200.0, screenTimeCost(60_000L), 1e-9)
        // Uma semana de 18 h no feed ≈ R$ 50.
        assertEquals(50.0, screenTimeCost(18L * 60 * 60_000), 1e-9)
        assertEquals(0.0, screenTimeCost(0L), 0.0)
    }

    @Test
    fun `custo segue o salario quando informado`() {
        assertEquals(4000.0 / 43_200.0, screenTimeCost(60_000L, monthlySalary = 4000.0), 1e-9)
    }
}
