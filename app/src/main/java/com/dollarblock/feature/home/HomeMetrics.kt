package com.dollarblock.feature.home

import com.dollarblock.domain.model.MonitoredAppUsage
import kotlin.math.roundToInt

/**
 * Daily metrics shown on the Home screen, computed from monitored apps.
 *
 * @property score 0..1000 points remaining today, or `null` when no monitored app has a limit.
 * @property timeSavedMinutes sum of (limit − used) across all apps with a limit, clamped ≥ 0.
 * @property currentlyBlockedCount number of monitored apps that have exceeded their daily limit.
 */
data class DailyMetrics(
    val score: Int?,
    val timeSavedMinutes: Int,
    val currentlyBlockedCount: Int,
)

object HomeMetrics {

    fun compute(monitoredUsage: List<MonitoredAppUsage>): DailyMetrics {
        val withLimit = monitoredUsage.filter { it.isMonitored && it.dailyLimitMinutes != null }

        val totalLimit = withLimit.sumOf { it.dailyLimitMinutes ?: 0 }
        val totalUsed = withLimit.sumOf { it.usedMinutesToday }
        val totalRemaining = (totalLimit - totalUsed).coerceAtLeast(0)

        val score = if (totalLimit == 0) {
            null
        } else {
            (totalRemaining.toFloat() / totalLimit * 1000).roundToInt()
        }

        val timeSaved = withLimit.sumOf { app ->
            val limit = app.dailyLimitMinutes ?: 0
            (limit - app.usedMinutesToday).coerceAtLeast(0)
        }

        val currentlyBlocked = withLimit.count { app ->
            app.usedMinutesToday >= (app.dailyLimitMinutes ?: Int.MAX_VALUE)
        }

        return DailyMetrics(
            score = score,
            timeSavedMinutes = timeSaved,
            currentlyBlockedCount = currentlyBlocked,
        )
    }
}
