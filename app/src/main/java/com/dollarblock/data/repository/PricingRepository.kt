package com.dollarblock.data.repository

import com.dollarblock.data.local.prefs.PricingPreferences
import com.dollarblock.feature.blocking.payment.GooglePayConfig
import com.dollarblock.feature.blocking.payment.PaymentApiClient
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Preço do passe do dia para exibição no app. A fonte da verdade de cobrança é sempre o
 * backend (`GET /pricing` / `POST /unlock-charge`); este repositório só resolve o que
 * mostrar na UI antes/durante a cobrança, com cache em disco para funcionar offline.
 */
@Singleton
class PricingRepository @Inject constructor(
    private val pricingPreferences: PricingPreferences,
) {
    /**
     * Busca o preço atual do passe do dia em [currency]. Tenta a rede primeiro; em caso de
     * falha, cai para o último valor em cache e, na ausência de cache, para
     * [GooglePayConfig.DEFAULT_PRICE].
     */
    suspend fun getDayPassPrice(currency: String = GooglePayConfig.CURRENCY_CODE): String {
        val cacheKey = cacheKeyFor(currency)
        val fetched = PaymentApiClient.getPricing().getOrNull()
            ?.get(PaymentApiClient.PRODUCT_DAY_PASS)
            ?.get(currency)
        if (fetched != null) {
            pricingPreferences.setCachedPrice(cacheKey, fetched)
            return fetched
        }
        return pricingPreferences.getCachedPrice(cacheKey) ?: GooglePayConfig.DEFAULT_PRICE
    }

    private fun cacheKeyFor(currency: String) = "day_pass_$currency"
}
