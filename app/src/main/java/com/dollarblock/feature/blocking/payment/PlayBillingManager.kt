package com.dollarblock.feature.blocking.payment

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Cobrança do passe do dia via Google Play Billing (E16).
 *
 * O passe é um produto consumível ([PaymentConfig.PLAY_PRODUCT_DAY_PASS]): cada compra é
 * consumida logo após a confirmação, permitindo comprar de novo em outro dia/app. O
 * desbloqueio em si continua sendo concedido pelo chamador (BlockActivity →
 * BlockPreferences.grantUnlockForToday) somente após [PurchaseListener.onPurchaseCompleted].
 *
 * Sem verificação server-side por enquanto: a compra é aceita quando o Play retorna
 * PURCHASED e consumida em seguida. Verificação via backend `dollarblock-payment`
 * (Google Play Developer API) é follow-up documentado no spec E16.
 */
class PlayBillingManager(
    context: Context,
    private val listener: PurchaseListener,
) : PurchasesUpdatedListener {

    interface PurchaseListener {
        /** Compra confirmada (PURCHASED) e consumo solicitado — conceder o desbloqueio. */
        fun onPurchaseCompleted()

        /** Fluxo encerrado sem compra. [cancelled] distingue desistência de erro. */
        fun onPurchaseFailed(cancelled: Boolean)
    }

    private val billingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build(),
        )
        .build()

    private var productDetails: ProductDetails? = null

    private val _ready = MutableStateFlow(false)
    /** true quando conectado ao Play e com o produto `day_pass` carregado. */
    val ready: StateFlow<Boolean> = _ready

    private val _formattedPrice = MutableStateFlow<String?>(null)
    /** Preço localizado do Play (ex.: "R$ 5,00"); null enquanto não carregado. */
    val formattedPrice: StateFlow<String?> = _formattedPrice

    /** Valor do preço como decimal (ex.: "5.00"), para registro no extrato. */
    val priceAmount: String?
        get() = productDetails?.oneTimePurchaseOfferDetails
            ?.priceAmountMicros?.let { micros -> "%.2f".format(java.util.Locale.US, micros / 1_000_000.0) }

    /** Moeda do preço do Play (ex.: "BRL"); null enquanto não carregado. */
    val priceCurrency: String?
        get() = productDetails?.oneTimePurchaseOfferDetails?.priceCurrencyCode

    fun connect() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryProduct()
                    consumeStalePurchases()
                } else {
                    Log.e(TAG, "Billing setup failed: ${billingResult.debugMessage}")
                    _ready.value = false
                }
            }

            override fun onBillingServiceDisconnected() {
                _ready.value = false
            }
        })
    }

    fun disconnect() {
        billingClient.endConnection()
    }

    private fun queryProduct() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PaymentConfig.PLAY_PRODUCT_DAY_PASS)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build(),
                ),
            )
            .build()
        billingClient.queryProductDetailsAsync(params) { billingResult, detailsList ->
            val details = detailsList.firstOrNull()
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && details != null) {
                productDetails = details
                _formattedPrice.value = details.oneTimePurchaseOfferDetails?.formattedPrice
                _ready.value = true
            } else {
                Log.e(
                    TAG,
                    "day_pass product not available: code=${billingResult.responseCode} " +
                        billingResult.debugMessage,
                )
                _ready.value = false
            }
        }
    }

    /**
     * Consome compras PURCHASED que ficaram sem consumo (ex.: app morto entre a compra e o
     * consumeAsync). Sem isso o Play bloqueia novas compras do mesmo consumível e
     * reembolsa automaticamente após 3 dias sem acknowledge. O desbloqueio correspondente
     * é concedido ao app-alvo atual via listener — o usuário pagou e não recebeu.
     */
    private fun consumeStalePurchases() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        billingClient.queryPurchasesAsync(params) { billingResult, purchases ->
            if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) return@queryPurchasesAsync
            purchases
                .filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                .filter { PaymentConfig.PLAY_PRODUCT_DAY_PASS in it.products }
                .forEach { purchase ->
                    Log.w(TAG, "Consuming stale day_pass purchase ${purchase.orderId}")
                    consume(purchase, notifyListener = true)
                }
        }
    }

    fun launchPurchase(activity: Activity): Boolean {
        val details = productDetails ?: return false
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .build(),
                ),
            )
            .build()
        val result = billingClient.launchBillingFlow(activity, params)
        return result.responseCode == BillingClient.BillingResponseCode.OK
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                val purchase = purchases
                    ?.filter { PaymentConfig.PLAY_PRODUCT_DAY_PASS in it.products }
                    ?.firstOrNull { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                if (purchase != null) {
                    consume(purchase, notifyListener = true)
                } else {
                    // PENDING (ex.: pagamento em lotérica/boleto) — não conceder ainda; a
                    // compra será recuperada por consumeStalePurchases numa próxima abertura.
                    Log.w(TAG, "Purchase update without PURCHASED state (pending?)")
                    listener.onPurchaseFailed(cancelled = false)
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED ->
                listener.onPurchaseFailed(cancelled = true)
            else -> {
                Log.e(TAG, "Purchase failed: code=${billingResult.responseCode} ${billingResult.debugMessage}")
                listener.onPurchaseFailed(cancelled = false)
            }
        }
    }

    private fun consume(purchase: Purchase, notifyListener: Boolean) {
        val params = ConsumeParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        billingClient.consumeAsync(params) { billingResult, _ ->
            if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                // Consumo falhou mas a compra está feita; será retentado via
                // consumeStalePurchases. O desbloqueio é concedido mesmo assim.
                Log.e(TAG, "consumeAsync failed: ${billingResult.debugMessage}")
            }
        }
        if (notifyListener) listener.onPurchaseCompleted()
    }

    private companion object {
        const val TAG = "DollarBlockPay"
    }
}
