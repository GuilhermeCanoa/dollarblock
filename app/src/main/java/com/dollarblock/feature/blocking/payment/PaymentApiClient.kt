package com.dollarblock.feature.blocking.payment

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object PaymentApiClient {

    private const val TAG = "DollarBlockPay"

    private const val BASE_URL = "https://duj02ll1zl.execute-api.us-east-1.amazonaws.com/test"
    private const val CHARGE_ENDPOINT = "$BASE_URL/unlock-charge"
    private const val PRICING_ENDPOINT = "$BASE_URL/pricing"

    const val PRODUCT_DAY_PASS = "day_pass"

    data class ChargeResult(val status: String, val paymentIntentId: String)

    suspend fun charge(
        packageName: String,
        paymentToken: String,
        idempotencyKey: String,
        product: String = PRODUCT_DAY_PASS,
        currency: String = GooglePayConfig.CURRENCY_CODE,
    ): Result<ChargeResult> = withContext(Dispatchers.IO) {
        runCatching {
            val body = JSONObject().apply {
                put("packageName", packageName)
                put("product", product)
                put("currency", currency)
                put("paymentToken", paymentToken)
                put("idempotencyKey", idempotencyKey)
            }.toString()

            val conn = URL(CHARGE_ENDPOINT).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.outputStream.use { it.write(body.toByteArray()) }

            val code = conn.responseCode
            val responseBody = if (code in 200..299) {
                conn.inputStream.bufferedReader().readText()
            } else {
                conn.errorStream?.bufferedReader()?.readText().orEmpty()
            }
            conn.disconnect()

            Log.d(TAG, "unlock-charge HTTP $code body=${responseBody.take(300)}")

            if (code != 200) {
                val msg = runCatching { JSONObject(responseBody).getString("error") }
                    .getOrDefault("HTTP $code")
                error(msg)
            }

            val json = JSONObject(responseBody)
            ChargeResult(
                status = json.getString("status"),
                paymentIntentId = json.getString("paymentIntentId"),
            )
        }
    }

    /**
     * Busca a tabela de preços do backend, ex. `{"day_pass":{"BRL":"1.00","USD":"1.00"}}`.
     * O chamador ([com.dollarblock.data.repository.PricingRepository]) decide o fallback
     * quando isto falha — este client só reporta sucesso/erro de rede.
     */
    suspend fun getPricing(): Result<Map<String, Map<String, String>>> = withContext(Dispatchers.IO) {
        runCatching {
            val conn = URL(PRICING_ENDPOINT).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000

            val code = conn.responseCode
            val responseBody = if (code in 200..299) {
                conn.inputStream.bufferedReader().readText()
            } else {
                conn.errorStream?.bufferedReader()?.readText().orEmpty()
            }
            conn.disconnect()

            if (code != 200) error("HTTP $code")

            val json = JSONObject(responseBody)
            json.keys().asSequence().associateWith { product ->
                val byCurrency = json.getJSONObject(product)
                byCurrency.keys().asSequence().associateWith { byCurrency.getString(it) }
            }
        }
    }
}
