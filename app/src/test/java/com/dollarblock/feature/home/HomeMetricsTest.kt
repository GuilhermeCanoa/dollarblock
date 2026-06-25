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
    fun `sem apps com limite o score eh nulo e metricas zeradas`() {
        val metrics = HomeMetrics.compute(
            listOf(
                app("a", monitored = true, limit = null, used = 30),
                app("b", monitored = false, limit = 60, used = 10),
            ),
        )

        assertNull(metrics.score)
        assertEquals(0, metrics.timeSavedMinutes)
        assertEquals(0, metrics.currentlyBlockedCount)
    }

    @Test
    fun `time saved soma apenas a folga positiva por app`() {
        val metrics = HomeMetrics.compute(
            listOf(
                app("a", limit = 60, used = 20), // economiza 40
                app("b", limit = 30, used = 50), // estourou: 0 (não conta negativo)
                app("c", limit = 45, used = 45), // economiza 0
            ),
        )

        assertEquals(40, metrics.timeSavedMinutes)
        // b (used=50 > limit=30) e c (used=45 >= limit=45) estão bloqueados
        assertEquals(2, metrics.currentlyBlockedCount)
    }

    @Test
    fun `score eh totalRemaining sobre totalLimit em escala 0-1000`() {
        // a: remaining=30, b: remaining=30 → totalRemaining=60, totalLimit=100 → 600
        val metrics = HomeMetrics.compute(
            listOf(
                app("a", limit = 60, used = 30),
                app("b", limit = 40, used = 10),
            ),
        )

        assertEquals(600, metrics.score)
    }

    @Test
    fun `uso acima do limite nao deixa o score negativo`() {
        val metrics = HomeMetrics.compute(
            listOf(app("a", limit = 30, used = 90)), // totalRemaining clampa em 0
        )

        assertEquals(0, metrics.score)
        assertEquals(0, metrics.timeSavedMinutes)
    }

    @Test
    fun `app no limite exato pontua 1000`() {
        val metrics = HomeMetrics.compute(
            listOf(app("a", limit = 60, used = 0)),
        )

        assertEquals(1000, metrics.score)
        assertEquals(60, metrics.timeSavedMinutes)
    }
}
