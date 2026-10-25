package com.dollarblock.domain.model

import com.dollarblock.data.local.prefs.CurrencyPreference
import com.dollarblock.data.local.prefs.resolveCurrency
import org.junit.Assert.assertEquals
import org.junit.Test

class MoneySettingsTest {

    @Test
    fun `format BRL usa formato brasileiro`() {
        assertEquals("R$ 1.234,56", MoneyFormat.format(1234.56, AppCurrency.BRL))
        assertEquals("R$ 0,00", MoneyFormat.format(0.0, AppCurrency.BRL))
    }

    @Test
    fun `format USD usa formato americano`() {
        assertEquals("$1,234.56", MoneyFormat.format(1234.56, AppCurrency.USD))
        assertEquals("$0.99", MoneyFormat.format(0.99, AppCurrency.USD))
        assertEquals("$4,000", MoneyFormat.format(4000.0, AppCurrency.USD, cents = false))
        assertEquals("R$ 3.000", MoneyFormat.format(3000.0, AppCurrency.BRL, cents = false))
    }

    @Test
    fun `formatStored usa a moeda gravada no extrato`() {
        assertEquals("R$ 1,99", MoneyFormat.formatStored("1.99", "BRL"))
        assertEquals("$0.99", MoneyFormat.formatStored("0.99", "USD"))
        assertEquals("₹45.00", MoneyFormat.formatStored("45", "inr"))
        // Moeda que o app não conhece (Play converteu para a moeda local): código + valor.
        assertEquals("ARS 580.00", MoneyFormat.formatStored("580", "ARS"))
        assertEquals("abc", MoneyFormat.formatStored("abc", "BRL"))
    }

    @Test
    fun `format INR usa simbolo da rupia e agrupamento indiano`() {
        assertEquals("₹45.00", MoneyFormat.format(45.0, AppCurrency.INR))
        assertEquals("₹0.69", MoneyFormat.format(0.69, AppCurrency.INR))
        assertEquals("₹999.50", MoneyFormat.format(999.5, AppCurrency.INR))
        assertEquals("₹1,000.00", MoneyFormat.format(1000.0, AppCurrency.INR))
        assertEquals("₹30,000", MoneyFormat.format(30000.0, AppCurrency.INR, cents = false))
        assertEquals("₹1,23,456.78", MoneyFormat.format(123456.78, AppCurrency.INR))
        assertEquals("₹12,34,567", MoneyFormat.format(1234567.0, AppCurrency.INR, cents = false))
    }

    @Test
    fun `india usa rupia - preco do passe igual ao do Play Console`() {
        assertEquals("45.00", AppCurrency.INR.dayPassPrice)
        assertEquals(30000.0, AppCurrency.INR.referenceMonthlySalary, 0.0)
    }

    @Test
    fun `so os EUA usam dolar - preco do passe e salario de referencia em USD`() {
        assertEquals(AppCurrency.USD, AppCurrency.forRegion("US"))
        assertEquals(AppCurrency.USD, AppCurrency.forRegion("us"))
        assertEquals(AppCurrency.BRL, AppCurrency.forRegion("BR"))
        assertEquals(AppCurrency.INR, AppCurrency.forRegion("IN"))
        assertEquals(AppCurrency.INR, AppCurrency.forRegion("in"))
        assertEquals(AppCurrency.BRL, AppCurrency.forRegion("PT"))
        assertEquals(AppCurrency.BRL, AppCurrency.forRegion(""))
        assertEquals("0.99", AppCurrency.USD.dayPassPrice)
        assertEquals("1.99", AppCurrency.BRL.dayPassPrice)
        assertEquals(4000.0, AppCurrency.USD.referenceMonthlySalary, 0.0)
        assertEquals(MoneySettings.DEFAULT_MONTHLY_SALARY, AppCurrency.BRL.referenceMonthlySalary, 0.0)
    }

    @Test
    fun `selecao manual desligada - moeda efetiva segue o pais`() {
        // CURRENCY_SELECTION_ENABLED = false: a preferência salva é ignorada.
        assertEquals(AppCurrency.BRL, resolveCurrency(CurrencyPreference.SYSTEM, "BR"))
        assertEquals(AppCurrency.USD, resolveCurrency(CurrencyPreference.SYSTEM, "US"))
        assertEquals(AppCurrency.BRL, resolveCurrency(CurrencyPreference.SYSTEM, "DE"))
        assertEquals(AppCurrency.BRL, resolveCurrency(CurrencyPreference.USD, "BR"))
        assertEquals(AppCurrency.USD, resolveCurrency(CurrencyPreference.BRL, "US"))
    }

    @Test
    fun `salario padrao sem configuracao e a referencia da moeda`() {
        assertEquals(4000.0, MoneySettings(monthlySalary = 4000.0, currency = AppCurrency.USD).referenceMonthlySalary, 0.0)
        assertEquals(3000.0, MoneySettings().referenceMonthlySalary, 0.0)
    }

    @Test
    fun `perMinuteRate acompanha o salario configurado`() {
        val default = com.dollarblock.feature.home.HomeMetrics.perMinuteRate(3000.0)
        assertEquals(com.dollarblock.feature.home.HomeMetrics.REAIS_PER_MINUTE, default, 1e-9)
        assertEquals(default * 2, com.dollarblock.feature.home.HomeMetrics.perMinuteRate(6000.0), 1e-9)
    }
}
