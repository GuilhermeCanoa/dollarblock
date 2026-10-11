package com.dollarblock.feature.onboarding

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.dollarblock.R
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

/** As penetras: rede social (nome de marca, não traduz) e a isca de cada uma. */
private class Spam(val icon: SocialIcon, val app: String, val message: Int)

private val Spams = listOf(
    Spam(SocialIcon.INSTAGRAM, "Instagram", R.string.onb_final_spam_instagram),
    Spam(SocialIcon.TIKTOK, "TikTok", R.string.onb_final_spam_tiktok),
    Spam(SocialIcon.YOUTUBE, "YouTube", R.string.onb_final_spam_youtube),
    Spam(SocialIcon.FACEBOOK, "Facebook", R.string.onb_final_spam_facebook),
    Spam(SocialIcon.X, "X", R.string.onb_final_spam_x),
)

private const val ENTER_MS = 1500
private const val LINGER_MS = 350L
private const val FLY_MS = 650
private const val REST_MS = 2600L

/**
 * O ciclo das notificações penetras, dividido entre a página (que desenha o peteleco e o cesto
 * balançando) e [SpamIntruder] (que desenha a notificação).
 */
internal class SpamState {
    /** 0 = fora da tela, à esquerda; 1 = encostada na mão livre do segurança. */
    val enter = Animatable(0f)
    /** 0..1 do voo do tapa até o cesto. */
    val fly = Animatable(0f)
    val swat = Animatable(0f)
    val wobble = Animatable(0f)
    var index by mutableIntStateOf(0)
    /** Onde estava a entrada quando levou o tapa (o voo sai dali). */
    var hitAt by mutableFloatStateOf(1f)
    val taps = Channel<Unit>(Channel.CONFLATED)
}

/**
 * Enquanto [running], a cada poucos segundos uma notificação de rede social tenta entrar pela
 * esquerda até a mão do segurança, leva um peteleco e cai no cesto. Tocar nela antecipa o tapa.
 */
@Composable
internal fun rememberSpamState(running: Boolean): SpamState {
    val state = remember { SpamState() }
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        delay(900)
        while (true) {
            state.fly.snapTo(0f)
            state.enter.snapTo(0f)
            while (state.taps.tryReceive().isSuccess) Unit
            val entering = launch { state.enter.animateTo(1f, tween(ENTER_MS, easing = FastOutSlowInEasing)) }
            withTimeoutOrNull(ENTER_MS + LINGER_MS) { state.taps.receive() }
            entering.cancel()
            state.hitAt = state.enter.value
            launch {
                state.swat.animateTo(1f, tween(90))
                state.swat.animateTo(0f, tween(320))
            }
            delay(70)
            state.fly.animateTo(1f, tween(FLY_MS, easing = LinearEasing))
            launch {
                state.wobble.snapTo(1f)
                state.wobble.animateTo(0f, tween(600))
            }
            state.index = (state.index + 1) % Spams.size
            delay(REST_MS)
        }
    }
    return state
}

private fun lerp(a: Offset, b: Offset, t: Float) = a + (b - a) * t

/**
 * A notificação penetra, posicionada em px da página: entra até [swatPoint] (a mão do
 * segurança) e, no tapa, voa num arco girando e encolhendo até [binMouth] (a boca do cesto).
 */
@Composable
internal fun SpamIntruder(state: SpamState, swatPoint: Offset, binMouth: Offset) {
    var cardW by remember { mutableFloatStateOf(0f) }
    var cardH by remember { mutableFloatStateOf(0f) }
    val spam = Spams[state.index]
    val name = spam.app
    val description = stringResource(R.string.onb_final_spam_description, name)
    val shape = RoundedCornerShape(16.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .onSizeChanged {
                cardW = it.width.toFloat()
                cardH = it.height.toFloat()
            }
            .offset {
                val w = cardW
                val h = cardH
                // Encostada na mão: borda direita na mão, centro na altura dela. Entra de fora
                // da tela (além da margem da página), um pouco mais alta, descendo até ali.
                val target = Offset(swatPoint.x - w - 2.dp.toPx(), swatPoint.y - h / 2f)
                val start = Offset(-(w + 48.dp.toPx()), target.y - 60.dp.toPx())
                fun entering(e: Float) = lerp(start, target, e)
                val f = state.fly.value
                val pos = if (f <= 0f) {
                    entering(state.enter.value)
                } else {
                    // Arco do tapa: sobe e cai na boca do cesto (pelo centro do cartão).
                    val c0 = entering(state.hitAt) + Offset(w / 2f, h / 2f)
                    val c1 = Offset((c0.x + binMouth.x) / 2f, minOf(c0.y, binMouth.y) - 90.dp.toPx())
                    val u = 1f - f
                    val c = c0 * (u * u) + c1 * (2f * u * f) + binMouth * (f * f)
                    c - Offset(w / 2f, h / 2f)
                }
                IntOffset(pos.x.roundToInt(), pos.y.roundToInt())
            }
            .graphicsLayer {
                val f = state.fly.value
                rotationZ = -480f * f
                scaleX = 1f - 0.8f * f
                scaleY = 1f - 0.8f * f
                alpha = (state.enter.value * 3f).coerceAtMost(1f) * (1f - ((f - 0.8f) / 0.2f).coerceIn(0f, 1f))
            }
            .width(184.dp)
            .shadow(8.dp, shape)
            .background(MaterialTheme.colorScheme.surfaceVariant, shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), shape)
            .clickable { state.taps.trySend(Unit) }
            .semantics { contentDescription = description }
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Canvas(Modifier.size(26.dp)) {
            drawSocialIcon(spam.icon, center, size.minDimension, 1f)
        }
        Column(Modifier.padding(start = 8.dp)) {
            Text(
                text = name + " · " + stringResource(R.string.onb_final_note_when),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(spam.message),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
