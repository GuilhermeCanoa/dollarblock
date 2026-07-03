package com.dollarblock.feature.blocking

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dollarblock.BuildConfig
import com.dollarblock.MainActivity
import com.dollarblock.R
import com.dollarblock.core.designsystem.BlockingRed
import com.dollarblock.core.designsystem.DollarBlockTheme
import com.dollarblock.core.designsystem.DollarGreenDark
import com.dollarblock.core.designsystem.NeutralWhite
import kotlinx.coroutines.delay
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.dollarblock.data.local.prefs.BlockPreferences
import com.dollarblock.data.repository.PricingRepository
import com.dollarblock.domain.model.PaymentMethod
import com.dollarblock.domain.repository.EventsRepository
import com.dollarblock.feature.blocking.payment.GooglePayConfig
import com.dollarblock.feature.blocking.payment.PaymentApiClient
import com.dollarblock.feature.blocking.payment.StripeToken
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

    private lateinit var paymentsClient: PaymentsClient
    private var targetPackage: String = ""
    private var appLabel: String = ""

    private val readyToPay = MutableStateFlow(false)
    private val paymentInProgress = MutableStateFlow(false)
    private val unlocksPaidToday = MutableStateFlow(0)
    private val dayPassPrice = MutableStateFlow(GooglePayConfig.DEFAULT_PRICE)

    private val paymentLauncher =
        registerForActivityResult(StartIntentSenderForResult()) { result ->
            when (result.resultCode) {
                Activity.RESULT_OK -> {
                    val paymentData = result.data?.let { PaymentData.getFromIntent(it) }
                    if (paymentData != null) {
                        handlePaymentData(paymentData)
                    } else {
                        paymentInProgress.value = false
                        toast(R.string.pay_error)
                    }
                }
                Activity.RESULT_CANCELED -> {
                    paymentInProgress.value = false
                    toast(R.string.pay_cancelled)
                }
                AutoResolveHelper.RESULT_ERROR -> {
                    val status = AutoResolveHelper.getStatusFromIntent(result.data)
                    Log.e(TAG, "Google Pay sheet error: $status")
                    paymentInProgress.value = false
                    toast(R.string.pay_error)
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        targetPackage = intent.getStringExtra(EXTRA_PACKAGE).orEmpty()
        appLabel = intent.getStringExtra(EXTRA_LABEL) ?: getString(R.string.app_name)
        paymentsClient = GooglePayConfig.paymentsClient(this)
        checkReadyToPay()
        lifecycleScope.launch { unlocksPaidToday.value = eventsRepository.unlocksPaidToday() }
        lifecycleScope.launch { dayPassPrice.value = pricingRepository.getDayPassPrice() }

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = goHome()
            },
        )

        setContent {
            DollarBlockTheme {
                val ready by readyToPay.collectAsState()
                val processing by paymentInProgress.collectAsState()
                val paidToday by unlocksPaidToday.collectAsState()
                val price by dayPassPrice.collectAsState()
                BlockScreen(
                    appLabel = appLabel,
                    unlocksPaidToday = paidToday,
                    price = price,
                    googlePayReady = ready,
                    paymentInProgress = processing,
                    showDebugSimulate = BuildConfig.DEBUG,
                    onPayWithGooglePay = ::startPayment,
                    onSimulatePayment = { onPaymentSuccess(PaymentMethod.SIMULATED) },
                    onGoHome = ::goHome,
                )
            }
        }
    }

    private fun checkReadyToPay() {
        val request = IsReadyToPayRequest.fromJson(GooglePayConfig.isReadyToPayRequest().toString())
        paymentsClient.isReadyToPay(request).addOnCompleteListener { task ->
            readyToPay.value = runCatching { task.getResult(ApiException::class.java) }.getOrDefault(false)
        }
    }

    private fun startPayment() {
        paymentInProgress.value = true
        val request =
            PaymentDataRequest.fromJson(GooglePayConfig.paymentDataRequest(dayPassPrice.value).toString())
        paymentsClient.loadPaymentData(request).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val paymentData = task.result
                if (paymentData != null) {
                    handlePaymentData(paymentData)
                } else {
                    paymentInProgress.value = false
                    toast(R.string.pay_error)
                }
            } else {
                when (val exception = task.exception) {
                    is ResolvableApiException ->
                        paymentLauncher.launch(
                            IntentSenderRequest.Builder(exception.resolution.intentSender).build(),
                        )
                    else -> {
                        Log.e(TAG, "loadPaymentData failed", exception)
                        paymentInProgress.value = false
                        toast(R.string.pay_error)
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
            paymentInProgress.value = false
            toast(R.string.pay_error)
            return
        }
        Log.d(TAG, "Charging pkg=$targetPackage tokenId=${token.take(8)}… (rawLen=${rawToken?.length})")
        val idempotencyKey = UUID.randomUUID().toString()
        lifecycleScope.launch {
            val result = PaymentApiClient.charge(targetPackage, token, idempotencyKey)
            paymentInProgress.value = false
            result.fold(
                onSuccess = { charge ->
                    Log.d(TAG, "Charge result status=${charge.status} pi=${charge.paymentIntentId}")
                    if (charge.status == "succeeded") {
                        onPaymentSuccess(PaymentMethod.GOOGLE_PAY)
                    } else {
                        toast(R.string.pay_error)
                    }
                },
                onFailure = {
                    Log.e(TAG, "Charge failed", it)
                    toast(R.string.pay_error)
                },
            )
        }
    }

    private fun onPaymentSuccess(method: String) {
        if (targetPackage.isNotEmpty()) {
            blockPreferences.grantUnlockForToday(targetPackage)
            lifecycleScope.launch {
                eventsRepository.recordUnlock(
                    packageName = targetPackage,
                    appLabel = appLabel,
                    amount = dayPassPrice.value,
                    currency = GooglePayConfig.CURRENCY_CODE,
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
        const val EXTRA_LABEL = "extra_label"
        const val EXTRA_PACKAGE = "extra_package"
    }
}

@Composable
private fun BlockScreen(
    appLabel: String,
    unlocksPaidToday: Int,
    price: String,
    googlePayReady: Boolean,
    paymentInProgress: Boolean,
    showDebugSimulate: Boolean,
    onPayWithGooglePay: () -> Unit,
    onSimulatePayment: () -> Unit,
    onGoHome: () -> Unit,
) {
    val quotes = stringArrayResource(R.array.home_quotes)
    val quote = remember { quotes.random() }
    val mintGlow = Color(0xFF64FFDA)

    // Fundo com gradiente vertical de marca (Velvet → tom intermediário) para um
    // momento de bloqueio "pesado e seguro" (styleguide §4).
    val backdrop = Brush.verticalGradient(
        listOf(DollarGreenDark, Color(0xFF0E3328), DollarGreenDark),
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backdrop),
        contentAlignment = Alignment.Center,
    ) {
        // Frase motivacional no topo
        Text(
            text = "\"$quote\"",
            style = MaterialTheme.typography.bodyMedium.copy(
                fontStyle = FontStyle.Italic,
                lineHeight = 20.sp,
            ),
            color = mintGlow,
            textAlign = TextAlign.Center,
            modifier = androidx.compose.ui.Modifier
                .align(Alignment.TopCenter)
                .padding(horizontal = 24.dp, vertical = 52.dp),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.block_screen_title),
                style = MaterialTheme.typography.headlineLarge,
                color = NeutralWhite,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            InvoiceReceipt(
                appLabel = appLabel,
                price = price,
                message = stringResource(
                    when {
                        unlocksPaidToday >= 2 -> R.string.block_screen_message_many
                        unlocksPaidToday == 1 -> R.string.block_screen_message_repeat
                        else -> R.string.block_screen_message
                    },
                ),
            )
            Spacer(Modifier.height(32.dp))

            if (paymentInProgress) {
                CircularProgressIndicator(color = NeutralWhite)
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.pay_processing),
                    style = MaterialTheme.typography.bodyMedium,
                    color = NeutralWhite,
                )
            } else {
                if (googlePayReady) {
                    GooglePayButton(onClick = onPayWithGooglePay)
                } else {
                    Text(
                        text = stringResource(R.string.pay_unavailable),
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
    }
}

/**
 * A fatura: papel de recibo com tipografia mono, valor grande e carimbo
 * "BLOQUEADO" estampado em diagonal com haptic (o momento-assinatura do app).
 */
@Composable
private fun InvoiceReceipt(
    appLabel: String,
    price: String,
    message: String,
    modifier: Modifier = Modifier,
) {
    val paper = Color(0xFFF7F4EC)
    val ink = Color(0xFF1C2B26)

    // Animação de estampa: o carimbo "cai" no recibo (escala 2.4 → 1) com haptic no impacto.
    val stampScale = remember { Animatable(2.4f) }
    val stampAlpha = remember { Animatable(0f) }
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(Unit) {
        delay(400)
        stampAlpha.animateTo(1f, tween(120))
        stampScale.animateTo(
            targetValue = 1f,
            animationSpec = spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium),
        )
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    Box(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(paper)
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.block_receipt_header),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                letterSpacing = 2.sp,
                color = ink.copy(alpha = 0.55f),
            )
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = appLabel,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    color = ink,
                    textAlign = TextAlign.Center,
                )
                StampText(stampScale = stampScale, stampAlpha = stampAlpha)
            }
            Spacer(Modifier.height(14.dp))
            DashedDivider(ink.copy(alpha = 0.35f))
            Spacer(Modifier.height(14.dp))
            Text(
                text = message,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = ink.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(14.dp))
            DashedDivider(ink.copy(alpha = 0.35f))
            Spacer(Modifier.height(14.dp))
            Text(
                text = "R$ " + price.replace('.', ','),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 36.sp,
                color = ink,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.block_receipt_caption),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = ink.copy(alpha = 0.6f),
            )
        }
    }
}

@Composable
private fun StampText(
    stampScale: Animatable<Float, AnimationVector1D>,
    stampAlpha: Animatable<Float, AnimationVector1D>,
) {
    Text(
        text = stringResource(R.string.block_stamp),
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Black,
        fontSize = 30.sp,
        letterSpacing = 3.sp,
        color = BlockingRed,
        modifier = Modifier
            .graphicsLayer {
                rotationZ = -14f
                scaleX = stampScale.value
                scaleY = stampScale.value
                alpha = stampAlpha.value
            }
            .border(3.dp, BlockingRed, RoundedCornerShape(6.dp))
            .padding(horizontal = 14.dp, vertical = 6.dp),
    )
}

@Composable
private fun DashedDivider(color: Color, modifier: Modifier = Modifier) {
    Canvas(
        modifier
            .fillMaxWidth()
            .height(1.dp),
    ) {
        drawLine(
            color = color,
            start = Offset(0f, 0f),
            end = Offset(size.width, 0f),
            strokeWidth = 2f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
        )
    }
}

@Composable
private fun GooglePayButton(onClick: () -> Unit) {
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
            text = stringResource(R.string.pay_with_google),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}
