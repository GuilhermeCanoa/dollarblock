package com.dollarblock.data.local.prefs

import android.content.Context
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.dollarblock.domain.model.AppCurrency
import com.dollarblock.domain.model.MoneySettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Preferência de moeda de exibição.
 *
 * - [SYSTEM]: segue o país do celular — Brasil (ou idioma pt) usa Real, o resto do
 *   mundo usa Dólar (mesmo critério do idioma em [AppLanguage]).
 * - [BRL] / [USD]: força a moeda independentemente do celular.
 *
 * Só afeta **exibição** dos valores derivados do salário (taxímetro/extrato); a
 * cobrança real do passe do dia continua em BRL no backend.
 */
enum class CurrencyPreference { SYSTEM, BRL, USD }

/** Resolve a preferência em moeda efetiva; SYSTEM decide pelo locale (BR/pt → Real). */
fun resolveCurrency(
    preference: CurrencyPreference,
    locale: Locale = Locale.getDefault(),
): AppCurrency = when (preference) {
    CurrencyPreference.BRL -> AppCurrency.BRL
    CurrencyPreference.USD -> AppCurrency.USD
    CurrencyPreference.SYSTEM ->
        if (locale.country.equals("BR", ignoreCase = true) || locale.language == "pt") {
            AppCurrency.BRL
        } else {
            AppCurrency.USD
        }
}

private val Context.moneyDataStore by preferencesDataStore(name = "dollarblock_money")

/**
 * Persiste o salário líquido mensal informado pelo usuário (base do taxímetro; na
 * ausência, vale a referência de R$ 2.000 — [MoneySettings.DEFAULT_MONTHLY_SALARY])
 * e a preferência de moeda de exibição.
 */
@Singleton
class MoneyPreferences @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val dataStore = context.moneyDataStore
    private val salaryKey = doublePreferencesKey("monthly_salary")
    private val currencyKey = stringPreferencesKey("display_currency")

    /** Salário líquido mensal informado, ou null quando o usuário nunca configurou. */
    val monthlySalary: Flow<Double?> = dataStore.data.map { it[salaryKey] }

    val currencyPreference: Flow<CurrencyPreference> = dataStore.data.map { prefs ->
        when (prefs[currencyKey]) {
            CurrencyPreference.BRL.name -> CurrencyPreference.BRL
            CurrencyPreference.USD.name -> CurrencyPreference.USD
            else -> CurrencyPreference.SYSTEM
        }
    }

    /** Configuração consolidada pronta para consumo nas telas (salário efetivo + moeda). */
    val settings: Flow<MoneySettings> = combine(monthlySalary, currencyPreference) { salary, pref ->
        MoneySettings(
            monthlySalary = salary ?: MoneySettings.DEFAULT_MONTHLY_SALARY,
            salaryConfigured = salary != null,
            currency = resolveCurrency(pref),
        )
    }

    /** Salva o salário; null volta para a referência padrão. */
    suspend fun setMonthlySalary(value: Double?) {
        dataStore.edit { prefs ->
            if (value == null) prefs.remove(salaryKey) else prefs[salaryKey] = value
        }
    }

    suspend fun setCurrencyPreference(preference: CurrencyPreference) {
        dataStore.edit { it[currencyKey] = preference.name }
    }

    suspend fun reset() {
        dataStore.edit { it.clear() }
    }
}
