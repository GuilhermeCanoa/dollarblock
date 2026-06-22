package com.dollarblock.feature.blocking.payment

import android.content.Context
import com.google.android.gms.wallet.PaymentsClient
import com.google.android.gms.wallet.Wallet
import com.google.android.gms.wallet.WalletConstants
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Configuração da Google Pay API para o DollarBlock.
 *
 * Ambiente: TESTE (`ENVIRONMENT_TEST`) com gateway `"example"` — não exige conta de PSP
 * nem chaves, e não realiza cobrança real. Para produção, ver `docs/PAYMENTS_SETUP.md`.
 */
object GooglePayConfig {

    /** Valor (mock) cobrado para desbloquear. */
    const val PRICE = "4.99"
    const val CURRENCY_CODE = "BRL"

    /** Duração da liberação após o pagamento. */
    const val UNLOCK_WINDOW_MINUTES = 5
    val UNLOCK_WINDOW_MS: Long = TimeUnit.MINUTES.toMillis(UNLOCK_WINDOW_MINUTES.toLong())

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

    fun paymentDataRequest(): JSONObject = baseRequest().apply {
        put("allowedPaymentMethods", JSONArray().put(cardPaymentMethodWithTokenization()))
        put("transactionInfo", transactionInfo())
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
                            // Gateway de teste do Google — devolve token fake, sem PSP real.
                            put("gateway", "example")
                            put("gatewayMerchantId", "exampleGatewayMerchantId")
                        },
                    )
                },
            )
        }

    private fun transactionInfo(): JSONObject =
        JSONObject().apply {
            put("totalPrice", PRICE)
            put("totalPriceStatus", "FINAL")
            put("currencyCode", CURRENCY_CODE)
        }
}
