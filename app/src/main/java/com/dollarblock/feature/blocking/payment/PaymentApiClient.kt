package com.dollarblock.feature.blocking.payment

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object PaymentApiClient {

    private const val TAG = "DollarBlockPay"

    private const val ENDPOINT =
        "https://duj02ll1zl.execute-api.us-east-1.amazonaws.com/test/unlock-charge"

    data class ChargeResult(val status: String, val paymentIntentId: String)

    suspend fun charge(
        packageName: String,
        paymentToken: String,
        idempotencyKey: String,
        currency: String = GooglePayConfig.CURRENCY_CODE,
    ): Result<ChargeResult> = withContext(Dispatchers.IO) {
        runCatching {
            val body = JSONObject().apply {
                put("packageName", packageName)
                put("currency", currency)
                put("paymentToken", paymentToken)
                put("idempotencyKey", idempotencyKey)
            }.toString()

            val conn = URL(ENDPOINT).openConnection() as HttpURLConnection
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
}
