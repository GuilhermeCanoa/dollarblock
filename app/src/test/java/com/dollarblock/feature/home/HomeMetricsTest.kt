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
        assertEquals(0, metrics.activeLimitsCount)
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
        assertEquals(3, metrics.activeLimitsCount)
    }

    @Test
    fun `score eh a media das razoes restante sobre limite em porcentagem`() {
        // a: (60-30)/60 = 0.5 ; b: (40-10)/40 = 0.75 ; média 0.625 -> 63
        val metrics = HomeMetrics.compute(
            listOf(
                app("a", limit = 60, used = 30),
                app("b", limit = 40, used = 10),
            ),
        )

        assertEquals(63, metrics.score)
    }

    @Test
    fun `uso acima do limite nao deixa o score negativo`() {
        val metrics = HomeMetrics.compute(
            listOf(app("a", limit = 30, used = 90)), // razão clampa em 0
        )

        assertEquals(0, metrics.score)
        assertEquals(0, metrics.timeSavedMinutes)
    }

    @Test
    fun `app no limite exato pontua 100`() {
        val metrics = HomeMetrics.compute(
            listOf(app("a", limit = 60, used = 0)),
        )

        assertEquals(100, metrics.score)
        assertEquals(60, metrics.timeSavedMinutes)
    }
}
