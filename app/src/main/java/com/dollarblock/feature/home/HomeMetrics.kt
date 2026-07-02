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

/**
 * Equivalência concreta do prejuízo do dia, para o cérebro sentir o número
 * (efeito de concretude): "R$ 2,18" vira "um café".
 */
sealed interface MoneyEquivalence {
    /** Fração de um café — prejuízo menor que um café inteiro. */
    data class CoffeeFraction(val percent: Int) : MoneyEquivalence

    /** Cafés inteiros. */
    data class Coffees(val count: Int) : MoneyEquivalence

    /** Pizzas inteiras — para dias realmente caros. */
    data class Pizzas(val count: Int) : MoneyEquivalence
}

object HomeMetrics {

    private const val MONTHLY_SALARY = 2000.0
    private const val MINUTES_PER_MONTH = 43200.0
    const val REAIS_PER_MINUTE = MONTHLY_SALARY / MINUTES_PER_MONTH

    /** Preços de referência das equivalências (BRL). */
    const val COFFEE_PRICE = 6.0
    const val PIZZA_PRICE = 45.0

    /**
     * Converte o prejuízo em algo palpável. Retorna null quando o prejuízo é
     * desprezível (< 1% de um café) — nada a declarar.
     */
    fun equivalence(moneyLost: Double): MoneyEquivalence? = when {
        moneyLost >= PIZZA_PRICE -> MoneyEquivalence.Pizzas((moneyLost / PIZZA_PRICE).toInt())
        moneyLost >= COFFEE_PRICE -> MoneyEquivalence.Coffees((moneyLost / COFFEE_PRICE).toInt())
        else -> {
            val percent = (moneyLost / COFFEE_PRICE * 100).toInt()
            if (percent < 1) null else MoneyEquivalence.CoffeeFraction(percent)
        }
    }

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
