package com.dollarblock.feature.home

import com.dollarblock.domain.model.MonitoredAppUsage
import kotlin.math.roundToInt

/**
 * Métricas do dia exibidas na Home, calculadas a partir dos apps monitorados.
 *
 * @property score 0..100, ou `null` quando nenhum app monitorado tem limite definido hoje.
 * @property timeSavedMinutes soma de (limite − usado), só quando positivo.
 * @property activeLimitsCount quantidade de apps monitorados com limite diário definido.
 */
data class DailyMetrics(
    val score: Int?,
    val timeSavedMinutes: Int,
    val activeLimitsCount: Int,
)

/**
 * Lógica **pura** (sem Android/Room/Compose) do cálculo das métricas do dia, extraída do
 * ViewModel para poder ser coberta por teste unitário JVM. Ver CONTRIBUTING.md seção 4.
 */
object HomeMetrics {

    fun compute(monitoredUsage: List<MonitoredAppUsage>): DailyMetrics {
        // Só apps monitorados COM limite contam — sem meta não há "economia" nem "score".
        val withLimit = monitoredUsage.filter { it.isMonitored && it.dailyLimitMinutes != null }

        val timeSaved = withLimit.sumOf { app ->
            val limit = app.dailyLimitMinutes ?: 0
            (limit - app.usedMinutesToday).coerceAtLeast(0)
        }

        val score = if (withLimit.isEmpty()) {
            null
        } else {
            val perAppRatios = withLimit.map { app ->
                val limit = app.dailyLimitMinutes ?: 1
                ((limit - app.usedMinutesToday).toFloat() / limit).coerceIn(0f, 1f)
            }
            (perAppRatios.average() * 100).roundToInt()
        }

        return DailyMetrics(
            score = score,
            timeSavedMinutes = timeSaved,
            activeLimitsCount = withLimit.size,
        )
    }
}
