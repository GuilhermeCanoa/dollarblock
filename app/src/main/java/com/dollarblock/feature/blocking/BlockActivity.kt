package com.dollarblock.feature.blocking

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.text.format.DateFormat
import android.util.Log
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.dollarblock.BuildConfig
import com.dollarblock.MainActivity
import com.dollarblock.R
import com.dollarblock.core.designsystem.DollarBlockTheme
import com.dollarblock.core.designsystem.components.DollarBlockDialog
import com.dollarblock.data.local.prefs.BlockPreferences
import com.dollarblock.data.local.prefs.deviceCurrency
import com.dollarblock.data.repository.PricingRepository
import com.dollarblock.domain.model.AppCurrency
import com.dollarblock.domain.model.MoneyFormat
import com.dollarblock.domain.model.PaymentMethod
import com.dollarblock.domain.repository.EventsRepository
import com.dollarblock.feature.blocking.payment.GooglePayConfig
import com.dollarblock.feature.blocking.payment.PaymentApiClient
import com.dollarblock.feature.blocking.payment.PaymentConfig
import com.dollarblock.feature.blocking.payment.PaymentProvider
import com.dollarblock.feature.blocking.payment.PlayBillingManager
import com.dollarblock.feature.blocking.payment.StripeToken
import com.dollarblock.feature.blocking.scene.BlockBackground
import com.dollarblock.feature.blocking.scene.BlockGlowCenter
import com.dollarblock.feature.blocking.scene.BlockGlowMid
import com.dollarblock.feature.blocking.scene.BlockReceiptContent
import com.dollarblock.feature.blocking.scene.BlockScene
import com.dollarblock.feature.blocking.scene.BlockSceneChoreography
import com.dollarblock.feature.blocking.scene.BlockSceneRotation
import com.dollarblock.feature.blocking.scene.FitScene
import com.dollarblock.feature.blocking.scene.HeldReceiptScene
import com.dollarblock.feature.blocking.scene.MascotHeroScene
import com.dollarblock.feature.blocking.scene.StampScene
import com.dollarblock.feature.blocking.scene.controlsPaddingFor
import com.dollarblock.feature.blocking.scene.rememberBlockSceneClock
import com.dollarblock.service.nudge.NudgeEngine
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.wallet.AutoResolveHelper
import com.google.android.gms.wallet.IsReadyToPayRequest
import com.google.android.gms.wallet.PaymentData
import com.google.android.gms.wallet.PaymentDataRequest
import com.google.android.gms.wallet.PaymentsClient
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import kotlin.random.Random

@AndroidEntryPoint
class BlockActivity : AppCompatActivity() {

    @Inject
    lateinit var blockPreferences: BlockPreferences

    @Inject
    lateinit var eventsRepository: EventsRepository

    @Inject
    lateinit var pricingRepository: PricingRepository

    @Inject
    lateinit var nudgeEngine: NudgeEngine

    // Caminho Stripe/Google Pay (E9) — só inicializado quando PaymentConfig.PROVIDER
    // é STRIPE_GOOGLE_PAY; mantido para reuso futuro (E16).
    private lateinit var paymentsClient: PaymentsClient

    // Caminho Google Play Billing (E16) — provider ativo.
    private var billingManager: PlayBillingManager? = null

    /**
     * Moeda do fallback de preço (antes do Play responder) e do registro no extrato: a do
     * país no Play Billing; o caminho Stripe (desabilitado) só cobra em BRL.
     */
    private val fallbackCurrency: AppCurrency by lazy {
        if (PaymentConfig.PROVIDER == PaymentProvider.PLAY_BILLING) deviceCurrency() else AppCurrency.BRL
    }

    private var targetPackage: String = ""
    private var appLabel: String = ""
    private var scene: BlockScene = BlockScene.A_MASCOT
    private var sceneStartAtSeconds: Float = 0f

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
        pickScene(savedInstanceState)
        dayPassPrice.value = fallbackCurrency.dayPassPrice
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
                    scene = scene,
                    sceneStartAtSeconds = sceneStartAtSeconds,
                    appLabel = appLabel,
                    unlocksPaidToday = paidToday,
                    // Preço final exibido no recibo: o localizado do Play quando disponível,
                    // senão o fallback/pricing na moeda do país.
                    priceText = playPrice ?: MoneyFormat.formatStored(price, fallbackCurrency.code),
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

    /**
     * Cena desta abertura (E25), escolhida já no onCreate, síncrona e em memória — nada de
     * DataStore antes do primeiro frame (ADR-001). Recriada por mudança de configuração,
     * mantém a cena e abre na pose final; re-afirmada pelo serviço, continua a entrada de
     * onde a instância anterior estava ([BlockSceneRotation]). Em debug, o extra
     * [EXTRA_SCENE] (A, B ou C) força a cena; no release o extra é ignorado.
     */
    private fun pickScene(savedInstanceState: Bundle?) {
        val saved = savedInstanceState?.getString(STATE_SCENE)
            ?.let { name -> BlockScene.entries.firstOrNull { it.name == name } }
        if (saved != null) {
            scene = saved
            sceneStartAtSeconds = Float.MAX_VALUE
            return
        }
        val forced = if (BuildConfig.DEBUG) BlockScene.fromExtra(intent.getStringExtra(EXTRA_SCENE)) else null
        val now = SystemClock.elapsedRealtime()
        val pick = BlockSceneRotation.next(targetPackage, now, Random.Default, forced)
        scene = pick.scene
        sceneStartAtSeconds = (now - pick.startedAtMs) / 1000f
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_SCENE, scene.name)
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
                    currency = billingManager?.priceCurrency ?: fallbackCurrency.code,
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
            // Recibo do passe (E23/E24) — pelo escopo do engine: esta Activity fecha já já.
            nudgeEngine.onPassPaid(targetPackage, appLabel, unlocksPaidToday.value)
            // No Play Billing o valor/moeda reais são os do produto no Play Console;
            // nos demais caminhos, o preço resolvido via pricing/fallback na moeda do país.
            val amount = billingManager?.priceAmount ?: dayPassPrice.value
            val currency = billingManager?.priceCurrency ?: fallbackCurrency.code
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

        /** Só debug: força a cena da tela (A, B ou C) para revisar cada uma por adb. */
        const val EXTRA_SCENE = "extra_scene"
        private const val STATE_SCENE = "block_scene"
    }
}

@Composable
private fun BlockScreen(
    scene: BlockScene,
    sceneStartAtSeconds: Float,
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
    // Mensagem do recibo sorteada por situação (1ª fatura do dia, 2ª, 3ª+), para a
    // tela não repetir o mesmo texto a cada bloqueio.
    val messagesRes = when {
        unlocksPaidToday >= 2 -> R.array.block_screen_messages_many
        unlocksPaidToday == 1 -> R.array.block_screen_messages_repeat
        else -> R.array.block_screen_messages_first
    }
    val messages = stringArrayResource(messagesRes)
    val message = remember(messagesRes) { messages.random() }
    // Nº e data do recibo: o momento em que a fatura foi emitida (decorativo, não é um id).
    val issuedAtMillis = remember { System.currentTimeMillis() }
    val invoiceNumber = remember { "%06d".format(issuedAtMillis / 1000 % 1_000_000) }
    val issuedAt = remember {
        val locale = Locale.getDefault()
        SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, "ddMMyyyyHHmm"), locale)
            .format(Date(issuedAtMillis))
    }
    val receipt = BlockReceiptContent(appLabel, priceText, message, invoiceNumber, issuedAt)

    // A cena é só o slot visual (E25): roda a coreografia dela; os botões e o rodapé são os
    // mesmos nas três e aparecem em <= 1 s, sem depender da animação.
    val clock = rememberBlockSceneClock(BlockSceneChoreography.forScene(scene), sceneStartAtSeconds)
    var showEthicsDialog by remember { mutableStateOf(false) }
    val payLabel = when (provider) {
        // Nas cenas B e C o preço vai no botão; na A ele já está grande na tela.
        PaymentProvider.PLAY_BILLING ->
            if (scene == BlockScene.A_MASCOT) {
                stringResource(R.string.pay_day_pass)
            } else {
                stringResource(R.string.pay_day_pass_with_price, priceText)
            }
        PaymentProvider.STRIPE_GOOGLE_PAY -> stringResource(R.string.pay_with_google)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .blockBackground(),
    ) {
        // Edge-to-edge: o fundo cobre a tela toda, o conteúdo fica fora das barras do sistema.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            FitScene(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                when (scene) {
                    BlockScene.A_MASCOT -> MascotHeroScene(receipt = receipt, clock = clock)
                    BlockScene.B_BEHIND -> HeldReceiptScene(receipt = receipt, clock = clock)
                    BlockScene.C_STAMP -> StampScene(
                        receipt = receipt,
                        shortMessage = stringResource(R.string.block_scene_short_message),
                        clock = clock,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = controlsPaddingFor(scene))
                    .graphicsLayer { alpha = clock.controlsAlpha() },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BlockControls(
                    appLabel = appLabel,
                    payLabel = payLabel,
                    provider = provider,
                    paymentReady = paymentReady,
                    paymentInProgress = paymentInProgress,
                    paymentFailed = paymentFailed,
                    showDebugSimulate = showDebugSimulate,
                    onPay = onPay,
                    onSimulatePayment = onSimulatePayment,
                    onCourtesyUnlock = onCourtesyUnlock,
                    onGoHome = onGoHome,
                )
            }
            BlockFooter(
                provider = provider,
                onUnderstand = { showEthicsDialog = true },
                modifier = Modifier.padding(top = 10.dp, bottom = 6.dp),
            )
        }
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

/** Fundo escuro com um brilho esmeralda elíptico um pouco acima do centro (como no mock). */
private fun Modifier.blockBackground(): Modifier = drawBehind {
    drawRect(BlockBackground)
    val center = Offset(size.width * 0.5f, size.height * 0.42f)
    val rx = size.width * 1.2f
    val ry = size.height * 0.7f
    scale(rx / ry, 1f, pivot = center) {
        drawRect(
            Brush.radialGradient(
                0f to BlockGlowCenter,
                0.45f to BlockGlowMid,
                1f to BlockBackground,
                center = center,
                radius = ry,
            ),
            topLeft = Offset(center.x - ry, 0f),
            size = Size(ry * 2f, size.height),
        )
    }
}
