package com.dollarblock.feature.blocking

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.format.DateFormat
import android.util.Log
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import androidx.compose.ui.unit.sp
import com.dollarblock.BuildConfig
import com.dollarblock.MainActivity
import com.dollarblock.R
import com.dollarblock.core.designsystem.DollarBlockTheme
import com.dollarblock.core.designsystem.NeutralWhite
import com.dollarblock.core.designsystem.components.DollarBlockDialog
import kotlinx.coroutines.delay
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.dollarblock.data.local.prefs.BlockPreferences
import com.dollarblock.data.repository.PricingRepository
import com.dollarblock.domain.model.PaymentMethod
import com.dollarblock.domain.repository.EventsRepository
import com.dollarblock.feature.blocking.payment.GooglePayConfig
import com.dollarblock.feature.blocking.payment.PaymentApiClient
import com.dollarblock.feature.blocking.payment.PaymentConfig
import com.dollarblock.feature.blocking.payment.PaymentProvider
import com.dollarblock.feature.blocking.payment.PlayBillingManager
import com.dollarblock.feature.blocking.payment.StripeToken
import com.dollarblock.feature.onboarding.rememberAnimationsEnabled
import kotlinx.coroutines.launch
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.wallet.AutoResolveHelper
import com.google.android.gms.wallet.IsReadyToPayRequest
import com.google.android.gms.wallet.PaymentData
import com.google.android.gms.wallet.PaymentDataRequest
import com.google.android.gms.wallet.PaymentsClient
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

@AndroidEntryPoint
class BlockActivity : AppCompatActivity() {

    @Inject
    lateinit var blockPreferences: BlockPreferences

    @Inject
    lateinit var eventsRepository: EventsRepository

    @Inject
    lateinit var pricingRepository: PricingRepository

    // Caminho Stripe/Google Pay (E9) — só inicializado quando PaymentConfig.PROVIDER
    // é STRIPE_GOOGLE_PAY; mantido para reuso futuro (E16).
    private lateinit var paymentsClient: PaymentsClient

    // Caminho Google Play Billing (E16) — provider ativo.
    private var billingManager: PlayBillingManager? = null

    private var targetPackage: String = ""
    private var appLabel: String = ""

    /**
     * Estado da cobrança (pronto / em andamento / cortesia disponível). As transições são
     * a função pura [reduce] — ver [PaymentUiState] para a regra da saída de cortesia.
     */
    private val paymentState = MutableStateFlow(PaymentUiState())

    /** Aplica um evento de cobrança ao estado, na main thread. */
    private fun onPaymentEvent(event: PaymentEvent) {
        paymentState.value = paymentState.value.reduce(event)
    }
    private val unlocksPaidToday = MutableStateFlow(0)
    private val dayPassPrice = MutableStateFlow(GooglePayConfig.DEFAULT_PRICE)

    /** Preço localizado vindo do Play Billing (ex.: "R$ 1,99"); null usa o fallback. */
    private val billingPrice = MutableStateFlow<String?>(null)

    private val paymentLauncher =
        registerForActivityResult(StartIntentSenderForResult()) { result ->
            when (result.resultCode) {
                Activity.RESULT_OK -> {
                    val paymentData = result.data?.let { PaymentData.getFromIntent(it) }
                    if (paymentData != null) {
                        handlePaymentData(paymentData)
                    } else {
                        onPaymentError()
                    }
                }
                Activity.RESULT_CANCELED -> {
                    onPaymentEvent(PaymentEvent.Cancelled)
                    toast(R.string.pay_cancelled)
                }
                AutoResolveHelper.RESULT_ERROR -> {
                    val status = AutoResolveHelper.getStatusFromIntent(result.data)
                    Log.e(TAG, "Google Pay sheet error: $status")
                    onPaymentError()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        targetPackage = intent.getStringExtra(EXTRA_PACKAGE).orEmpty()
        appLabel = intent.getStringExtra(EXTRA_LABEL) ?: getString(R.string.app_name)
        when (PaymentConfig.PROVIDER) {
            PaymentProvider.PLAY_BILLING -> {
                val manager = PlayBillingManager(this, billingListener)
                billingManager = manager
                manager.connect()
                lifecycleScope.launch {
                    manager.ready.collect { onPaymentEvent(PaymentEvent.ReadinessChanged(it)) }
                }
                // Se a loja não responder dentro do timeout, o passe não tem como ser
                // cobrado — cai no mesmo estado de falha, com a saída de cortesia.
                lifecycleScope.launch {
                    delay(PaymentUiState.BILLING_READY_TIMEOUT_MS)
                    if (!paymentState.value.ready && !paymentState.value.inProgress) {
                        Log.e(TAG, "Billing not ready after ${PaymentUiState.BILLING_READY_TIMEOUT_MS}ms")
                    }
                    onPaymentEvent(PaymentEvent.ReadyTimeout)
                }
                lifecycleScope.launch { manager.formattedPrice.collect { billingPrice.value = it } }
            }
            PaymentProvider.STRIPE_GOOGLE_PAY -> {
                paymentsClient = GooglePayConfig.paymentsClient(this)
                checkReadyToPay()
                // Preço exibido/registrado vem do backend de pricing só no caminho Stripe;
                // no Play Billing o preço é o do produto no Play Console.
                lifecycleScope.launch { dayPassPrice.value = pricingRepository.getDayPassPrice() }
            }
        }
        lifecycleScope.launch { unlocksPaidToday.value = eventsRepository.unlocksPaidToday() }

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = goHome()
            },
        )

        setContent {
            DollarBlockTheme {
                val payment by paymentState.collectAsState()
                val paidToday by unlocksPaidToday.collectAsState()
                val price by dayPassPrice.collectAsState()
                val playPrice by billingPrice.collectAsState()
                BlockScreen(
                    appLabel = appLabel,
                    unlocksPaidToday = paidToday,
                    // Preço final exibido no recibo: o localizado do Play quando disponível,
                    // senão o fallback/pricing formatado em BRL.
                    priceText = playPrice ?: ("R$ " + price.replace('.', ',')),
                    provider = PaymentConfig.PROVIDER,
                    paymentReady = payment.ready,
                    paymentInProgress = payment.inProgress,
                    paymentFailed = payment.courtesyAvailable,
                    // Feature flag de dev: o botão de pagamento simulado só existe quando
                    // BlockingDevFlags.SIMULATED_PAYMENTS está ligado (e nunca em release).
                    showDebugSimulate = BuildConfig.DEBUG && BlockingDevFlags.SIMULATED_PAYMENTS,
                    onPay = ::startPayment,
                    onSimulatePayment = { onPaymentSuccess(PaymentMethod.SIMULATED) },
                    onCourtesyUnlock = ::onCourtesyUnlock,
                    onGoHome = ::goHome,
                )
            }
        }
    }

    override fun onDestroy() {
        billingManager?.disconnect()
        billingManager = null
        super.onDestroy()
    }

    private val billingListener = object : PlayBillingManager.PurchaseListener {
        // Os callbacks do Billing podem chegar fora da main thread — sempre repostar.
        override fun onPurchaseCompleted() {
            runOnUiThread {
                onPaymentSuccess(PaymentMethod.PLAY_BILLING)
            }
        }

        override fun onPurchaseFailed(cancelled: Boolean) {
            runOnUiThread {
                if (cancelled) {
                    // Desistência não é erro: continua sendo bloqueio normal.
                    onPaymentEvent(PaymentEvent.Cancelled)
                    toast(R.string.pay_cancelled)
                } else {
                    onPaymentError()
                }
            }
        }
    }

    private fun checkReadyToPay() {
        val request = IsReadyToPayRequest.fromJson(GooglePayConfig.isReadyToPayRequest().toString())
        paymentsClient.isReadyToPay(request).addOnCompleteListener { task ->
            val ready = runCatching { task.getResult(ApiException::class.java) }.getOrDefault(false)
            onPaymentEvent(PaymentEvent.ReadinessChanged(ready))
            // Sem Google Pay não há como cobrar o passe: mesma saída de cortesia.
            if (!ready) onPaymentEvent(PaymentEvent.ReadyTimeout)
        }
    }

    private fun startPayment() {
        // Nova tentativa limpa o estado de falha; se falhar de novo, onPaymentError o religa.
        onPaymentEvent(PaymentEvent.PayClicked)
        when (PaymentConfig.PROVIDER) {
            PaymentProvider.PLAY_BILLING -> startPlayBillingPayment()
            PaymentProvider.STRIPE_GOOGLE_PAY -> startGooglePayPayment()
        }
    }

    private fun startPlayBillingPayment() {
        val launched = billingManager?.launchPurchase(this) ?: false
        if (!launched) {
            Log.e(TAG, "launchBillingFlow did not start")
            onPaymentError()
        }
    }

    // ——— Caminho Stripe/Google Pay (E9) — desabilitado via PaymentConfig, mantido para reuso ———

    private fun startGooglePayPayment() {
        val request =
            PaymentDataRequest.fromJson(GooglePayConfig.paymentDataRequest(dayPassPrice.value).toString())
        paymentsClient.loadPaymentData(request).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val paymentData = task.result
                if (paymentData != null) {
                    handlePaymentData(paymentData)
                } else {
                    onPaymentError()
                }
            } else {
                when (val exception = task.exception) {
                    is ResolvableApiException ->
                        paymentLauncher.launch(
                            IntentSenderRequest.Builder(exception.resolution.intentSender).build(),
                        )
                    else -> {
                        Log.e(TAG, "loadPaymentData failed", exception)
                        onPaymentError()
                    }
                }
            }
        }
    }

    // Extracts the raw Stripe tokenization token from the Google Pay PaymentData JSON.
    // For the stripe gateway this is a JSON-stringified Stripe token object; the actual
    // tok_/pm_ id is pulled out later via StripeToken.extractId(). Returns null if the
    // token is missing (malformed response from Google Pay).
    private fun extractToken(paymentData: PaymentData): String? = runCatching {
        JSONObject(paymentData.toJson())
            .getJSONObject("paymentMethodData")
            .getJSONObject("tokenizationData")
            .getString("token")
    }.getOrNull()

    private fun handlePaymentData(paymentData: PaymentData) {
        val rawToken = extractToken(paymentData)
        val token = StripeToken.extractId(rawToken)
        if (token == null) {
            Log.e(TAG, "No usable Stripe token in Google Pay response (raw=${rawToken?.take(40)})")
            onPaymentError()
            return
        }
        Log.d(TAG, "Charging pkg=$targetPackage tokenId=${token.take(8)}… (rawLen=${rawToken?.length})")
        val idempotencyKey = UUID.randomUUID().toString()
        lifecycleScope.launch {
            val result = PaymentApiClient.charge(targetPackage, token, idempotencyKey)
            result.fold(
                onSuccess = { charge ->
                    Log.d(TAG, "Charge result status=${charge.status} pi=${charge.paymentIntentId}")
                    if (charge.status == "succeeded") {
                        onPaymentSuccess(PaymentMethod.GOOGLE_PAY)
                    } else {
                        onPaymentError()
                    }
                },
                onFailure = {
                    Log.e(TAG, "Charge failed", it)
                    onPaymentError()
                },
            )
        }
    }

    /**
     * Erro na cobrança (não desistência). Encerra o progresso e liga a saída de cortesia:
     * o usuário não pode ficar sem o app porque a nossa maquininha falhou.
     */
    private fun onPaymentError() {
        onPaymentEvent(PaymentEvent.Error)
        toast(R.string.pay_error)
    }

    /**
     * Libera o app até a meia-noite sem cobrar, depois de uma falha de cobrança. Registra
     * o desbloqueio no extrato com valor zero e método [PaymentMethod.COURTESY], para o
     * histórico deixar claro que nada foi cobrado.
     */
    private fun onCourtesyUnlock() {
        onPaymentEvent(PaymentEvent.Completed)
        if (targetPackage.isNotEmpty()) {
            blockPreferences.grantUnlockForToday(targetPackage)
            lifecycleScope.launch {
                eventsRepository.recordUnlock(
                    packageName = targetPackage,
                    appLabel = appLabel,
                    amount = COURTESY_AMOUNT,
                    currency = billingManager?.priceCurrency ?: GooglePayConfig.CURRENCY_CODE,
                    method = PaymentMethod.COURTESY,
                )
            }
        }
        toast(getString(R.string.pay_courtesy_granted))
        openTargetApp()
    }

    private fun onPaymentSuccess(method: String) {
        onPaymentEvent(PaymentEvent.Completed)
        if (targetPackage.isNotEmpty()) {
            blockPreferences.grantUnlockForToday(targetPackage)
            // No Play Billing o valor/moeda reais são os do produto no Play Console;
            // nos demais caminhos, o preço resolvido via pricing/fallback em BRL.
            val amount = billingManager?.priceAmount ?: dayPassPrice.value
            val currency = billingManager?.priceCurrency ?: GooglePayConfig.CURRENCY_CODE
            lifecycleScope.launch {
                eventsRepository.recordUnlock(
                    packageName = targetPackage,
                    appLabel = appLabel,
                    amount = amount,
                    currency = currency,
                    method = method,
                )
            }
        }
        toast(getString(R.string.pay_unlocked))
        openTargetApp()
    }

    private fun openTargetApp() {
        val launchIntent = packageManager.getLaunchIntentForPackage(targetPackage)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(launchIntent)
            finish()
        } else {
            goHome()
        }
    }

    private fun goHome() {
        startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        finish()
    }

    private fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    private fun toast(resId: Int) =
        Toast.makeText(this, resId, Toast.LENGTH_SHORT).show()

    @Suppress("unused")
    private fun openDollarBlock() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        )
        finish()
    }

    companion object {
        private const val TAG = "DollarBlockPay"

        /** Valor registrado no extrato para um desbloqueio de cortesia. */
        private const val COURTESY_AMOUNT = "0.00"
        const val EXTRA_LABEL = "extra_label"
        const val EXTRA_PACKAGE = "extra_package"
    }
}

@Composable
private fun BlockScreen(
    appLabel: String,
    unlocksPaidToday: Int,
    priceText: String,
    provider: PaymentProvider,
    paymentReady: Boolean,
    paymentInProgress: Boolean,
    paymentFailed: Boolean,
    showDebugSimulate: Boolean,
    onPay: () -> Unit,
    onSimulatePayment: () -> Unit,
    onCourtesyUnlock: () -> Unit,
    onGoHome: () -> Unit,
) {
    val quotes = stringArrayResource(R.array.home_quotes)
    val quote = remember { quotes.random() }
    // Mensagem do recibo sorteada por situação (1ª fatura do dia, 2ª, 3ª+), para a
    // tela não repetir o mesmo texto a cada bloqueio.
    val messagesRes = when {
        unlocksPaidToday >= 2 -> R.array.block_screen_messages_many
        unlocksPaidToday == 1 -> R.array.block_screen_messages_repeat
        else -> R.array.block_screen_messages_first
    }
    val messages = stringArrayResource(messagesRes)
    val message = remember(messagesRes) { messages.random() }
    val mintGlow = Color(0xFF64FFDA)
    // Nº e data do recibo: o momento em que a fatura foi emitida (decorativo, não é um id).
    val issuedAtMillis = remember { System.currentTimeMillis() }
    val invoiceNumber = remember { "%06d".format(issuedAtMillis / 1000 % 1_000_000) }
    val issuedAt = remember {
        val locale = Locale.getDefault()
        SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, "ddMMyyyyHHmm"), locale)
            .format(Date(issuedAtMillis))
    }

    // Sequência da cena (BlockHandover.kt): o guardião entrega o recibo → o carimbo cai →
    // a arte escurece e a frase aparece devagar. Sem animações no sistema, abre no fim.
    val animate = rememberAnimationsEnabled()
    val handover = remember { Animatable(if (animate) 0f else 1f) }
    val stampScale = remember { Animatable(if (animate) 2.4f else 1f) }
    val stampAlpha = remember { Animatable(if (animate) 0f else 1f) }
    val epilogue = remember { Animatable(if (animate) 0f else 1f) }
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(Unit) {
        if (!animate) return@LaunchedEffect
        delay(HANDOVER_DELAY_MS)
        handover.animateTo(1f, tween(HANDOVER_MS, easing = FastOutSlowInEasing))
        // O carimbo "cai" no recibo (escala 2.4 → 1) com haptic no impacto.
        delay(STAMP_DELAY_MS)
        stampAlpha.animateTo(1f, tween(120))
        stampScale.animateTo(
            targetValue = 1f,
            animationSpec = spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium),
        )
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        delay(EPILOGUE_DELAY_MS)
        epilogue.animateTo(1f, tween(EPILOGUE_MS, easing = LinearEasing))
    }

    // Onde está o recibo em branco da arte e onde o recibo de verdade termina, na raiz.
    var artBounds by remember { mutableStateOf<Rect?>(null) }
    var receiptBounds by remember { mutableStateOf<Rect?>(null) }
    val artReceipt = artBounds?.let { art ->
        val k = art.width / ART_W
        Rect(
            left = art.left + ArtReceipt.left * k,
            top = art.top + ArtReceipt.top * k,
            right = art.left + ArtReceipt.right * k,
            bottom = art.top + ArtReceipt.bottom * k,
        )
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(BlockArtBackground),
    ) {
        BlockHandoverArt(
            handover = handover.value,
            epilogue = epilogue.value,
            modifier = Modifier
                .fillMaxWidth()
                .height(maxWidth * (ART_H / ART_W))
                .onGloballyPositioned { artBounds = it.boundsInRoot() },
        )

        // Edge-to-edge: a arte cobre a tela toda, mas o conteúdo (inclusive o link do
        // rodapé) fica fora das barras de status/navegação do sistema.
        val density = LocalDensity.current
        val insetTop = with(density) { WindowInsets.safeDrawing.getTop(density).toDp() }
        val receiptAnchorTop = maxWidth * (ArtReceipt.top / ART_W) - insetTop
        var showEthicsDialog by remember { mutableStateOf(false) }

        BlockHandoverLayout(
            receiptAnchorTop = receiptAnchorTop,
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
            title = {
                Text(
                    text = stringResource(R.string.block_screen_title),
                    style = MaterialTheme.typography.headlineSmall.copy(
                        shadow = Shadow(Color.Black.copy(alpha = 0.6f), blurRadius = 12f),
                    ),
                    color = NeutralWhite,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp),
                )
            },
            // Frase de reflexão: a última coisa a aparecer, devagar, sobre a cena escurecida.
            quote = {
                Text(
                    text = "\"$quote\"",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontStyle = FontStyle.Italic,
                        lineHeight = 24.sp,
                    ),
                    color = mintGlow,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(horizontal = 32.dp)
                        .graphicsLayer { alpha = epilogue.value },
                )
            },
            stack = {
                BlockStack(
                    modifier = Modifier.padding(horizontal = 32.dp),
                    receipt = {
                        InvoiceReceipt(
                            appLabel = appLabel,
                            priceText = priceText,
                            message = message,
                            invoiceNumber = invoiceNumber,
                            issuedAt = issuedAt,
                            stampScale = stampScale,
                            stampAlpha = stampAlpha,
                            // Estreito e alinhado à mão do guardião: papel que ele segura,
                            // não um cartão de tela inteira.
                            modifier = Modifier
                                .widthIn(max = RECEIPT_MAX_WIDTH)
                                .offset {
                                    val art = artBounds ?: return@offset IntOffset.Zero
                                    val handX = art.left + ArtReceipt.center.x * art.width / ART_W
                                    IntOffset((handX - art.center.x).roundToInt(), 0)
                                }
                                .onGloballyPositioned { receiptBounds = it.boundsInRoot() }
                                .receiptHandover(handover.value, artReceipt, receiptBounds),
                        )
                    },
                    controlsAlpha = { ((handover.value - 0.5f) / 0.5f).coerceIn(0f, 1f) },
                ) {
                    if (paymentInProgress) {
                        CircularProgressIndicator(color = NeutralWhite)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.pay_processing),
                            style = MaterialTheme.typography.bodyMedium,
                            color = NeutralWhite,
                        )
                    } else if (paymentFailed) {
                        // Falha na cobrança: a culpa é nossa, então o passe do dia sai de graça.
                        // O botão de tentar de novo continua disponível pra quem quiser pagar.
                        PaymentFailedNotice(
                            appLabel = appLabel,
                            onCourtesyUnlock = onCourtesyUnlock,
                            onRetry = onPay.takeIf { paymentReady },
                        )
                    } else {
                        if (paymentReady) {
                            PayButton(
                                label = stringResource(
                                    when (provider) {
                                        PaymentProvider.PLAY_BILLING -> R.string.pay_day_pass
                                        PaymentProvider.STRIPE_GOOGLE_PAY -> R.string.pay_with_google
                                    },
                                ),
                                onClick = onPay,
                            )
                            // A dúvida "pra onde vai meu cartão?" nasce aqui — responde em uma linha.
                            if (provider == PaymentProvider.PLAY_BILLING) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = stringResource(R.string.privacy_payment_note),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = NeutralWhite.copy(alpha = 0.6f),
                                    textAlign = TextAlign.Center,
                                )
                            }
                        } else {
                            Text(
                                text = stringResource(
                                    when (provider) {
                                        PaymentProvider.PLAY_BILLING -> R.string.pay_billing_unavailable
                                        PaymentProvider.STRIPE_GOOGLE_PAY -> R.string.pay_unavailable
                                    },
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = NeutralWhite.copy(alpha = 0.85f),
                                textAlign = TextAlign.Center,
                            )
                        }
                        if (showDebugSimulate) {
                            Spacer(Modifier.height(12.dp))
                            OutlinedButton(
                                onClick = onSimulatePayment,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = NeutralWhite),
                                border = BorderStroke(1.dp, NeutralWhite.copy(alpha = 0.5f)),
                            ) {
                                Text(
                                    text = stringResource(R.string.pay_simulate),
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            }
                        }
                    }

                    if (!paymentInProgress) {
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = onGoHome) {
                            Text(
                                text = stringResource(R.string.block_screen_home),
                                style = MaterialTheme.typography.labelLarge,
                                color = NeutralWhite.copy(alpha = 0.85f),
                            )
                        }
                    }
                }
            },
            // Rodapé de transparência: o bloqueio é o contrato, não um sequestro. O link
            // abre a explicação de que pagar é opcional e a monitoria pode ser desativada.
            footer = {
                TextButton(
                    onClick = { showEthicsDialog = true },
                    modifier = Modifier.padding(bottom = 10.dp),
                ) {
                    Text(
                        text = stringResource(R.string.block_understand_link),
                        style = MaterialTheme.typography.labelMedium,
                        color = NeutralWhite.copy(alpha = 0.6f),
                    )
                }
            },
        )
        if (showEthicsDialog) {
            DollarBlockDialog(
                onDismissRequest = { showEthicsDialog = false },
                title = stringResource(R.string.block_understand_title),
                body = stringResource(R.string.block_understand_body),
                confirmText = stringResource(R.string.home_card_info_ok),
                onConfirm = { showEthicsDialog = false },
            )
        }
    }
}

/** O recibo e, abaixo dele, os botões — que só aparecem quando o recibo está chegando. */
@Composable
private fun BlockStack(
    receipt: @Composable () -> Unit,
    controlsAlpha: () -> Float,
    modifier: Modifier = Modifier,
    controls: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        receipt()
        Spacer(Modifier.height(28.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = controlsAlpha() },
            horizontalAlignment = Alignment.CenterHorizontally,
            content = controls,
        )
    }
}

@Composable
private fun ColumnScope.PaymentFailedNotice(
    appLabel: String,
    onCourtesyUnlock: () -> Unit,
    onRetry: (() -> Unit)?,
) {
    Text(
        text = stringResource(R.string.pay_failed_title),
        style = MaterialTheme.typography.titleMedium,
        color = NeutralWhite,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text = stringResource(R.string.pay_failed_body),
        style = MaterialTheme.typography.bodyMedium,
        color = NeutralWhite.copy(alpha = 0.85f),
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(20.dp))
    PayButton(
        label = stringResource(R.string.pay_failed_action, appLabel),
        onClick = onCourtesyUnlock,
    )
    if (onRetry != null) {
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = onRetry,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = NeutralWhite),
            border = BorderStroke(1.dp, NeutralWhite.copy(alpha = 0.5f)),
        ) {
            Text(
                text = stringResource(R.string.pay_failed_retry),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun PayButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Black,
            contentColor = Color.White,
        ),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}
