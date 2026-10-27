package com.dollarblock.feature.home

import com.dollarblock.domain.model.AppDay
import com.dollarblock.domain.model.MoneyReport
import com.dollarblock.domain.model.MonitoredAppUsage
import com.dollarblock.domain.model.PaymentMethod
import com.dollarblock.domain.model.UnlockDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeDashboardTest {

    private fun app(pkg: String, used: Int, limit: Int?, monitored: Boolean = true) =
        MonitoredAppUsage(pkg, pkg, monitored, limit, used)

    // O cenário do mockup: Instagram trancado, TikTok passou da metade, YouTube tranquilo.
    private val mockup = listOf(app("yt", 18, 60), app("ig", 47, 45), app("tt", 32, 60))

    @Test
    fun `linhas ordenadas por urgencia com a cor certa`() {
        val rows = HomeDashboard.appRows(mockup)
        assertEquals(listOf("ig", "tt", "yt"), rows.map { it.packageName })
        assertEquals(listOf(UsageLevel.LOCKED, UsageLevel.WARN, UsageLevel.OK), rows.map { it.level })
        assertEquals(1f, rows.first().progress!!, 0.001f) // estourado não passa de 100%
    }

    @Test
    fun `app sem limite vai para o fim e fica sem barra`() {
        val rows = HomeDashboard.appRows(listOf(app("wa", 90, null), app("ig", 47, 45)))
        assertEquals("wa", rows.last().packageName)
        assertEquals(UsageLevel.NO_LIMIT, rows.last().level)
        assertNull(rows.last().progress)
    }

    @Test
    fun `no maximo 3 apps, os mais urgentes`() {
        val rows = HomeDashboard.appRows(mockup + app("wa", 90, null) + app("x", 1, 60))
        assertEquals(listOf("ig", "tt", "yt"), rows.map { it.packageName })
    }

    @Test
    fun `app desmonitorado nao aparece`() {
        val rows = HomeDashboard.appRows(listOf(app("a", 10, 30), app("x", 10, 30, monitored = false)))
        assertEquals(listOf("a"), rows.map { it.packageName })
    }

    @Test
    fun `orcamento soma os apps com limite - 1h37 de 2h45`() {
        val budget = HomeDashboard.budget(mockup)!!
        assertEquals(97, budget.usedMinutes)
        assertEquals(165, budget.limitMinutes)
        assertEquals(listOf("ig", "tt", "yt"), budget.segments.map { it.first })
        assertEquals(47f / 165, budget.segments.first().second, 0.001f)
    }

    @Test
    fun `orcamento estourado encolhe as fatias para caber na barra`() {
        val budget = HomeDashboard.budget(listOf(app("a", 90, 30), app("b", 30, 30)))!!
        assertEquals(1f, budget.segments.sumOf { it.second.toDouble() }.toFloat(), 0.001f)
    }

    @Test
    fun `sem limites nao ha orcamento`() {
        assertNull(HomeDashboard.budget(listOf(app("a", 10, null))))
    }

    @Test
    fun `proximo a fechar e o app aberto com menos minutos`() {
        val next = HomeDashboard.nextToClose(mockup)!!
        assertEquals("tt", next.packageName)
        assertEquals(28, next.minutesLeft)
        assertNull(HomeDashboard.nextToClose(listOf(app("ig", 47, 45))))
    }

    @Test
    fun `app mais insistido hoje`() {
        assertEquals("ig", HomeDashboard.topAttempts(mapOf("ig" to 3, "tt" to 1)))
        assertEquals("a", HomeDashboard.topAttempts(mapOf("b" to 2, "a" to 2)))
        assertNull(HomeDashboard.topAttempts(emptyMap()))
    }

    @Test
    fun `carteira do mes - 2 pagos e 6 recusados, cortesia fora`() {
        val blocks = (1L..8L).map { AppDay("ig", it) } + AppDay("tt", 9)
        val unlocks = listOf(
            UnlockDay(AppDay("ig", 1), "1.99", PaymentMethod.PLAY_BILLING),
            UnlockDay(AppDay("ig", 2), "1.99", PaymentMethod.PLAY_BILLING),
            UnlockDay(AppDay("tt", 9), "0", PaymentMethod.COURTESY),
        )
        val wallet = MoneyReport.wallet(blocks, unlocks, dayPassPrice = 1.99)
        assertEquals(3.98, wallet.paid, 0.001)
        assertEquals(2, wallet.passesPaid)
        assertEquals(6, wallet.passesRefused)
        assertEquals(11.94, wallet.saved, 0.001)
        assertEquals(8, wallet.bills)
    }
}
