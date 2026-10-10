package com.dollarblock.feature.home

import com.dollarblock.domain.model.AppCurrency
import com.dollarblock.domain.model.MonitoredAppUsage

/**
 * Daily metrics shown on the Home screen, computed from monitored apps.
 *
 * @property moneyLostToday monetary value of time spent on monitored apps today (BRL),
 *   based on a R$3000/month reference salary (43200 min/month ≈ R$0.0694/min).
 *   `null` when no apps are monitored.
 * @property currentlyBlockedCount number of monitored apps that have exceeded their daily limit.
 */
data class DailyMetrics(
    val moneyLostToday: Double?,
    val currentlyBlockedCount: Int,
)

/**
 * Equivalência concreta do prejuízo do dia, para o cérebro sentir o número
 * (efeito de concretude): "R$ 2,18" vira "36% de um café" ou "9% de um Big Mac".
 * Exatamente um de [count]/[percent] é preenchido.
 */
data class MoneyEquivalence(
    val item: ComparisonItem,
    /** Unidades inteiras do item (prejuízo >= preço do item). */
    val count: Int? = null,
    /** Percentual de uma unidade (prejuízo < preço do item). */
    val percent: Int? = null,
)

/** Um dia do extrato, identificado por epoch day, com o total gasto (uso convertido em BRL). */
data class DaySpend(val epochDay: Long, val amount: Double)

/** Melhor (menor gasto) e pior (maior gasto) dia de um período, para destaque no extrato. */
data class BestWorstDay(val best: DaySpend?, val worst: DaySpend?)

object HomeMetrics {

    const val DEFAULT_MONTHLY_SALARY = 3000.0
    private const val MINUTES_PER_MONTH = 43200.0
    const val REAIS_PER_MINUTE = DEFAULT_MONTHLY_SALARY / MINUTES_PER_MONTH

    /** Preço do minuto de scroll dado o salário líquido mensal configurado. */
    fun perMinuteRate(monthlySalary: Double): Double = monthlySalary / MINUTES_PER_MONTH

    /** Preço do café (BRL) — unidade do haptic do count-up da conta. */
    const val COFFEE_PRICE = 6.0

    /** Acima disso a comparação vira "37 cafés" — número grande demais pra sentir. */
    private const val MAX_COUNT = 30

    /**
     * Converte o prejuízo em algo palpável, escolhendo entre os itens do
     * [ComparisonItem] que têm preço em [currency] e dão um número legível
     * (de 1% de uma unidade até [MAX_COUNT] unidades). [seed] escolhe qual — a
     * Home sorteia um por visita para a frase não se repetir.
     * Retorna null quando o prejuízo é desprezível (< 1% do item mais barato).
     */
    fun equivalence(
        moneyLost: Double,
        currency: AppCurrency = AppCurrency.BRL,
        seed: Int = 0,
    ): MoneyEquivalence? {
        if (moneyLost <= 0.0) return null
        val candidates = ComparisonItem.entries.mapNotNull { item ->
            val price = item.priceIn(currency) ?: return@mapNotNull null
            val ratio = moneyLost / price
            when {
                ratio >= 1.0 -> (ratio.toInt()).takeIf { it <= MAX_COUNT }
                    ?.let { MoneyEquivalence(item, count = it) }
                else -> (ratio * 100).toInt().takeIf { it >= 1 }
                    ?.let { MoneyEquivalence(item, percent = it) }
            }
        }
        if (candidates.isEmpty()) return null
        return candidates[Math.floorMod(seed, candidates.size)]
    }

    /**
     * Destaca o melhor (menor gasto) e o pior (maior gasto) dia dentre [daySpends].
     * Dias com gasto zero contam como candidatos a "melhor dia" (dia sem prejuízo).
     * Retorna nulos quando a lista está vazia.
     */
    fun bestAndWorstDay(daySpends: List<DaySpend>): BestWorstDay {
        if (daySpends.isEmpty()) return BestWorstDay(null, null)
        val best = daySpends.minByOrNull { it.amount }
        val worst = daySpends.maxByOrNull { it.amount }
        return BestWorstDay(best, worst)
    }

    /**
     * true quando o count-up cruzou um múltiplo inteiro de café (para disparar haptic
     * na conta da Home). Compara o inteiro de cafés antes e depois do frame.
     */
    fun crossedCoffeeMultiple(previousLost: Double, newLost: Double): Boolean {
        if (newLost <= previousLost) return false
        val before = (previousLost / COFFEE_PRICE).toInt()
        val after = (newLost / COFFEE_PRICE).toInt()
        return after > before
    }

    fun compute(
        monitoredUsage: List<MonitoredAppUsage>,
        monthlySalary: Double = DEFAULT_MONTHLY_SALARY,
    ): DailyMetrics {
        val monitored = monitoredUsage.filter { it.isMonitored }

        val moneyLostToday = if (monitored.isEmpty()) {
            null
        } else {
            monitored.sumOf { it.usedMinutesToday } * perMinuteRate(monthlySalary)
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
