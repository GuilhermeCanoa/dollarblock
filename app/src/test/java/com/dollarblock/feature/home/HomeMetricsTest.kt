package com.dollarblock.feature.home

import com.dollarblock.domain.model.MonitoredAppUsage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeMetricsTest {

    private fun app(
        pkg: String,
        monitored: Boolean = true,
        limit: Int? = null,
        used: Int = 0,
    ) = MonitoredAppUsage(
        packageName = pkg,
        appName = pkg,
        isMonitored = monitored,
        dailyLimitMinutes = limit,
        usedMinutesToday = used,
    )

    @Test
    fun `sem apps monitorados moneyLost eh nulo`() {
        val metrics = HomeMetrics.compute(
            listOf(
                app("a", monitored = false, used = 30),
                app("b", monitored = false, used = 10),
            ),
        )

        assertNull(metrics.moneyLostToday)
        assertEquals(0, metrics.currentlyBlockedCount)
    }

    @Test
    fun `moneyLost soma todos os apps monitorados`() {
        val metrics = HomeMetrics.compute(
            listOf(
                app("a", monitored = true, used = 60),
                app("b", monitored = true, used = 60),
                app("c", monitored = false, used = 999),
            ),
        )

        val expected = 120 * HomeMetrics.REAIS_PER_MINUTE
        assertEquals(expected, metrics.moneyLostToday!!, 0.001)
    }

    @Test
    fun `currentlyBlocked conta apps que atingiram o limite`() {
        val metrics = HomeMetrics.compute(
            listOf(
                app("a", limit = 60, used = 20),  // dentro do limite
                app("b", limit = 30, used = 50),  // estourou
                app("c", limit = 45, used = 45),  // exatamente no limite
            ),
        )

        assertEquals(2, metrics.currentlyBlockedCount)
    }

    @Test
    fun `app sem limite nao conta como bloqueado`() {
        val metrics = HomeMetrics.compute(
            listOf(app("a", limit = null, used = 999)),
        )

        assertEquals(0, metrics.currentlyBlockedCount)
    }

    @Test
    fun `equivalencia despreza valores menores que 1 porcento de um cafe`() {
        assertNull(HomeMetrics.equivalence(0.0))
        assertNull(HomeMetrics.equivalence(0.05))
    }

    @Test
    fun `equivalencia abaixo de um cafe vira fracao de cafe`() {
        val equiv = HomeMetrics.equivalence(3.0) // café = R$ 6

        assertEquals(MoneyEquivalence.CoffeeFraction(50), equiv)
    }

    @Test
    fun `equivalencia entre cafe e pizza vira cafes inteiros`() {
        assertEquals(MoneyEquivalence.Coffees(1), HomeMetrics.equivalence(6.0))
        assertEquals(MoneyEquivalence.Coffees(4), HomeMetrics.equivalence(29.9))
    }

    @Test
    fun `equivalencia a partir de uma pizza vira pizzas`() {
        assertEquals(MoneyEquivalence.Pizzas(1), HomeMetrics.equivalence(45.0))
        assertEquals(MoneyEquivalence.Pizzas(2), HomeMetrics.equivalence(95.0))
    }

    @Test
    fun `app nao monitorado com limite nao conta como bloqueado`() {
        val metrics = HomeMetrics.compute(
            listOf(app("a", monitored = false, limit = 10, used = 999)),
        )

        assertNull(metrics.moneyLostToday)
        assertEquals(0, metrics.currentlyBlockedCount)
    }
}
