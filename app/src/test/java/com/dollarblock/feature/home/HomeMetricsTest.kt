package com.dollarblock.feature.home

import com.dollarblock.domain.model.AppCurrency
import com.dollarblock.domain.model.MonitoredAppUsage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun `equivalencia despreza valores menores que 1 porcento do item mais barato`() {
        assertNull(HomeMetrics.equivalence(0.0))
        assertNull(HomeMetrics.equivalence(0.01)) // 0,2% de uma passagem de R$ 5
    }

    @Test
    fun `equivalencia sempre gera numero legivel`() {
        listOf(0.5, 3.0, 6.0, 29.9, 95.0, 400.0).forEach { lost ->
            repeat(50) { seed ->
                val equiv = requireNotNull(HomeMetrics.equivalence(lost, AppCurrency.BRL, seed))
                val price = requireNotNull(equiv.item.priceBrl)
                if (equiv.count != null) {
                    assertNull(equiv.percent)
                    assertEquals((lost / price).toInt(), equiv.count)
                    assertTrue(equiv.count!! in 1..30)
                } else {
                    assertEquals((lost / price * 100).toInt(), equiv.percent)
                    assertTrue(equiv.percent!! in 1..99)
                }
            }
        }
    }

    @Test
    fun `equivalencia varia com a seed`() {
        val items = (0 until 50).mapNotNull { HomeMetrics.equivalence(20.0, AppCurrency.BRL, it)?.item }.toSet()
        assertTrue("esperava variedade, veio $items", items.size >= 10)
    }

    @Test
    fun `equivalencia e estavel para a mesma seed`() {
        assertEquals(
            HomeMetrics.equivalence(20.0, AppCurrency.BRL, 7),
            HomeMetrics.equivalence(20.0, AppCurrency.BRL, 7),
        )
    }

    @Test
    fun `equivalencia respeita a moeda`() {
        repeat(50) { seed ->
            val usd = requireNotNull(HomeMetrics.equivalence(10.0, AppCurrency.USD, seed))
            assertTrue(usd.item.priceUsd != null)
            val brl = requireNotNull(HomeMetrics.equivalence(10.0, AppCurrency.BRL, seed))
            assertTrue(brl.item.priceBrl != null)
        }
    }

    @Test
    fun `equivalencia compara com acao da NVIDIA`() {
        // R$ 51 = 5% de uma ação da NVIDIA (R$ 1.020)
        val nvidia = (0 until 50).mapNotNull { HomeMetrics.equivalence(51.0, AppCurrency.BRL, it) }
            .first { it.item == ComparisonItem.NVIDIA_SHARE }
        assertEquals(5, nvidia.percent)
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

    @Test
    fun `perMinuteRate escala com o salario configurado`() {
        // Dobrar o salário dobra o preço do minuto de scroll.
        val base = HomeMetrics.perMinuteRate(HomeMetrics.DEFAULT_MONTHLY_SALARY)
        assertEquals(base * 2, HomeMetrics.perMinuteRate(HomeMetrics.DEFAULT_MONTHLY_SALARY * 2), 1e-9)
    }

    @Test
    fun `moneyLost usa o salario custom em vez do padrao`() {
        val metrics = HomeMetrics.compute(
            listOf(app("a", monitored = true, used = 60)),
            monthlySalary = 4000.0,
        )
        assertEquals(60 * HomeMetrics.perMinuteRate(4000.0), metrics.moneyLostToday!!, 1e-9)
    }
}
