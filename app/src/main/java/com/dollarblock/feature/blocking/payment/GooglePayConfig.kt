package com.dollarblock.feature.blocking.payment

import android.content.Context
import com.dollarblock.BuildConfig
import com.google.android.gms.wallet.PaymentsClient
import com.google.android.gms.wallet.Wallet
import com.google.android.gms.wallet.WalletConstants
import org.json.JSONArray
import org.json.JSONObject

/**
 * Configuração da Google Pay API para o DollarBlock.
 *
 * Ambiente: TESTE (`ENVIRONMENT_TEST`) com gateway `"stripe"` — tokeniza via Stripe,
 * cobra no backend Lambda. Para produção, trocar pk_test_ por pk_live_ e ENVIRONMENT_TEST
 * por ENVIRONMENT_PRODUCTION.
 */
object GooglePayConfig {

    /**
     * Preço padrão do passe do dia, usado como fallback quando
     * [com.dollarblock.data.repository.PricingRepository] não tem cache nem consegue
     * consultar `GET /pricing` (offline no primeiro uso). O valor cobrado de fato é
     * sempre resolvido no backend a partir de `product`+`currency` — o cliente nunca
     * define o valor cobrado, apenas o exibe.
     */
    const val DEFAULT_PRICE = "1.00"
    const val CURRENCY_CODE = "BRL"

    private const val ENVIRONMENT = WalletConstants.ENVIRONMENT_TEST

    private val allowedCardNetworks = JSONArray(listOf("AMEX", "MASTERCARD", "VISA"))
    private val allowedAuthMethods = JSONArray(listOf("PAN_ONLY", "CRYPTOGRAM_3DS"))

    fun paymentsClient(context: Context): PaymentsClient {
        val options = Wallet.WalletOptions.Builder()
            .setEnvironment(ENVIRONMENT)
            .build()
        return Wallet.getPaymentsClient(context, options)
    }

    fun isReadyToPayRequest(): JSONObject = baseRequest().apply {
        put("allowedPaymentMethods", JSONArray().put(baseCardPaymentMethod()))
    }

    fun paymentDataRequest(price: String): JSONObject = baseRequest().apply {
        put("allowedPaymentMethods", JSONArray().put(cardPaymentMethodWithTokenization()))
        put("transactionInfo", transactionInfo(price))
        put("merchantInfo", JSONObject().put("merchantName", "DollarBlock"))
    }

    private fun baseRequest(): JSONObject =
        JSONObject().put("apiVersion", 2).put("apiVersionMinor", 0)

    private fun baseCardPaymentMethod(): JSONObject =
        JSONObject().apply {
            put("type", "CARD")
            put(
                "parameters",
                JSONObject().apply {
                    put("allowedAuthMethods", allowedAuthMethods)
                    put("allowedCardNetworks", allowedCardNetworks)
                },
            )
        }

    private fun cardPaymentMethodWithTokenization(): JSONObject =
        baseCardPaymentMethod().apply {
            put(
                "tokenizationSpecification",
                JSONObject().apply {
                    put("type", "PAYMENT_GATEWAY")
                    put(
                        "parameters",
                        JSONObject().apply {
                            put("gateway", "stripe")
                            put("stripe:version", "2020-08-27")
                            put("stripe:publishableKey", BuildConfig.STRIPE_PUBLISHABLE_KEY)
                        },
                    )
                },
            )
        }

    private fun transactionInfo(price: String): JSONObject =
        JSONObject().apply {
            put("totalPrice", price)
            put("totalPriceStatus", "FINAL")
            put("currencyCode", CURRENCY_CODE)
        }
}
