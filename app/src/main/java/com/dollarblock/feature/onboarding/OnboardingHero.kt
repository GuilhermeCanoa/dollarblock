package com.dollarblock.feature.onboarding

import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.dollarblock.R
import kotlin.math.cos
import kotlin.math.sin

/**
 * Proporção da arte `onb_hero` (941×742): o recorte do mockup + 130 px de teto esticado em
 * cima, com a luminária aumentada 1.2x e subida a janela com serra e pinheiros no lugar dos
 * prédios e a lixeira reduzida (85% x 80%) e deslocada 30 px
 * para a esquerda, com os ícones no tamanho original (gerada por script a partir do mockup). Coordenadas "px" abaixo são dessa arte.
 */
private const val ART_W = 941f
internal const val HERO_ASPECT = ART_W / 742f

/** Altura da sola do tênis do mascote, em fração da altura da arte — o texto se alinha por ela. */
internal const val HERO_FOOT_Y = 708f / 742f

// ── Ventilador ──────────────────────────────────────────────────────────────────────────
/** Uma volta completa das pás — ventilador preguiçoso de sala, não turbina. */
private const val FAN_TURN_MS = 1_400

private const val FAN_SCALE = 1.5f       // tamanho relativo ao desenho original
private const val FAN_X = 0.50f          // centro horizontal (acima da cabeça do escudo)
private const val FAN_HUB_Y = 0.11f      // altura do motor (fração da altura da arte)
private const val FAN_RADIUS = 0.15f * FAN_SCALE // comprimento da pá (fração da largura)
private const val FAN_TILT = 0.22f       // achatamento vertical: visto de baixo, em perspectiva
private const val FAN_BLADES = 4

private val BladeFill = Color(0xFF0E3524)
private val BladeEdge = Color(0xFF3F7F45)
private val HousingFill = Color(0xFF103B29)
private val HousingLight = Color(0xFF5E9F4E)
private val RodColor = Color(0xFF0B2A1D)

// ── Brilho nos ícones da lixeira ────────────────────────────────────────────────────────
/** Ciclo do brilho: varre os três ícones na primeira fração e descansa no resto. */
private const val SHINE_CYCLE_MS = 5_200
private const val SHINE_SWEEP = 0.5f
private const val SHINE_FROM_X = -60f    // px da arte: começa antes do Instagram…
private const val SHINE_TO_X = 300f      // …e termina depois do Facebook
private const val SHINE_COLOR_HALF = 60f // meia-largura da faixa que revela a cor
private const val SHINE_GLOSS_HALF = 22f // meia-largura do reflexo branco no meio dela
private const val TRASH_RIM_Y = 527f     // borda da lixeira: nada de colorir o balde

/** Um ícone monocromático da arte: centro, lado, giro (graus) e a cor real da marca. */
private class SocialTile(val cx: Float, val cy: Float, val side: Float, val rotation: Float, val brand: List<Color>)

// Posições já com a lixeira deslocada 30 px para a esquerda na arte.
// Ordem = de trás pra frente (o TikTok fica atrás do Instagram e do Facebook).
private val SocialTiles = listOf(
    SocialTile(142f, 484f, 72f, 10f, listOf(Color(0xFF25F4EE), Color(0xFFFE2C55))),
    SocialTile(
        58f, 492f, 80f, -9f,
        listOf(Color(0xFFFEDA75), Color(0xFFFA7E1E), Color(0xFFD62976), Color(0xFF962FBF), Color(0xFF4F5BD5)),
    ),
    SocialTile(206f, 505f, 74f, 20f, listOf(Color(0xFF1877F2), Color(0xFF1877F2))),
)

// ── Cone de luz da luminária ────────────────────────────────────────────────────────────
private const val LAMP_RIM_Y = 228f      // aro iluminado da cúpula
private const val LAMP_RIM_L = 17f
private const val LAMP_RIM_R = 165f
private const val LAMP_CONE_BOTTOM = 620f
private const val LAMP_CONE_SPREAD = 130f // quanto cada lado abre até lá embaixo
private val LampLight = Color(0xFFE8EE8C)

// ── Vapor do café ───────────────────────────────────────────────────────────────────────
private const val STEAM_CYCLE_MS = 2_800
private const val STEAM_BASE_Y = 340f    // boca da caneca
private const val STEAM_RISE = 26f       // quanto cada fio sobe no ciclo
private const val STEAM_LENGTH = 34f
private val SteamXs = listOf(553f, 567f, 581f)
private val SteamColor = Color(0xFFF1F5E4)

/** Opaco no miolo, transparente nas pontas: a arte vira fundo, não foto. */
private val HeroFadeMask = Brush.verticalGradient(
    0f to Color.Transparent,
    0.05f to Color.Black,
    0.72f to Color.Black,
    1f to Color.Transparent,
)

/**
 * Fundo animado da 1ª página: o escudo de óculos escuros no sofá. Por cima da arte estática,
 * em Canvas: o cone de luz da luminária, o vapor do café, um ventilador de teto girando e
 * um brilho que passa pelos ícones na lixeira revelando a cor original deles. Ocupa a
 * largura toda e esmaece em cima e embaixo para se fundir com a página.
 */
@Composable
fun OnboardingHero(modifier: Modifier = Modifier) {
    val animate = rememberAnimationsEnabled()
    val transition = rememberInfiniteTransition(label = "hero")
    val turn = if (animate) {
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(FAN_TURN_MS, easing = LinearEasing), RepeatMode.Restart),
            label = "fanTurn",
        ).value
    } else {
        20f
    }
    val shine = if (animate) {
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(SHINE_CYCLE_MS, easing = LinearEasing), RepeatMode.Restart),
            label = "shine",
        ).value
    } else {
        1f // parado: sem brilho
    }
    val steam = if (animate) {
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(STEAM_CYCLE_MS, easing = LinearEasing), RepeatMode.Restart),
            label = "steam",
        ).value
    } else {
        0.3f
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(HERO_ASPECT)
            // Máscara de alfa: o topo e a base se dissolvem no fundo da página, seja qual for.
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(brush = HeroFadeMask, blendMode = BlendMode.DstIn)
            },
    ) {
        Image(
            painter = painterResource(R.drawable.onb_hero),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Canvas(modifier = Modifier.fillMaxSize()) {
            val px = size.width / ART_W
            drawLampCone(px)
            drawCoffeeSteam(px, steam)
            if (shine < SHINE_SWEEP) {
                val t = shine / SHINE_SWEEP
                drawSocialShine(px, bandX = SHINE_FROM_X + (SHINE_TO_X - SHINE_FROM_X) * t)
            }
            drawCeilingFan(angleDeg = turn, motionTrail = animate)
        }
    }
}

// ---------------------------------------------------------------------------------------
// Luz da luminária e vapor do café
// ---------------------------------------------------------------------------------------

/**
 * Cone de luz saindo do aro da cúpula e abrindo para baixo. Camadas cada vez mais abertas e
 * mais fracas fazem a borda macia; cada uma some em degradê até [LAMP_CONE_BOTTOM].
 */
private fun DrawScope.drawLampCone(px: Float) {
    val layers = 4
    for (k in 0 until layers) {
        val spread = LAMP_CONE_SPREAD * (0.4f + 0.6f * k / (layers - 1))
        val inset = 8f * (layers - 1 - k) / (layers - 1) // as camadas de dentro saem mais do miolo do aro
        val cone = Path().apply {
            moveTo((LAMP_RIM_L + inset) * px, LAMP_RIM_Y * px)
            lineTo((LAMP_RIM_R - inset) * px, LAMP_RIM_Y * px)
            lineTo((LAMP_RIM_R + spread) * px, LAMP_CONE_BOTTOM * px)
            lineTo((LAMP_RIM_L - spread) * px, LAMP_CONE_BOTTOM * px)
            close()
        }
        drawPath(
            path = cone,
            brush = Brush.verticalGradient(
                0f to LampLight.copy(alpha = 0.075f),
                0.55f to LampLight.copy(alpha = 0.03f),
                1f to Color.Transparent,
                startY = LAMP_RIM_Y * px,
                endY = LAMP_CONE_BOTTOM * px,
            ),
        )
    }
}

/**
 * Três fiozinhos de vapor saindo da caneca: cada um sobe ondulando, aparece e some
 * (defasados entre si para nunca sumirem todos juntos).
 */
private fun DrawScope.drawCoffeeSteam(px: Float, t: Float) {
    val segments = 14
    SteamXs.forEachIndexed { i, cx ->
        val phase = (t + i / SteamXs.size.toFloat()) % 1f
        val alpha = 0.5f * sin(Math.PI * phase).toFloat()
        if (alpha <= 0.01f) return@forEachIndexed
        val bottom = STEAM_BASE_Y - phase * STEAM_RISE
        val top = bottom - STEAM_LENGTH
        val wave = 2 * Math.PI * t + i * 2.1
        val path = Path().apply {
            for (j in 0..segments) {
                val f = j / segments.toFloat()           // 0 = embaixo, 1 = em cima
                val y = bottom - f * STEAM_LENGTH
                val x = cx + sin(f * 5.5 + wave).toFloat() * (1.5f + 3.5f * f) // abre ao subir
                if (j == 0) moveTo(x * px, y * px) else lineTo(x * px, y * px)
            }
        }
        drawPath(
            path = path,
            brush = Brush.verticalGradient(
                0f to Color.Transparent,
                0.45f to SteamColor.copy(alpha = alpha),
                1f to SteamColor.copy(alpha = alpha * 0.4f),
                startY = top * px,
                endY = bottom * px,
            ),
            style = Stroke(width = 4.5f * px, cap = StrokeCap.Round),
        )
    }
}

// ---------------------------------------------------------------------------------------
// Brilho nos ícones
// ---------------------------------------------------------------------------------------

/**
 * Faixa diagonal em [bandX] (px da arte). Onde ela passa, o ícone ganha a cor da marca
 * (blend `Color`: matiz da marca, luminosidade do desenho) e um reflexo branco no meio.
 * `BlendMode.Color` só existe a partir do Android 10; antes disso fica só o reflexo.
 */
private fun DrawScope.drawSocialShine(px: Float, bandX: Float) {
    val clip = Rect(0f, 0f, size.width, TRASH_RIM_Y * px)

    fun band(half: Float, peak: Color) = Brush.linearGradient(
        colors = listOf(Color.Transparent, peak, Color.Transparent),
        start = Offset((bandX - half) * px, clip.bottom),
        end = Offset((bandX + half) * px, clip.bottom - half * 0.8f * px),
    )

    // Desenha os ícones (com o pincel dado) numa camada, recorta pela faixa e compõe com [blend].
    fun tilesLayer(blend: BlendMode, mask: Brush, fill: (SocialTile) -> Brush) = drawIntoCanvas { canvas ->
        canvas.saveLayer(clip, Paint().apply { blendMode = blend })
        clipRect(bottom = clip.bottom) {
            SocialTiles.forEach { tile ->
                val c = Offset(tile.cx * px, tile.cy * px)
                val half = tile.side / 2f * px
                rotate(tile.rotation, pivot = c) {
                    drawRoundRect(
                        brush = fill(tile),
                        topLeft = Offset(c.x - half, c.y - half),
                        size = Size(half * 2, half * 2),
                        cornerRadius = CornerRadius(half * 0.42f),
                    )
                }
            }
            drawRect(brush = mask, blendMode = BlendMode.DstIn)
        }
        canvas.restore()
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        tilesLayer(BlendMode.Color, band(SHINE_COLOR_HALF, Color.Black)) { tile ->
            val c = Offset(tile.cx * px, tile.cy * px)
            val half = tile.side / 2f * px
            // Diagonal de baixo-esquerda para cima-direita, como o gradiente do Instagram.
            Brush.linearGradient(tile.brand, start = Offset(c.x - half, c.y + half), end = Offset(c.x + half, c.y - half))
        }
    }
    tilesLayer(BlendMode.SrcOver, band(SHINE_GLOSS_HALF, Color.White.copy(alpha = 0.38f))) {
        SolidColor(Color.White)
    }
}

// ---------------------------------------------------------------------------------------
// Ventilador
// ---------------------------------------------------------------------------------------

private fun DrawScope.drawCeilingFan(angleDeg: Float, motionTrail: Boolean) {
    val w = size.width
    val hub = Offset(w * FAN_X, size.height * FAN_HUB_Y)
    val radius = w * FAN_RADIUS
    val housing = Size(w * 0.05f * FAN_SCALE, w * 0.022f * FAN_SCALE)

    // Haste do teto até o motor.
    drawRect(
        color = RodColor,
        topLeft = Offset(hub.x - w * 0.004f * FAN_SCALE, 0f),
        size = Size(w * 0.008f * FAN_SCALE, hub.y),
    )

    // Pás atrás do motor (metade "de cima" da elipse) primeiro, as da frente por último.
    val angles = (0 until FAN_BLADES).map { angleDeg + it * 360f / FAN_BLADES }
    val (back, front) = angles.partition { sin(Math.toRadians(it.toDouble())) < 0 }

    fun blades(list: List<Float>) = list.forEach { a ->
        if (motionTrail) {
            drawBlade(hub, radius, a - 14f, alpha = 0.18f)
            drawBlade(hub, radius, a - 7f, alpha = 0.35f)
        }
        drawBlade(hub, radius, a, alpha = 1f)
    }

    blades(back)
    // Motor: corpo + reflexo da luminária.
    drawOval(
        color = HousingFill,
        topLeft = Offset(hub.x - housing.width / 2, hub.y - housing.height / 2),
        size = housing,
    )
    drawOval(
        color = HousingLight,
        topLeft = Offset(hub.x - housing.width / 2, hub.y - housing.height / 2),
        size = housing,
        style = Stroke(width = w * 0.002f * FAN_SCALE),
        alpha = 0.7f,
    )
    blades(front)
}

/** Uma pá em perspectiva: estreita no motor, larga na ponta, achatada por [FAN_TILT]. */
private fun DrawScope.drawBlade(hub: Offset, radius: Float, angleDeg: Float, alpha: Float) {
    val a = Math.toRadians(angleDeg.toDouble())
    // Eixo da pá e eixo perpendicular, ambos no plano horizontal, projetados na tela.
    fun project(along: Float, across: Float): Offset {
        val x = along * cos(a) - across * sin(a)
        val y = along * sin(a) + across * cos(a)
        return Offset(hub.x + x.toFloat(), hub.y + y.toFloat() * FAN_TILT)
    }

    val root = radius * 0.12f
    val rootHalf = radius * 0.05f
    val tipHalf = radius * 0.13f
    val path = Path().apply {
        project(root, -rootHalf).let { moveTo(it.x, it.y) }
        project(radius * 0.9f, -tipHalf).let { lineTo(it.x, it.y) }
        val tip = project(radius, 0f)
        val c1 = project(radius * 1.02f, -tipHalf)
        quadraticTo(c1.x, c1.y, tip.x, tip.y)
        val c2 = project(radius * 1.02f, tipHalf)
        val back = project(radius * 0.9f, tipHalf)
        quadraticTo(c2.x, c2.y, back.x, back.y)
        project(root, rootHalf).let { lineTo(it.x, it.y) }
        close()
    }
    drawPath(path, BladeFill, alpha = alpha)
    drawPath(path, BladeEdge, alpha = alpha * 0.8f, style = Stroke(width = size.width * 0.0018f * FAN_SCALE))
}
