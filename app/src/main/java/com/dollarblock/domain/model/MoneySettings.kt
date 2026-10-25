package com.dollarblock.domain.model

/**
 * Moeda de exibição dos valores do app. Só formatação — sem conversão cambial: cada
 * moeda tem a própria tabela (preço do passe e salário de referência), não um câmbio.
 *
 * @property code código ISO 4217, o mesmo que o Play Billing devolve em `priceCurrencyCode`.
 * @property dayPassPrice preço do passe do dia nessa moeda, em formato "0.99". É só o
 *   fallback de exibição: o valor cobrado é o do produto `day_pass` no Play Console para
 *   o país do usuário — manter os dois em sincronia manualmente.
 * @property referenceMonthlySalary salário líquido mensal usado quando o usuário não
 *   informa o próprio (régua da conta da Home, onboarding e extrato).
 */
enum class AppCurrency(
    val code: String,
    val dayPassPrice: String,
    val referenceMonthlySalary: Double,
) {
    BRL("BRL", "1.99", 3000.0),
    USD("USD", "0.99", 4000.0),
    INR("INR", "45.00", 30000.0),
    ;

    companion object {
        /**
         * Moeda pela região (ISO 3166, ex. "US"): EUA → dólar, Índia → rupia; o resto do
         * mundo, Brasil incluso, continua em real.
         */
        fun forRegion(region: String): AppCurrency = when (region.uppercase()) {
            "US" -> USD
            "IN" -> INR
            else -> BRL
        }

        fun fromCode(code: String): AppCurrency? = entries.firstOrNull { it.code.equals(code, ignoreCase = true) }
    }
}

/**
 * Configuração monetária da conta: salário líquido mensal usado para precificar o
 * minuto de scroll e a moeda em que esses valores são exibidos.
 */
data class MoneySettings(
    val monthlySalary: Double = DEFAULT_MONTHLY_SALARY,
    /** true quando o usuário informou o próprio salário (vs. referência padrão). */
    val salaryConfigured: Boolean = false,
    val currency: AppCurrency = AppCurrency.BRL,
) {
    /** Salário de referência da moeda atual (o usado enquanto [salaryConfigured] é false). */
    val referenceMonthlySalary: Double get() = currency.referenceMonthlySalary

    companion object {
        /** Referência histórica do app (BRL) quando o usuário não informa o salário. */
        const val DEFAULT_MONTHLY_SALARY = 3000.0
    }
}

/**
 * Formata valores monetários para exibição. BRL usa o formato brasileiro ("R$ 1.234,56"),
 * USD o americano ("$1,234.56") e INR o indiano, com agrupamento em lakh ("₹1,23,456.78").
 * Pura — testável na JVM.
 */
object MoneyFormat {
    fun format(value: Double, currency: AppCurrency, cents: Boolean = true): String {
        // Locale.US fixa "1,234.56"; para BRL o swap converte para "1.234,56".
        // Sem centavos para valores redondos de referência ("R$ 3.000", "$4,000").
        val us = (if (cents) "%,.2f" else "%,.0f").format(java.util.Locale.US, value)
        return when (currency) {
            AppCurrency.USD -> "$$us"
            AppCurrency.BRL -> "R$ " + us.replace(',', 'X').replace('.', ',').replace('X', '.')
            AppCurrency.INR -> "₹" + lakhGrouping(us)
        }
    }

    /** "123,456.78" → "1,23,456.78": últimos 3 dígitos inteiros, depois grupos de 2. */
    private fun lakhGrouping(usFormatted: String): String {
        val intPart = usFormatted.substringBefore('.').replace(",", "")
        val decimals = usFormatted.substringAfter('.', missingDelimiterValue = "")
        val last3 = intPart.takeLast(3)
        val rest = intPart.dropLast(3)
        val grouped = if (rest.isEmpty()) {
            last3
        } else {
            rest.reversed().chunked(2).joinToString(",").reversed() + "," + last3
        }
        return if (decimals.isEmpty()) grouped else "$grouped.$decimals"
    }

    /**
     * Formata um valor do extrato como foi gravado (texto "1.99" + código da moeda da
     * cobrança). Moeda fora de [AppCurrency] (ex.: o Play converteu o preço para outra
     * moeda local) sai como "INR 25.00", sem inventar símbolo.
     */
    fun formatStored(amount: String, currencyCode: String): String {
        val value = amount.trim().replace(',', '.').toDoubleOrNull()
        val currency = AppCurrency.fromCode(currencyCode)
        return when {
            value == null -> amount
            currency == null -> "${currencyCode.uppercase()} " + "%.2f".format(java.util.Locale.US, value)
            else -> format(value, currency)
        }
    }
}
