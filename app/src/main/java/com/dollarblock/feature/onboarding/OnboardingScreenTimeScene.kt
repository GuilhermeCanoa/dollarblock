package com.dollarblock.feature.onboarding

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.launch

// Arte num quadro de 200 × 264 unidades, escalada pela altura. O celular fica à esquerda do
// quadro, encostado no texto da página; o cronômetro, à direita dele.
private const val ART_W = 200f
private const val ART_H = 264f

/** Proporção largura/altura da cena — quem a coloca na página dá a altura e usa esta proporção. */
internal const val SCREEN_TIME_ASPECT = ART_W / ART_H

// Loop: o cronômetro conta até 2h30 de tela, segura um instante e zera.
private const val COUNT_S = 52f
private const val HOLD_S = 2f
private const val LOOP_S = COUNT_S + HOLD_S
private const val MAX_MINUTES = 150f
private const val STATIC_MINUTES = 84f

// Voltas do ponteiro por segundo.
private const val HAND_TURNS_PER_S = 0.175f

// Celular e a tela.
private val PhoneTopLeft = Offset(4f, 6f)
private val PhoneSize = Size(100f, 248f)
private val ScreenTopLeft = Offset(10f, 20f)
private val ScreenSize = Size(88f, 226f)

// Feed: cartões que sobem sem parar, cada um com o ícone de uma rede.
private const val CARD_H = 52f
private const val CARD_GAP = 8f
private const val FEED_SPEED = 40f // unidades por segundo
private val FeedOrder = listOf(
    SocialIcon.INSTAGRAM, SocialIcon.TIKTOK, SocialIcon.YOUTUBE, SocialIcon.FACEBOOK,
    SocialIcon.X, SocialIcon.SNAPCHAT, SocialIcon.TIKTOK, SocialIcon.DISCORD,
)

// Uma volta completa do feed (todos os cartões), em unidades da arte.
private val FEED_LOOP = (CARD_H + CARD_GAP) * FeedOrder.size

// Cronômetro à direita do celular.
private val WatchCenter = Offset(164f, 96f)
private const val WATCH_R = 23f

private val PhoneBody = Color(0xFF0B1F17)
private val PhoneRim = Color(0xFF2E6B4E)
private val ScreenBg = Color(0xFF133329)
private val CardBg = Color(0xFF215445)
private val CardLine = Color(0xFF4F8A75)
private val Mint = Color(0xFF64FFDA)
private val WatchFace = Color(0xFF0F2A21)

/**
 * "Primeiro, contar o tempo": um celular com o feed das redes sociais rolando sem fim e, ao
 * lado, um cronômetro contando o tempo de tela. O usuário pode arrastar o feed para cima ou
 * para baixo (com inércia); a rolagem automática segue por baixo. Sem animações do sistema,
 * o cronômetro fica parado em 1h 24m (o feed ainda arrasta). Decorativa: o texto da página já
 * diz o que ela mostra.
 */
@Composable
fun ScreenTimeScene(modifier: Modifier = Modifier) {
    val textMeasurer = rememberTextMeasurer()
    val animate = rememberAnimationsEnabled()
    val seconds = if (animate) {
        rememberInfiniteTransition(label = "screenTime").animateFloat(
            initialValue = 0f,
            targetValue = LOOP_S,
            animationSpec = infiniteRepeatable(tween((LOOP_S * 1000).toInt(), easing = LinearEasing), RepeatMode.Restart),
            label = "screenTimeSeconds",
        ).value
    } else {
        null
    }
    // Relógio próprio do feed: uma volta = a lista inteira, então o loop não dá salto.
    val autoScroll = if (animate) {
        rememberInfiniteTransition(label = "feed").animateFloat(
            initialValue = 0f,
            targetValue = FEED_LOOP,
            animationSpec = infiniteRepeatable(
                tween((FEED_LOOP / FEED_SPEED * 1000).toInt(), easing = LinearEasing),
                RepeatMode.Restart,
            ),
            label = "feedScroll",
        ).value
    } else {
        0f
    }
    // Rolagem do usuário, somada à automática: arrasta o feed e, ao soltar, segue com inércia.
    val manualScroll = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    Canvas(
        modifier = modifier.pointerInput(Unit) {
            val tracker = VelocityTracker()
            val artPerPx = ART_H / size.height
            detectVerticalDragGestures(
                onDragStart = {
                    tracker.resetTracking()
                    scope.launch { manualScroll.stop() }
                },
                onVerticalDrag = { change, dragAmount ->
                    change.consume()
                    tracker.addPosition(change.uptimeMillis, change.position)
                    // Arrastar para cima sobe o feed (mostra os posts de baixo).
                    scope.launch { manualScroll.snapTo(manualScroll.value - dragAmount * artPerPx) }
                },
                onDragEnd = {
                    val velocity = -tracker.calculateVelocity().y * artPerPx
                    scope.launch { manualScroll.animateDecay(velocity, exponentialDecay()) }
                },
            )
        },
    ) {
        val scale = size.height / ART_H
        val origin = Offset((size.width - ART_W * scale) / 2f, 0f)

        val minutes = if (seconds == null) STATIC_MINUTES else MAX_MINUTES * (seconds / COUNT_S).coerceAtMost(1f)
        val scroll = autoScroll + manualScroll.value

        withTransform({
            translate(origin.x, origin.y)
            scale(scale, scale, pivot = Offset.Zero)
        }) {
            drawOval(Color.Black.copy(alpha = 0.25f), Offset(0f, 252f), Size(108f, 9f))
            drawPhone(scroll)
            drawStopwatch(minutes, seconds)
        }
        drawTimeLabel(textMeasurer, minutes, origin + Offset(WatchCenter.x, WATCH_R + WatchCenter.y + 15f) * scale)
    }
}

private fun DrawScope.drawPhone(scroll: Float) {
    drawRoundRect(PhoneBody, PhoneTopLeft, PhoneSize, CornerRadius(18f))
    drawRoundRect(PhoneRim, PhoneTopLeft, PhoneSize, CornerRadius(18f), style = Stroke(2.8f))
    val screen = Path().apply {
        addRoundRect(
            RoundRect(
                ScreenTopLeft.x, ScreenTopLeft.y,
                ScreenTopLeft.x + ScreenSize.width, ScreenTopLeft.y + ScreenSize.height,
                CornerRadius(10f),
            ),
        )
    }
    clipPath(screen) {
        drawRect(ScreenBg, ScreenTopLeft, ScreenSize)
        val pitch = CARD_H + CARD_GAP
        // Módulo sempre positivo: o usuário pode rolar para trás do começo.
        val wrapped = ((scroll % FEED_LOOP) + FEED_LOOP) % FEED_LOOP
        val first = (wrapped / pitch).toInt()
        val shift = wrapped % pitch
        for (k in 0..4) {
            val y = ScreenTopLeft.y + 6f + k * pitch - shift
            drawFeedCard(FeedOrder[(first + k) % FeedOrder.size], y)
        }
        // Esmaece a ponta de cima e de baixo, como um feed que não acaba.
        drawRect(
            Brush.verticalGradient(
                0f to ScreenBg, 0.1f to Color.Transparent, 0.9f to Color.Transparent, 1f to ScreenBg,
                startY = ScreenTopLeft.y, endY = ScreenTopLeft.y + ScreenSize.height,
            ),
            ScreenTopLeft, ScreenSize,
        )
    }
    // Câmera no topo.
    drawRoundRect(PhoneRim.copy(alpha = 0.6f), Offset(PhoneTopLeft.x + 38f, PhoneTopLeft.y + 6f), Size(24f, 5f), CornerRadius(2.5f))
}

private fun DrawScope.drawFeedCard(kind: SocialIcon, y: Float) {
    val x = ScreenTopLeft.x + 6f
    val w = ScreenSize.width - 12f
    drawRoundRect(CardBg, Offset(x, y), Size(w, CARD_H), CornerRadius(8f))
    drawSocialIcon(kind, Offset(x + 19f, y + 18f), 28f, 1f)
    drawLine(CardLine, Offset(x + 40f, y + 13f), Offset(x + w - 8f, y + 13f), strokeWidth = 4.4f, cap = StrokeCap.Round)
    drawLine(CardLine, Offset(x + 40f, y + 23f), Offset(x + w - 20f, y + 23f), strokeWidth = 4.4f, cap = StrokeCap.Round)
    // "Foto" do post.
    drawRoundRect(CardLine.copy(alpha = 0.55f), Offset(x + 6f, y + 36f), Size(w - 12f, 10f), CornerRadius(4f))
}

private fun DrawScope.drawStopwatch(minutes: Float, seconds: Float?) {
    val c = WatchCenter
    // Coroa e botão lateral.
    drawRoundRect(PhoneRim, Offset(c.x - 3f, c.y - WATCH_R - 6f), Size(6f, 5f), CornerRadius(1.2f))
    drawRoundRect(PhoneRim, Offset(c.x - 5f, c.y - WATCH_R - 8f), Size(10f, 3f), CornerRadius(1.2f))
    rotate(40f, pivot = c) {
        drawRoundRect(PhoneRim, Offset(c.x - 2f, c.y - WATCH_R - 4f), Size(4f, 4f), CornerRadius(1f))
    }
    drawCircle(WatchFace, WATCH_R, c)
    // Arco da hora corrente enchendo, por baixo dos traços.
    val hourFrac = (minutes % 60f) / 60f
    drawArc(
        Mint.copy(alpha = 0.22f), -90f, 360f * hourFrac, useCenter = true,
        topLeft = Offset(c.x - WATCH_R + 2f, c.y - WATCH_R + 2f), size = Size((WATCH_R - 2f) * 2, (WATCH_R - 2f) * 2),
    )
    drawCircle(Mint, WATCH_R, c, style = Stroke(2.2f))
    for (i in 0 until 12) {
        val a = i / 12f * 2f * PI.toFloat()
        val inner = if (i % 3 == 0) WATCH_R - 5.5f else WATCH_R - 4f
        drawLine(
            Mint.copy(alpha = 0.7f),
            Offset(c.x + sin(a) * inner, c.y - cos(a) * inner),
            Offset(c.x + sin(a) * (WATCH_R - 2.2f), c.y - cos(a) * (WATCH_R - 2.2f)),
            strokeWidth = if (i % 3 == 0) 1.4f else 0.8f,
        )
    }
    val a = ((seconds ?: 0.43f) * HAND_TURNS_PER_S % 1f) * 2f * PI.toFloat()
    drawLine(Color.White, c, Offset(c.x + sin(a) * (WATCH_R - 6f), c.y - cos(a) * (WATCH_R - 6f)), strokeWidth = 1.6f, cap = StrokeCap.Round)
    drawCircle(Mint, 2f, c)
}

private fun formatScreenTime(minutes: Int): String =
    if (minutes < 60) "${minutes}m" else "${minutes / 60}h ${"%02d".format(minutes % 60)}m"

/** Tempo contado, embaixo do cronômetro — em px de tela, para o texto não escalar com a arte. */
private fun DrawScope.drawTimeLabel(textMeasurer: TextMeasurer, minutes: Float, center: Offset) {
    val layout = textMeasurer.measure(
        formatScreenTime(minutes.toInt()),
        TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White),
    )
    drawText(layout, topLeft = center - Offset(layout.size.width / 2f, layout.size.height / 2f))
}
