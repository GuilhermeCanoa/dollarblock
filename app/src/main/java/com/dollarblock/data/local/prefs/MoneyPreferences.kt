package com.dollarblock.data.local.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.dollarblock.core.locale.DeviceRegion
import com.dollarblock.domain.model.AppCurrency
import com.dollarblock.domain.model.MoneySettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Preferência de moeda de exibição.
 *
 * - [SYSTEM]: segue o país do aparelho ([DeviceRegion]) — EUA usa Dólar, Índia usa
 *   Rupia, o resto do mundo (Brasil incluso) usa Real.
 * - [BRL] / [USD]: força a moeda independentemente do celular.
 *
 * A moeda decide a exibição (conta/extrato), o salário de referência e o preço de
 * fallback do passe; o valor cobrado de fato é o do Play Console para o país da conta.
 */
enum class CurrencyPreference { SYSTEM, BRL, USD }

/**
 * A troca manual de moeda fica desabilitada (UI removida do Perfil): a moeda efetiva é
 * sempre a do país do aparelho. Reative para deixar o usuário forçar uma moeda.
 */
const val CURRENCY_SELECTION_ENABLED = false

/** Resolve a preferência em moeda efetiva; SYSTEM (e a seleção desligada) decide por [region]. */
fun resolveCurrency(
    preference: CurrencyPreference,
    region: String,
): AppCurrency = if (!CURRENCY_SELECTION_ENABLED) AppCurrency.forRegion(region) else when (preference) {
    CurrencyPreference.BRL -> AppCurrency.BRL
    CurrencyPreference.USD -> AppCurrency.USD
    CurrencyPreference.SYSTEM -> AppCurrency.forRegion(region)
}

/** Moeda efetiva do aparelho, para telas sem acesso a [MoneyPreferences] (onboarding, bloqueio). */
fun Context.deviceCurrency(): AppCurrency =
    resolveCurrency(CurrencyPreference.SYSTEM, DeviceRegion.current(this))

private val Context.moneyDataStore by preferencesDataStore(name = "dollarblock_money")

/**
 * Persiste o salário líquido mensal informado pelo usuário (base da conta; na
 * ausência, vale a referência da moeda — [AppCurrency.referenceMonthlySalary])
 * e a preferência de moeda de exibição.
 */
@Singleton
class MoneyPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val dataStore = context.moneyDataStore
    private val salaryKey = doublePreferencesKey("monthly_salary")
    private val currencyKey = stringPreferencesKey("display_currency")
    private val salaryTipShownKey = booleanPreferencesKey("salary_tip_shown")

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
        val currency = resolveCurrency(pref, DeviceRegion.current(context))
        MoneySettings(
            monthlySalary = salary ?: currency.referenceMonthlySalary,
            salaryConfigured = salary != null,
            currency = currency,
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

    /** true depois que o balão-tutorial do card de salário já foi mostrado uma vez. */
    val salaryTipShown: Flow<Boolean> = dataStore.data.map { it[salaryTipShownKey] ?: false }

    suspend fun setSalaryTipShown() {
        dataStore.edit { it[salaryTipShownKey] = true }
    }

    suspend fun reset() {
        dataStore.edit { it.clear() }
    }
}
