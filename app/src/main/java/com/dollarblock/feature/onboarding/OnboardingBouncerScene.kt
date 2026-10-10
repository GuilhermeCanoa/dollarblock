package com.dollarblock.feature.onboarding

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.dollarblock.R
import com.dollarblock.feature.blocking.payment.GooglePayConfig
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin

// Arte num quadro de 300 × 210 unidades, escalada pela altura e centrada na largura.
private const val ART_W = 300f
private const val ART_H = 210f

/** Proporção largura/altura da cena — quem a coloca na página dá a largura ou a altura. */
internal const val BOUNCER_ASPECT = ART_W / ART_H

private const val GROUND = 196f

// Ritmo: sozinho, o feed rola devagar e o cronômetro estoura em AUTO_FILL_S; cada
// DRAG_FILL unidades de rolagem do usuário (arrasto ou inércia) equivalem a um limite inteiro.
private const val AUTO_FILL_S = 14f / 1.5f
private const val DRAG_FILL = 1200f
private const val AUTO_FEED_SPEED = 14f
private const val FLING_DECAY = 4f
private const val CLOSED_HOLD_S = 4f
private const val SWITCH_S = 0.5f
private const val STAMP_DELAY_S = 0.4f
private const val STAMP_DROP_S = 0.35f

// Cenário.
private val PhoneTopLeft = Offset(14f, 22f)
private val PhoneSize = Size(86f, 174f)
private val ScreenTopLeft = Offset(20f, 34f)
private val ScreenSize = Size(74f, 150f)
private const val POST_A_X = 120f
private const val POST_B_X = 168f
private const val POST_TOP = 132f
private val WatchCenter = Offset(272f, 34f)
private const val WATCH_R = 16f
private const val BOUNCER_IDLE_X = 216f
private const val BOUNCER_BLOCK_X = 198f

// Feed na tela do celular.
private const val CARD_H = 40f
private const val CARD_GAP = 6f
private val FeedOrder = listOf(
    SocialIcon.INSTAGRAM, SocialIcon.TIKTOK, SocialIcon.YOUTUBE, SocialIcon.FACEBOOK,
    SocialIcon.X, SocialIcon.SNAPCHAT, SocialIcon.TIKTOK, SocialIcon.DISCORD,
)
private val FEED_LOOP = (CARD_H + CARD_GAP) * FeedOrder.size

// O app "bloqueado" na miniatura da tela de bloqueio (nome de marca, não traduz).
private const val BLOCKED_APP = "Instagram"

private val PhoneBody = Color(0xFF0B1F17)
private val PhoneRim = Color(0xFF2E6B4E)
private val ScreenBg = Color(0xFF133329)
private val CardBg = Color(0xFF215445)
private val CardLine = Color(0xFF4F8A75)
private val Mint = Color(0xFF64FFDA)
private val StopRed = Color(0xFFFF5252)
private val Brass = Color(0xFFD9A94E)
private val BrassDark = Color(0xFF8F6A22)
private val BrassLight = Color(0xFFF5DA8E)
private val Velvet = Color(0xFFB3263A)
private val VelvetLight = Color(0xFFE0525F)
private val SuitDark = Color(0xFF1B2622)
private val SuitLine = Color(0xFF0A100E)
private val Sleeve = Color(0xFF2C3B35)
private val Shirt = Color(0xFFEFF5F0)
private val Tie = Color(0xFF00E676)
private val Shades = Color(0xFF0A0F0D)
private val Wire = Color(0xFFB8C4BE)

// Tela de bloqueio real (BlockActivity): fundo, papel e tinta do recibo, botão de pagar.
private val BlockBgDark = Color(0xFF0A2A1E)
private val BlockBgMid = Color(0xFF0E3328)
private val ReceiptPaper = Color(0xFFF7F4EC)
private val ReceiptInk = Color(0xFF1C2B26)
private val PayStart = Color(0xFF00E676)
private val PayEnd = Color(0xFF64FFDA)

private fun smooth(x: Float): Float {
    val c = x.coerceIn(0f, 1f)
    return c * c * (3f - 2f * c)
}

/**
 * Estado da cena, avançado quadro a quadro: o feed, o uso (0..1 do limite) e o momento em
 * que a corda fechou. Fechado, o feed congela; depois de [CLOSED_HOLD_S] reabre e zera.
 */
private class BouncerState {
    var now by mutableFloatStateOf(0f)
    var feed by mutableFloatStateOf(0f)
    var usage by mutableFloatStateOf(0f)
    var closedAt by mutableFloatStateOf(-1f)
    var rattleAt by mutableFloatStateOf(-100f)
    private var velocity = 0f

    val isClosed get() = closedAt >= 0f

    /** 0 = aberto, 1 = corda fechada (com a subida e a descida suaves). */
    val closedness: Float
        get() = if (!isClosed) 0f else
            smooth((now - closedAt) / SWITCH_S) * (1f - smooth((now - closedAt - CLOSED_HOLD_S) / SWITCH_S))

    /** O que o cronômetro mostra: o uso, e ao reabrir, voltando a zero. */
    val shownUsage: Float
        get() = if (isClosed && now - closedAt > CLOSED_HOLD_S) {
            1f - smooth((now - closedAt - CLOSED_HOLD_S) / SWITCH_S)
        } else {
            usage
        }

    fun step(dt: Float, auto: Boolean) {
        now += dt
        if (isClosed) {
            if (now - closedAt > CLOSED_HOLD_S + SWITCH_S) {
                closedAt = -1f
                usage = 0f
            }
            return
        }
        val moved = velocity * dt
        feed += moved
        velocity *= exp(-FLING_DECAY * dt)
        if (abs(velocity) < 1f) velocity = 0f
        if (auto) feed += AUTO_FEED_SPEED * dt
        addUsage(abs(moved) / DRAG_FILL + if (auto) dt / AUTO_FILL_S else 0f)
    }

    /** Arrasto do usuário, em unidades da arte (positivo = feed sobe). */
    fun drag(amount: Float) {
        if (isClosed) {
            if (now - rattleAt > 0.35f) rattleAt = now
            return
        }
        feed += amount
        addUsage(abs(amount) / DRAG_FILL)
    }

    fun fling(speed: Float) {
        velocity = if (isClosed) 0f else speed
    }

    private fun addUsage(amount: Float) {
        usage = (usage + amount).coerceAtMost(1f)
        if (usage >= 1f && !isClosed) {
            closedAt = now
            velocity = 0f
        }
    }
}

/** Textos da tela de bloqueio em miniatura — os mesmos da BlockActivity. */
private class BlockTexts(
    val title: String,
    val header: String,
    val stamp: String,
    val price: String,
    val caption: String,
    val pay: String,
)

/**
 * "Agora, a tranca": o segurança da porta. O celular é a porta da balada, com a placa
 * ABERTO e um feed das redes rolando na tela, que o usuário pode arrastar (com inércia).
 * Cada rolagem adianta o cronômetro; sozinho ele também anda, devagar. No limite, o
 * segurança (o escudo do DollarBlock, de óculos escuros e terno) engancha a corda de veludo e
 * levanta a mão, a placa vira FECHADO e a tela do celular mostra uma miniatura da tela de
 * bloqueio real (fatura, carimbo BLOQUEADO caindo, preço do passe). Tentar rolar trancado
 * faz o segurança balançar a cabeça. Reabre sozinho alguns segundos depois. Sem animações
 * do sistema, nada anda sozinho — só o arrasto do usuário. Decorativa para o leitor de tela.
 */
@Composable
fun BouncerScene(modifier: Modifier = Modifier) {
    val textMeasurer = rememberTextMeasurer()
    val texts = BlockTexts(
        title = stringResource(R.string.block_screen_title),
        header = stringResource(R.string.block_receipt_header),
        stamp = stringResource(R.string.block_stamp),
        price = "R$ " + GooglePayConfig.DEFAULT_PRICE.replace('.', ','),
        caption = stringResource(R.string.block_receipt_caption),
        pay = stringResource(R.string.pay_day_pass),
    )
    val animate = rememberAnimationsEnabled()
    val state = remember { BouncerState() }
    LaunchedEffect(animate) {
        var last = withFrameNanos { it }
        while (true) {
            withFrameNanos { frame ->
                state.step(((frame - last) / 1e9f).coerceAtMost(0.05f), auto = animate)
                last = frame
            }
        }
    }

    Canvas(
        modifier = modifier.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                // Só a tela do celular captura o dedo; fora dela, a página rola normalmente.
                val scale = size.height / ART_H
                val originX = (size.width - ART_W * scale) / 2f
                val screen = Rect(
                    Offset(originX + ScreenTopLeft.x * scale, ScreenTopLeft.y * scale),
                    Size(ScreenSize.width * scale, ScreenSize.height * scale),
                )
                if (!screen.contains(down.position)) return@awaitEachGesture
                var overSlop = 0f
                val drag = awaitVerticalTouchSlopOrCancellation(down.id) { change, over ->
                    change.consume()
                    overSlop = over
                } ?: return@awaitEachGesture
                val tracker = VelocityTracker()
                tracker.addPosition(drag.uptimeMillis, drag.position)
                // Arrastar para cima sobe o feed.
                state.drag(-overSlop / scale)
                verticalDrag(drag.id) { change ->
                    tracker.addPosition(change.uptimeMillis, change.position)
                    state.drag(-change.positionChange().y / scale)
                    change.consume()
                }
                state.fling(-tracker.calculateVelocity().y / scale)
            }
        },
    ) {
        val scale = size.height / ART_H
        val origin = Offset((size.width - ART_W * scale) / 2f, 0f)
        val closed = state.closedness
        val sinceRattle = state.now - state.rattleAt
        val headShake = if (state.isClosed && sinceRattle < 0.6f) sin(sinceRattle * 30f) * 3f * (1f - sinceRattle / 0.6f) else 0f

        withTransform({
            translate(origin.x, origin.y)
            scale(scale, scale, pivot = Offset.Zero)
        }) {
            clipRect(0f, 0f, ART_W, ART_H) {
                drawPhoneDoor(state.feed, closed)
                drawPosts()
                drawRope(closed)
                drawStopwatch(state.shownUsage, state.now, closed)
                val bouncerX = BOUNCER_IDLE_X + (BOUNCER_BLOCK_X - BOUNCER_IDLE_X) * closed
                drawBouncer(bouncerX, GROUND + sin(state.now * 2.4f) * 0.8f, raise = closed, t = state.now, headDx = headShake)
            }
        }
        if (closed > 0f) {
            val sinceClose = state.now - state.closedAt
            val stamp = ((sinceClose - STAMP_DELAY_S) / STAMP_DROP_S).coerceIn(0f, 1f)
            val screen = Rect(origin + ScreenTopLeft * scale, Size(ScreenSize.width * scale, ScreenSize.height * scale))
            drawMiniBlockScreen(textMeasurer, texts, screen, 8f * scale, closed, stamp, headShake * scale)
        }
    }
}

// ---------------------------------------------------------------------------------------
// Cenário: a porta (celular com o feed), os pedestais e a corda
// ---------------------------------------------------------------------------------------

private fun DrawScope.drawPhoneDoor(feed: Float, closed: Float) {
    drawOval(Color.Black.copy(alpha = 0.25f), Offset(8f, GROUND - 4f), Size(98f, 8f))
    drawRoundRect(PhoneBody, PhoneTopLeft, PhoneSize, CornerRadius(14f))
    drawRoundRect(PhoneRim, PhoneTopLeft, PhoneSize, CornerRadius(14f), style = Stroke(2.4f))
    val screen = Path().apply {
        addRoundRect(
            RoundRect(
                ScreenTopLeft.x, ScreenTopLeft.y,
                ScreenTopLeft.x + ScreenSize.width, ScreenTopLeft.y + ScreenSize.height,
                CornerRadius(8f),
            ),
        )
    }
    clipPath(screen) {
        drawRect(ScreenBg, ScreenTopLeft, ScreenSize)
        val pitch = CARD_H + CARD_GAP
        // Módulo sempre positivo: dá para rolar para trás do começo.
        val wrapped = ((feed % FEED_LOOP) + FEED_LOOP) % FEED_LOOP
        val first = (wrapped / pitch).toInt()
        val shift = wrapped % pitch
        for (k in 0..4) {
            drawFeedCard(FeedOrder[(first + k) % FeedOrder.size], ScreenTopLeft.y + 5f + k * pitch - shift)
        }
        // O brilho da "porta da balada" por cima do feed enquanto está aberto.
        drawRect(
            Brush.verticalGradient(
                listOf(Mint.copy(alpha = 0.22f), Color.Transparent, Mint.copy(alpha = 0.1f)),
                startY = ScreenTopLeft.y,
                endY = ScreenTopLeft.y + ScreenSize.height,
            ),
            ScreenTopLeft, ScreenSize,
            alpha = 1f - closed,
        )
    }
    drawRoundRect(PhoneRim.copy(alpha = 0.6f), Offset(PhoneTopLeft.x + 33f, PhoneTopLeft.y + 5f), Size(20f, 4f), CornerRadius(2f))
}

private fun DrawScope.drawFeedCard(kind: SocialIcon, y: Float) {
    val x = ScreenTopLeft.x + 5f
    val w = ScreenSize.width - 10f
    drawRoundRect(CardBg, Offset(x, y), Size(w, CARD_H), CornerRadius(6f))
    drawSocialIcon(kind, Offset(x + 12f, y + 12f), 16f, 1f)
    drawLine(CardLine, Offset(x + 25f, y + 9f), Offset(x + w - 6f, y + 9f), strokeWidth = 3f, cap = StrokeCap.Round)
    drawLine(CardLine, Offset(x + 25f, y + 16f), Offset(x + w - 15f, y + 16f), strokeWidth = 3f, cap = StrokeCap.Round)
    drawRoundRect(CardLine.copy(alpha = 0.55f), Offset(x + 5f, y + 25f), Size(w - 10f, 9f), CornerRadius(3f))
}

private fun DrawScope.drawPosts() {
    listOf(POST_A_X, POST_B_X).forEach { x ->
        drawOval(Color.Black.copy(alpha = 0.25f), Offset(x - 13f, GROUND - 3f), Size(26f, 6f))
        drawOval(BrassDark, Offset(x - 11f, GROUND - 6f), Size(22f, 7f))
        drawOval(Brass, Offset(x - 11f, GROUND - 7.5f), Size(22f, 6f))
        drawRect(Brass, Offset(x - 2.5f, POST_TOP), Size(5f, GROUND - POST_TOP - 4f))
        drawLine(BrassLight, Offset(x - 1f, POST_TOP + 2f), Offset(x - 1f, GROUND - 8f), strokeWidth = 1f)
        drawCircle(Brass, 5f, Offset(x, POST_TOP - 2f))
        drawCircle(BrassLight, 1.6f, Offset(x - 1.6f, POST_TOP - 3.6f))
    }
}

/** A corda: solta, pende do pedestal da esquerda; fechada, engancha no da direita. */
private fun DrawScope.drawRope(closed: Float) {
    val start = Offset(POST_A_X + 3f, POST_TOP + 1f)
    val hanging = Offset(POST_A_X + 7f, GROUND - 22f)
    val hooked = Offset(POST_B_X - 3f, POST_TOP + 1f)
    val end = hanging + (hooked - hanging) * closed
    val mid = (start + end) / 2f
    val control = Offset(mid.x + 7f * (1f - closed), mid.y + 22f * closed)
    val rope = Path().apply {
        moveTo(start.x, start.y)
        quadraticTo(control.x, control.y, end.x, end.y)
    }
    drawPath(rope, Velvet, style = Stroke(5f, cap = StrokeCap.Round))
    drawPath(rope, VelvetLight, style = Stroke(1.4f, cap = StrokeCap.Round))
    drawCircle(Brass, 2.6f, end)
    drawCircle(Brass, 2.6f, start)
}

/** O mesmo cronômetro da Medição: enche até a marca do limite e fica vermelho ao chegar. */
private fun DrawScope.drawStopwatch(fill: Float, t: Float, closed: Float) {
    val c = WatchCenter
    val ring = if (fill >= 1f) StopRed else Mint
    drawRoundRect(PhoneRim, Offset(c.x - 2.5f, c.y - WATCH_R - 5f), Size(5f, 4f), CornerRadius(1f))
    drawRoundRect(PhoneRim, Offset(c.x - 4.5f, c.y - WATCH_R - 7f), Size(9f, 3f), CornerRadius(1f))
    drawCircle(Color(0xFF0F2A21), WATCH_R, c)
    drawArc(
        ring.copy(alpha = 0.25f), -90f, 270f * fill, useCenter = true,
        topLeft = Offset(c.x - WATCH_R + 2f, c.y - WATCH_R + 2f), size = Size((WATCH_R - 2f) * 2, (WATCH_R - 2f) * 2),
    )
    drawCircle(ring, WATCH_R, c, style = Stroke(2f))
    // Marca do limite (3/4 da volta).
    drawLine(StopRed, Offset(c.x - WATCH_R + 1f, c.y), Offset(c.x - WATCH_R + 6f, c.y), strokeWidth = 2.4f, cap = StrokeCap.Round)
    val a = 270f * fill / 180f * PI.toFloat()
    drawLine(Color.White, c, Offset(c.x + sin(a) * (WATCH_R - 5f), c.y - cos(a) * (WATCH_R - 5f)), strokeWidth = 1.6f, cap = StrokeCap.Round)
    drawCircle(ring, 1.8f, c)
    if (closed > 0f) {
        // Pulso de alerta no limite.
        val pulse = t % 1f
        drawCircle(StopRed.copy(alpha = (1f - pulse) * 0.5f * closed), WATCH_R + 3f + pulse * 6f, c, style = Stroke(1.5f))
    }
}

// ---------------------------------------------------------------------------------------
// A tela de bloqueio em miniatura — em px de tela, para o texto ficar nítido
// ---------------------------------------------------------------------------------------

private fun TextMeasurer.line(
    text: String,
    size: TextUnit,
    color: Color,
    maxWidth: Float,
    weight: FontWeight = FontWeight.Normal,
    mono: Boolean = false,
    maxLines: Int = 1,
    letterSpacing: TextUnit = TextUnit.Unspecified,
): TextLayoutResult = measure(
    text,
    TextStyle(
        fontSize = size,
        fontWeight = weight,
        color = color,
        fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
        textAlign = TextAlign.Center,
        letterSpacing = letterSpacing,
        lineHeight = size * 1.15f,
    ),
    overflow = TextOverflow.Ellipsis,
    maxLines = maxLines,
    constraints = Constraints(minWidth = maxWidth.toInt(), maxWidth = maxWidth.toInt()),
)

/**
 * Miniatura da tela de bloqueio real (BlockActivity): fundo verde-escuro, "Sua fatura
 * chegou", o recibo de papel com o nome do app e o carimbo BLOQUEADO caindo, o preço do
 * passe e o botão de pagar. Sobe por cima do feed congelado quando a corda fecha.
 */
private fun DrawScope.drawMiniBlockScreen(
    textMeasurer: TextMeasurer,
    texts: BlockTexts,
    screen: Rect,
    corner: Float,
    appear: Float,
    stamp: Float,
    rattle: Float,
) {
    val rise = (1f - appear) * screen.height * 0.12f
    val w = screen.width
    val h = screen.height
    val left = screen.left + rattle
    val top = screen.top + rise
    clipPath(Path().apply { addRoundRect(RoundRect(screen, CornerRadius(corner))) }) {
        drawRect(
            Brush.verticalGradient(listOf(BlockBgDark, BlockBgMid, BlockBgDark), startY = screen.top, endY = screen.bottom),
            screen.topLeft, screen.size, alpha = appear,
        )
        if (appear < 0.05f) return@clipPath

        // Título (abaixo da placa pendurada no topo da tela).
        val inner = w * 0.86f
        val title = textMeasurer.line(texts.title, 9.sp, Color.White.copy(alpha = appear), inner, FontWeight.Bold, maxLines = 2)
        drawText(title, topLeft = Offset(left + (w - inner) / 2f, top + h * 0.235f))

        // O recibo.
        val rLeft = left + w * 0.08f
        val rTop = top + h * 0.235f + title.size.height + h * 0.03f
        val rW = w * 0.84f
        val pad = rW * 0.06f
        val rInner = rW - pad * 2
        val header = textMeasurer.line(texts.header, 4.5.sp, ReceiptInk.copy(alpha = 0.55f), rInner, mono = true, letterSpacing = 0.4.sp)
        val app = textMeasurer.line(BLOCKED_APP, 8.sp, ReceiptInk, rInner, FontWeight.Bold, mono = true)
        val price = textMeasurer.line(texts.price, 11.sp, ReceiptInk, rInner, FontWeight.Bold, mono = true)
        val caption = textMeasurer.line(texts.caption, 4.sp, ReceiptInk.copy(alpha = 0.6f), rInner, mono = true, maxLines = 2)
        val gap = h * 0.022f
        val rH = pad + header.size.height + gap + app.size.height + gap * 2 + h * 0.06f + gap * 2 +
            price.size.height + caption.size.height + pad
        drawRoundRect(ReceiptPaper, Offset(rLeft, rTop), Size(rW, rH), CornerRadius(corner * 0.5f), alpha = appear)
        var y = rTop + pad
        drawText(header, topLeft = Offset(rLeft + pad, y))
        y += header.size.height + gap
        drawText(app, topLeft = Offset(rLeft + pad, y))
        val appBottom = y + app.size.height
        y = appBottom + gap
        val dash = PathEffect.dashPathEffect(floatArrayOf(3f, 3f))
        drawLine(ReceiptInk.copy(alpha = 0.35f), Offset(rLeft + pad, y), Offset(rLeft + rW - pad, y), strokeWidth = 1f, pathEffect = dash)
        // A mensagem do recibo, em traços (ilegível de propósito nesse tamanho).
        y += gap
        val msgH = h * 0.06f
        drawLine(ReceiptInk.copy(alpha = 0.3f), Offset(rLeft + pad * 1.5f, y + msgH * 0.25f), Offset(rLeft + rW - pad * 1.5f, y + msgH * 0.25f), strokeWidth = 2f, cap = StrokeCap.Round)
        drawLine(ReceiptInk.copy(alpha = 0.3f), Offset(rLeft + pad * 3f, y + msgH * 0.75f), Offset(rLeft + rW - pad * 3f, y + msgH * 0.75f), strokeWidth = 2f, cap = StrokeCap.Round)
        y += msgH + gap
        drawLine(ReceiptInk.copy(alpha = 0.35f), Offset(rLeft + pad, y), Offset(rLeft + rW - pad, y), strokeWidth = 1f, pathEffect = dash)
        y += gap
        drawText(price, topLeft = Offset(rLeft + pad, y))
        y += price.size.height
        drawText(caption, topLeft = Offset(rLeft + pad, y))

        // O carimbo BLOQUEADO: cai de 2,4× para 1× no canto do nome do app, como no app.
        if (stamp > 0f) {
            val stampText = textMeasurer.line(texts.stamp, 4.5.sp, StopRed, rInner * 0.6f, FontWeight.Black, mono = true)
            val sw = stampText.multiParagraph.getLineWidth(0) + 6f
            val sh = stampText.size.height + 2f
            val center = Offset(rLeft + rW - pad - sw / 2f + 5f, appBottom - app.size.height - sh * 0.45f)
            val s = 2.4f - 1.4f * smooth(stamp) - sin(stamp * PI.toFloat()) * 0.15f
            rotate(-10f, pivot = center) {
                scale(s, pivot = center) {
                    drawRoundRect(StopRed, Offset(center.x - sw / 2f, center.y - sh / 2f), Size(sw, sh), CornerRadius(2f), style = Stroke(1.2f), alpha = stamp)
                    drawText(stampText, topLeft = Offset(center.x - stampText.size.width / 2f, center.y - stampText.size.height / 2f), alpha = stamp)
                }
            }
        }

        // Botão de pagar o passe do dia.
        val bTop = rTop + rH + h * 0.05f
        val bH = h * 0.075f
        val bLeft = rLeft
        drawRoundRect(
            Brush.horizontalGradient(listOf(PayStart, PayEnd), startX = bLeft, endX = bLeft + rW),
            Offset(bLeft, bTop), Size(rW, bH), CornerRadius(bH / 2f), alpha = appear,
        )
        val pay = textMeasurer.line(texts.pay, 4.5.sp, BlockBgDark, rInner, FontWeight.Bold)
        drawText(pay, topLeft = Offset(bLeft + pad, bTop + (bH - pay.size.height) / 2f), alpha = appear)
    }
}

// ---------------------------------------------------------------------------------------
// O segurança
// ---------------------------------------------------------------------------------------

private fun rotateAround(p: Offset, pivot: Offset, degrees: Float): Offset {
    val r = degrees * PI.toFloat() / 180f
    val d = p - pivot
    return pivot + Offset(d.x * cos(r) - d.y * sin(r), d.x * sin(r) + d.y * cos(r))
}

private fun DrawScope.drawArm(shoulder: Offset, degrees: Float) {
    val hand = rotateAround(shoulder + Offset(0f, 32f), shoulder, degrees)
    // Manga um tom acima do paletó, com contorno, para o braço não sumir contra o terno.
    drawLine(SuitLine, shoulder, hand, strokeWidth = 10.6f, cap = StrokeCap.Round)
    drawLine(Sleeve, shoulder, hand, strokeWidth = 8.4f, cap = StrokeCap.Round)
    drawCircle(Shirt, 5.2f, hand)
    drawCircle(SuitLine, 5.2f, hand, style = Stroke(0.8f))
}

/**
 * O escudo do DollarBlock de segurança: óculos escuros, terno, gravata verde, ponto no
 * ouvido. [headDx] balança a cabeça ("não") quando o usuário tenta rolar com a tela trancada.
 */
private fun DrawScope.drawBouncer(cx: Float, g: Float, raise: Float, t: Float, headDx: Float) {
    drawOval(Color.Black.copy(alpha = 0.3f), Offset(cx - 24f, g - 4f), Size(48f, 7f))
    // Pernas e sapatos.
    drawRect(Color(0xFF14201B), Offset(cx - 11f, g - 32f), Size(9f, 28f))
    drawRect(Color(0xFF14201B), Offset(cx + 2f, g - 32f), Size(9f, 28f))
    drawOval(Shades, Offset(cx - 15f, g - 6f), Size(14f, 6f))
    drawOval(Shades, Offset(cx + 1f, g - 6f), Size(14f, 6f))

    val shoulderY = g - 76f
    // Braço de trás (lado da porta), parado.
    drawArm(Offset(cx - 20f, shoulderY + 3f), 10f)

    // Paletó.
    val jacket = Path().apply {
        moveTo(cx - 23f, shoulderY)
        lineTo(cx + 23f, shoulderY)
        lineTo(cx + 19f, g - 28f)
        lineTo(cx - 19f, g - 28f)
        close()
    }
    drawPath(jacket, SuitDark)
    drawPath(jacket, SuitLine, style = Stroke(1.2f))
    // Camisa, gravata, botões e o broche dourado.
    val shirt = Path().apply {
        moveTo(cx - 8f, shoulderY)
        lineTo(cx + 8f, shoulderY)
        lineTo(cx, shoulderY + 20f)
        close()
    }
    drawPath(shirt, Shirt)
    val tie = Path().apply {
        moveTo(cx, shoulderY + 2f)
        lineTo(cx + 2.8f, shoulderY + 8f)
        lineTo(cx, shoulderY + 24f)
        lineTo(cx - 2.8f, shoulderY + 8f)
        close()
    }
    drawPath(tie, Tie)
    drawLine(SuitLine, Offset(cx - 8f, shoulderY), Offset(cx - 2f, shoulderY + 24f), strokeWidth = 1.2f)
    drawLine(SuitLine, Offset(cx + 8f, shoulderY), Offset(cx + 2f, shoulderY + 24f), strokeWidth = 1.2f)
    drawCircle(Color(0xFF3A4A44), 1.5f, Offset(cx, g - 40f))
    drawCircle(Color(0xFF3A4A44), 1.5f, Offset(cx, g - 33f))
    drawCircle(Brass, 3.4f, Offset(cx - 13f, shoulderY + 11f))
    drawCircle(BrassDark, 3.4f, Offset(cx - 13f, shoulderY + 11f), style = Stroke(0.8f))

    // Braço da frente: levanta a mão de "pare" quando fecha.
    val frontShoulder = Offset(cx + 20f, shoulderY + 3f)
    val armAngle = -10f - 95f * raise
    drawArm(frontShoulder, armAngle)

    // Cabeça um pouco menor que o corpo pede, encolhida a partir do pescoço.
    val hx = cx + headDx
    scale(HEAD_SCALE, pivot = Offset(hx, shoulderY + 2f)) {
        drawShieldHead(Offset(hx, shoulderY - 52f), frown = raise, t = t)

        // Ponto no ouvido: o fio enrolado descendo da lateral do escudo até a gola.
        val top = shoulderY - 52f
        val wire = Path().apply {
            moveTo(hx + 23f, top + 24f)
            cubicTo(hx + 29f, top + 34f, hx + 20f, top + 42f, hx + 26f, top + 48f)
            cubicTo(hx + 29f, top + 52f, hx + 21f, shoulderY - 2f, hx + 16f, shoulderY + 1f)
        }
        drawPath(wire, Wire, style = Stroke(1.1f, cap = StrokeCap.Round))
        drawCircle(Wire, 1.8f, Offset(hx + 23f, top + 24f))
    }

    // A placa "DOOMSCROLL" com o X vermelho, erguida na mão de "pare".
    if (raise > 0.05f) {
        val hand = rotateAround(frontShoulder + Offset(0f, 32f), frontShoulder, armAngle)
        drawDoomscrollSign(hand, raise)
    }
}

private const val HEAD_SCALE = 0.84f
private const val DOOMSCROLL = "DOOMSCROLL"
// A placa de madeira (referência: placa de tábuas num poste).
private val WoodPlanks = listOf(Color(0xFFDDBE90), Color(0xFFD2B083), Color(0xFFD9B88A), Color(0xFFCBA676))
private val WoodGrain = Color(0xFF8A6238)
private val WoodEdge = Color(0xFF6E4E2C)
private val WoodPost = Color(0xFFC9A273)
private val SignInk = Color(0xFF3B2412)

/**
 * A placa do segurança: tábuas de madeira num poste, presa na mão, com "DOOMSCROLL" e um X
 * vermelho grande por cima. Sobe junto com o braço e aparece com [raise].
 */
private fun DrawScope.drawDoomscrollSign(hand: Offset, raise: Float) {
    val w = 66f
    val h = 32f
    val boardBottom = hand.y - 8f
    val left = hand.x - w / 2f
    val top = boardBottom - h
    val alpha = smooth((raise - 0.4f) / 0.6f)
    if (alpha <= 0f) return

    // Poste: atrás da placa, aparecendo um pouco acima dela, e descendo até a mão.
    drawRect(WoodPost, Offset(hand.x - 2.6f, top - 4f), Size(5.2f, hand.y + 3f - (top - 4f)), alpha = alpha)
    drawLine(WoodEdge, Offset(hand.x + 2.6f, top - 4f), Offset(hand.x + 2.6f, hand.y + 3f), strokeWidth = 0.8f, alpha = alpha * 0.6f)
    drawRect(WoodEdge, Offset(hand.x - 2.6f, top - 4f), Size(5.2f, 1f), alpha = alpha * 0.6f)
    // Luva por cima do poste.
    drawCircle(Shirt, 5.2f, hand, alpha = alpha)
    drawCircle(SuitLine, 5.2f, hand, style = Stroke(0.8f), alpha = alpha)

    // Sombra e as quatro tábuas, com pontas levemente irregulares.
    drawRect(Color.Black.copy(alpha = 0.22f * alpha), Offset(left + 1.5f, top + 1.8f), Size(w, h))
    val plank = h / WoodPlanks.size
    // Recuo da ponta direita e esquerda de cada tábua (madeira cortada à mão).
    val rightCut = listOf(0f, 1.6f, 0.6f, 1.2f)
    val leftCut = listOf(0.8f, 0f, 1.2f, 0.4f)
    WoodPlanks.forEachIndexed { i, tone ->
        val y = top + i * plank
        val board = Path().apply {
            moveTo(left + leftCut[i], y)
            lineTo(left + w - rightCut[i], y)
            lineTo(left + w - rightCut[i] * 0.4f, y + plank * 0.55f)
            lineTo(left + w - rightCut[i], y + plank)
            lineTo(left + leftCut[i], y + plank)
            close()
        }
        drawPath(board, tone, alpha = alpha)
        drawPath(board, WoodEdge, style = Stroke(0.7f), alpha = alpha)
        // Veios: dois traços finos ao longo da tábua.
        drawLine(WoodGrain, Offset(left + 4f + i * 3f, y + plank * 0.35f), Offset(left + w * 0.55f + i * 2f, y + plank * 0.38f), strokeWidth = 0.45f, alpha = alpha * 0.35f)
        drawLine(WoodGrain, Offset(left + w * 0.4f - i * 2f, y + plank * 0.68f), Offset(left + w - 5f, y + plank * 0.65f), strokeWidth = 0.45f, alpha = alpha * 0.3f)
    }
    // Uns nós na madeira.
    drawOval(WoodGrain, Offset(left + 7f, top + plank * 1.3f), Size(2.4f, 1.2f), alpha = alpha * 0.45f)
    drawOval(WoodGrain, Offset(left + w - 13f, top + plank * 3.4f), Size(2.2f, 1.1f), alpha = alpha * 0.45f)

    // "DOOMSCROLL" pichado (letras desenhadas à mão, com escorrido de tinta).
    drawGraffitiWord(Offset(left + 3.5f, top + h / 2f - 1f), width = w - 7f, alpha = alpha)

    // O X vermelho grande, de canto a canto, translúcido para a palavra continuar legível.
    val inset = 3.5f
    drawLine(StopRed, Offset(left + inset, top + inset), Offset(left + w - inset, top + h - inset), strokeWidth = 3.6f, cap = StrokeCap.Round, alpha = alpha * 0.5f)
    drawLine(StopRed, Offset(left + w - inset, top + inset), Offset(left + inset, top + h - inset), strokeWidth = 3.6f, cap = StrokeCap.Round, alpha = alpha * 0.5f)
}

// A cabeça: o formato em camadas do personagem da entrada (onb_hero), nas cores do escudo da marca.
private val HeadRim = Color(0xFF0B5E3C)
private val HeadBandLight = Color(0xFF2BF08C)
private val HeadBandDark = Color(0xFF00A86B)
private val HeadLine = Color(0xFF0B5E3C)
private val HeadFaceLight = Color(0xFF6CF7B0)
private val HeadFaceDark = Color(0xFF00C878)
private val HeadInk = Color(0xFF0A3A24)

/** Contorno do escudo: ponta no alto, ombros caindo, laterais retas e a ponta embaixo. */
private fun shieldPath(c: Offset, top: Float): Path = Path().apply {
    moveTo(c.x, top - 4f)
    lineTo(c.x + 23f, top + 5f)
    lineTo(c.x + 23f, top + 27f)
    quadraticTo(c.x + 22f, top + 45f, c.x, top + 56f)
    quadraticTo(c.x - 22f, top + 45f, c.x - 23f, top + 27f)
    lineTo(c.x - 23f, top + 5f)
    close()
}

/**
 * A cabeça do segurança, no molde do personagem da entrada: escudo em camadas (borda escura,
 * faixa, filete escuro e o rosto claro, nos verdes do escudo da marca), sobrancelhas grossas e
 * óculos wayfarer com reflexos diagonais. [frown] franze as sobrancelhas (0 =
 * relaxado, 1 = sério, quando a corda fecha).
 */
private fun DrawScope.drawShieldHead(topCenter: Offset, frown: Float, t: Float) {
    val top = topCenter.y
    val center = Offset(topCenter.x, top + 26f)
    val outline = shieldPath(topCenter, top)

    drawPath(outline, HeadRim)
    scale(0.9f, pivot = center) {
        drawPath(outline, Brush.linearGradient(listOf(HeadBandLight, HeadBandDark), start = Offset(center.x - 20f, top), end = Offset(center.x + 20f, top + 56f)))
    }
    scale(0.8f, pivot = center) { drawPath(outline, HeadLine) }
    scale(0.74f, pivot = center) {
        drawPath(outline, Brush.radialGradient(listOf(HeadFaceLight, HeadFaceDark), center = Offset(center.x - 3f, top + 18f), radius = 34f))
    }
    // Brilho na faixa, do lado de cima à esquerda.
    drawLine(Color.White.copy(alpha = 0.35f), Offset(center.x - 3f, top - 1f), Offset(center.x - 19f, top + 6f), strokeWidth = 1.6f, cap = StrokeCap.Round)

    // Sobrancelhas grossas: relaxadas abertas; franzidas (ponta de dentro baixa) quando fecha.
    val browY = top + 12f
    listOf(-1f, 1f).forEach { side ->
        val outer = Offset(center.x + side * 14f, browY + 1f)
        val inner = Offset(center.x + side * 4f, browY + 0.5f + 3f * frown)
        val control = Offset(center.x + side * 9f, browY - 3f + 2.5f * frown)
        val brow = Path().apply {
            moveTo(outer.x, outer.y)
            quadraticTo(control.x, control.y, inner.x, inner.y)
        }
        drawPath(brow, HeadInk, style = Stroke(2.6f, cap = StrokeCap.Round))
    }

    // Óculos wayfarer: barra de cima grossa e lentes trapezoidais com reflexos.
    val gy = top + 17f
    listOf(-1f, 1f).forEach { side ->
        val lens = Path().apply {
            moveTo(center.x + side * 1.5f, gy)
            lineTo(center.x + side * 19f, gy)
            lineTo(center.x + side * 17.5f, gy + 9f)
            quadraticTo(center.x + side * 10f, gy + 11.5f, center.x + side * 3f, gy + 8.5f)
            close()
        }
        drawPath(lens, Shades)
        drawPath(lens, HeadInk, style = Stroke(1.4f))
        val lx = center.x + side * 10.5f
        drawLine(Color.White.copy(alpha = 0.32f), Offset(lx - 3.5f, gy + 7.5f), Offset(lx - 0.5f, gy + 1.5f), strokeWidth = 1.3f, cap = StrokeCap.Round)
        drawLine(Color.White.copy(alpha = 0.22f), Offset(lx + 0.5f, gy + 7.5f), Offset(lx + 3f, gy + 2.5f), strokeWidth = 0.9f, cap = StrokeCap.Round)
        // Haste até a borda do escudo.
        drawLine(HeadInk, Offset(center.x + side * 19f, gy + 1f), Offset(center.x + side * 22f, gy + 1.5f), strokeWidth = 1.6f)
    }
    drawLine(HeadInk, Offset(center.x - 20f, gy + 0.4f), Offset(center.x + 20f, gy + 0.4f), strokeWidth = 2.4f, cap = StrokeCap.Round)
    // Um brilho que atravessa a lente esquerda de vez em quando.
    val glint = ((t % 3.3f) / 0.5f).coerceIn(0f, 1f)
    if (glint < 1f) {
        val gx = center.x - 17f + glint * 13f
        drawLine(Color.White.copy(alpha = 0.75f * (1f - glint)), Offset(gx, gy + 8f), Offset(gx + 4f, gy + 1.5f), strokeWidth = 1.5f, cap = StrokeCap.Round)
    }
}

// ---------------------------------------------------------------------------------------
// "DOOMSCROLL" pichado: letras desenhadas como pinceladas grossas, inclinadas, cada uma
// pulando um pouco, com um filete branco por dentro e tinta escorrendo (estilo grafite).
// ---------------------------------------------------------------------------------------

private const val GLYPH_H = 14f
private const val GLYPH_SLANT = 0.18f
private val GraffitiInk = Color(0xFF17100A)
private val GraffitiTilt = listOf(-5f, 4f, -3f, 6f, -4f, 3f, -6f, 4f, -2f, 5f)
private val GraffitiBounce = listOf(0f, -0.6f, 0.5f, -0.3f, 0.6f, -0.5f, 0.2f, -0.4f, 0.5f, 0f)

/** Ponto inclinado (itálico de grafite): quanto mais alto, mais à direita. */
private fun slanted(x: Float, y: Float) = Offset(x + (GLYPH_H - y) * GLYPH_SLANT, y)

/** Uma pincelada da letra, em coordenadas da letra (altura [GLYPH_H], base embaixo). */
private class BrushStroke(build: BrushStroke.() -> Unit) {
    val path = Path()

    init {
        build()
    }

    fun m(x: Float, y: Float) = slanted(x, y).let { path.moveTo(it.x, it.y) }
    fun l(x: Float, y: Float) = slanted(x, y).let { path.lineTo(it.x, it.y) }
    fun c(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) {
        val a = slanted(x1, y1)
        val b = slanted(x2, y2)
        val e = slanted(x3, y3)
        path.cubicTo(a.x, a.y, b.x, b.y, e.x, e.y)
    }
}

/** Uma gota escorrendo: começa em ([x], [y]) e desce [length]. */
private class Drip(val x: Float, val y: Float, val length: Float)

private class Glyph(val advance: Float, val strokes: List<BrushStroke>, val drips: List<Drip>)

private fun graffitiGlyph(ch: Char): Glyph = when (ch) {
    'D' -> Glyph(
        9.5f,
        listOf(
            BrushStroke { m(2f, 1.5f); c(1.6f, 6f, 2.4f, 10f, 2f, 13f) },
            BrushStroke { m(0.8f, 1.8f); c(11f, -0.5f, 11.5f, 14.5f, 1.2f, 13.2f) },
        ),
        listOf(Drip(2f, 13.4f, 3f)),
    )
    'O' -> Glyph(
        9f,
        listOf(BrushStroke { m(6f, 1f); c(0.5f, 0.5f, 0.5f, 13.5f, 4.5f, 13.3f); c(9f, 13.5f, 9.5f, 1f, 4f, 1.6f) }),
        listOf(Drip(5f, 13.5f, 2.2f)),
    )
    'M' -> Glyph(
        11f,
        listOf(BrushStroke { m(1f, 13f); c(1f, 9f, 1.2f, 4f, 1.8f, 1f); l(5.2f, 9f); l(8.6f, 1f); c(9.2f, 5f, 9.4f, 9f, 9.6f, 13f) }),
        listOf(Drip(1f, 13.3f, 3.5f), Drip(9.6f, 13.3f, 1.8f)),
    )
    'S' -> Glyph(
        8.5f,
        listOf(BrushStroke { m(8.5f, 2.6f); c(6f, 0f, 1f, 0.8f, 1.4f, 4.4f); c(1.8f, 7.6f, 8.8f, 6.4f, 8.4f, 10.4f); c(8f, 14f, 2f, 13.8f, 0.8f, 11f) }),
        listOf(Drip(4.4f, 13.4f, 2.6f)),
    )
    'C' -> Glyph(
        8.5f,
        listOf(BrushStroke { m(8.6f, 3f); c(6.4f, 0f, 1f, 1.6f, 1f, 7f); c(1f, 12.4f, 6f, 14.2f, 9f, 11.2f) }),
        listOf(Drip(3.6f, 13.2f, 3.2f)),
    )
    'R' -> Glyph(
        9.5f,
        listOf(
            BrushStroke { m(2f, 13.2f); c(1.8f, 9f, 2.2f, 5f, 2f, 1.4f) },
            BrushStroke { m(1f, 1.6f); c(10.5f, 0f, 10.5f, 8f, 2.6f, 7.2f) },
            BrushStroke { m(4f, 7.4f); c(6f, 9f, 7.5f, 12f, 9.4f, 13.4f) },
        ),
        listOf(Drip(2f, 13.5f, 2.4f), Drip(9.4f, 13.6f, 3.4f)),
    )
    'L' -> Glyph(
        8f,
        listOf(BrushStroke { m(2.6f, 0.6f); c(2.2f, 5f, 1.6f, 10f, 2f, 12.6f); c(4.5f, 11.8f, 7f, 12.4f, 9f, 13.4f) }),
        listOf(Drip(6f, 12.9f, 2.8f)),
    )
    else -> Glyph(6f, emptyList(), emptyList())
}

/**
 * Escreve [DOOMSCROLL] pichado, ocupando [width] a partir de [start] (x da esquerda, y do
 * meio da palavra).
 */
private fun DrawScope.drawGraffitiWord(start: Offset, width: Float, alpha: Float) {
    val glyphs = DOOMSCROLL.map(::graffitiGlyph)
    val total = glyphs.sumOf { it.advance.toDouble() }.toFloat() + GLYPH_H * GLYPH_SLANT
    val s = width / total
    val brush = Stroke(width = 3.7f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val shine = Stroke(width = 0.6f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    withTransform({
        translate(start.x, start.y - GLYPH_H * s / 2f)
        scale(s, s, pivot = Offset.Zero)
    }) {
        var x = 0f
        glyphs.forEachIndexed { i, glyph ->
            withTransform({
                translate(x, GraffitiBounce[i % GraffitiBounce.size])
                rotate(GraffitiTilt[i % GraffitiTilt.size], pivot = Offset(glyph.advance / 2f, GLYPH_H / 2f))
            }) {
                glyph.drips.forEach { drip ->
                    val from = slanted(drip.x, drip.y)
                    val to = from + Offset(0f, drip.length)
                    drawLine(GraffitiInk, from, to, strokeWidth = 1.4f, cap = StrokeCap.Round, alpha = alpha)
                    drawCircle(GraffitiInk, 1.05f, to, alpha = alpha)
                }
                glyph.strokes.forEach { drawPath(it.path, GraffitiInk, style = brush, alpha = alpha) }
                // O filete branco por dentro da pincelada, como no grafite.
                translate(-0.45f, -0.45f) {
                    glyph.strokes.forEach { drawPath(it.path, Color.White, style = shine, alpha = alpha * 0.55f) }
                }
            }
            x += glyph.advance
        }
    }
}
