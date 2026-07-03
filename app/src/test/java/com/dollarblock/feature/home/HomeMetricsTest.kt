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

    @Test
    fun `bestAndWorstDay retorna nulos para lista vazia`() {
        val result = HomeMetrics.bestAndWorstDay(emptyList())

        assertNull(result.best)
        assertNull(result.worst)
    }

    @Test
    fun `bestAndWorstDay identifica menor e maior gasto`() {
        val result = HomeMetrics.bestAndWorstDay(
            listOf(
                DaySpend(epochDay = 1, amount = 5.0),
                DaySpend(epochDay = 2, amount = 0.0),
                DaySpend(epochDay = 3, amount = 12.0),
            ),
        )

        assertEquals(2L, result.best!!.epochDay)
        assertEquals(3L, result.worst!!.epochDay)
    }

    @Test
    fun `bestAndWorstDay com um unico dia usa o mesmo dia para best e worst`() {
        val result = HomeMetrics.bestAndWorstDay(listOf(DaySpend(epochDay = 7, amount = 3.0)))

        assertEquals(7L, result.best!!.epochDay)
        assertEquals(7L, result.worst!!.epochDay)
    }

    @Test
    fun `crossedCoffeeMultiple detecta cruzamento de cafe inteiro`() {
        // café = R$ 6
        assertEquals(true, HomeMetrics.crossedCoffeeMultiple(previousLost = 5.5, newLost = 6.5))
        assertEquals(false, HomeMetrics.crossedCoffeeMultiple(previousLost = 5.0, newLost = 5.9))
    }

    @Test
    fun `crossedCoffeeMultiple ignora quando valor nao aumenta`() {
        assertEquals(false, HomeMetrics.crossedCoffeeMultiple(previousLost = 6.5, newLost = 6.5))
        assertEquals(false, HomeMetrics.crossedCoffeeMultiple(previousLost = 6.5, newLost = 5.0))
    }

    @Test
    fun `crossedCoffeeMultiple detecta multiplos cafes de uma vez`() {
        assertEquals(true, HomeMetrics.crossedCoffeeMultiple(previousLost = 0.0, newLost = 18.0))
    }
}
