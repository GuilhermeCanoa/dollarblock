package com.dollarblock.domain.model

/** Moeda de exibição dos valores derivados do salário. Só formatação — sem conversão cambial. */
enum class AppCurrency { BRL, USD }

/**
 * Configuração monetária do taxímetro: salário líquido mensal usado para precificar o
 * minuto de scroll e a moeda em que esses valores são exibidos.
 */
data class MoneySettings(
    val monthlySalary: Double = DEFAULT_MONTHLY_SALARY,
    /** true quando o usuário informou o próprio salário (vs. referência padrão). */
    val salaryConfigured: Boolean = false,
    val currency: AppCurrency = AppCurrency.BRL,
) {
    companion object {
        /** Referência histórica do app quando o usuário não informa o salário. */
        const val DEFAULT_MONTHLY_SALARY = 2000.0
    }
}

/**
 * Formata valores monetários para exibição. BRL usa o formato brasileiro ("R$ 1.234,56"),
 * USD o americano ("$ 1,234.56"). Pura — testável na JVM.
 */
object MoneyFormat {
    fun format(value: Double, currency: AppCurrency): String {
        // Locale.US fixa "1,234.56"; para BRL o swap converte para "1.234,56".
        val us = "%,.2f".format(java.util.Locale.US, value)
        return when (currency) {
            AppCurrency.USD -> "$ $us"
            AppCurrency.BRL -> "R$ " + us.replace(',', 'X').replace('.', ',').replace('X', '.')
        }
    }
}
