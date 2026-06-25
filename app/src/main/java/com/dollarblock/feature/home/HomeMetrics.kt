package com.dollarblock.feature.home

import com.dollarblock.domain.model.MonitoredAppUsage

/**
 * Daily metrics shown on the Home screen, computed from monitored apps.
 *
 * @property moneyLostToday monetary value of time spent on monitored apps today (BRL),
 *   based on a R$2000/month reference salary (43200 min/month ≈ R$0.0463/min).
 *   `null` when no apps are monitored.
 * @property currentlyBlockedCount number of monitored apps that have exceeded their daily limit.
 */
data class DailyMetrics(
    val moneyLostToday: Double?,
    val currentlyBlockedCount: Int,
)

object HomeMetrics {

    private const val MONTHLY_SALARY = 2000.0
    private const val MINUTES_PER_MONTH = 43200.0
    const val REAIS_PER_MINUTE = MONTHLY_SALARY / MINUTES_PER_MONTH

    fun compute(monitoredUsage: List<MonitoredAppUsage>): DailyMetrics {
        val monitored = monitoredUsage.filter { it.isMonitored }

        val moneyLostToday = if (monitored.isEmpty()) {
            null
        } else {
            monitored.sumOf { it.usedMinutesToday } * REAIS_PER_MINUTE
        }

        val withLimit = monitored.filter { it.dailyLimitMinutes != null }
        val currentlyBlocked = withLimit.count { app ->
            app.usedMinutesToday >= (app.dailyLimitMinutes ?: Int.MAX_VALUE)
        }

        return DailyMetrics(
            moneyLostToday = moneyLostToday,
            currentlyBlockedCount = currentlyBlocked,
        )
    }
}
