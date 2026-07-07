package com.dollarblock.domain.model

import com.dollarblock.data.local.prefs.CurrencyPreference
import com.dollarblock.data.local.prefs.resolveCurrency
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class MoneySettingsTest {

    @Test
    fun `format BRL usa formato brasileiro`() {
        assertEquals("R$ 1.234,56", MoneyFormat.format(1234.56, AppCurrency.BRL))
        assertEquals("R$ 0,00", MoneyFormat.format(0.0, AppCurrency.BRL))
    }

    @Test
    fun `format USD usa formato americano`() {
        assertEquals("$ 1,234.56", MoneyFormat.format(1234.56, AppCurrency.USD))
    }

    @Test
    fun `lancamento BRL-only - moeda efetiva e sempre Real`() {
        // CURRENCY_SELECTION_ENABLED = false: qualquer preferência/locale resolve BRL.
        assertEquals(AppCurrency.BRL, resolveCurrency(CurrencyPreference.SYSTEM, Locale("pt", "BR")))
        assertEquals(AppCurrency.BRL, resolveCurrency(CurrencyPreference.SYSTEM, Locale.US))
        assertEquals(AppCurrency.BRL, resolveCurrency(CurrencyPreference.SYSTEM, Locale.GERMANY))
        assertEquals(AppCurrency.BRL, resolveCurrency(CurrencyPreference.USD, Locale("pt", "BR")))
        assertEquals(AppCurrency.BRL, resolveCurrency(CurrencyPreference.BRL, Locale.US))
    }

    @Test
    fun `perMinuteRate acompanha o salario configurado`() {
        val default = com.dollarblock.feature.home.HomeMetrics.perMinuteRate(2000.0)
        assertEquals(com.dollarblock.feature.home.HomeMetrics.REAIS_PER_MINUTE, default, 1e-9)
        assertEquals(default * 2, com.dollarblock.feature.home.HomeMetrics.perMinuteRate(4000.0), 1e-9)
    }
}
