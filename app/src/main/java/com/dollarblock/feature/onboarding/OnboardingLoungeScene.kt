package com.dollarblock.feature.onboarding

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.sp
import com.dollarblock.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// Unidade da cena: px da arte `onb_lounge_fg` (560×460, primeiro plano recortado da
// ilustração docs/art/onb_lounge_source.webp por scripts/build-onb-lounge.ps1: guarda-sol
// com o letreiro "Dollar Block", personagem de braços atrás da nuca, cadeira, cooler e as
// sombras deles). A cena tem sempre ART_H de altura; a largura acompanha a tela. O fundo de
// praia é desenhado aqui.
private const val ART_H = 460f
private const val FG_W = 560
private const val FG_H = 460

/**
 * Superfície de cima do guarda-sol (onde os ícones batem), da ponta esquerda à direita —
 * o contorno que o build-onb-lounge.ps1 imprime, sem a ponta do mastro.
 */
private val CanopyTop = listOf(
    Offset(22f, 172f),
    Offset(30f, 160f),
    Offset(50f, 134f),
    Offset(70f, 110f),
    Offset(90f, 85f),
    Offset(110f, 64f),
    Offset(130f, 48f),
    Offset(150f, 36f),
    Offset(190f, 21f),
    Offset(230f, 12f),
    Offset(270f, 11f),
    Offset(310f, 13f),
    Offset(350f, 15f),
    Offset(390f, 21f),
    Offset(430f, 33f),
    Offset(470f, 47f),
    Offset(480f, 60f),
)

// Praia de dia (tudo em verdes, como a arte da 1ª página).
private const val HORIZON = 300f
private val SkyTop = Color(0xFF0C3022)
private val SkyMid = Color(0xFF2C7653)
private val SkyHorizon = Color(0xFF8CCB98)
private val SunColor = Color(0xFFF2FFC4)
private val SeaFar = Color(0xFF2A7A5A)
private val SeaNear = Color(0xFF3A9572)
private val Foam = Color(0xFFA6EBC0)
private val IslandDark = Color(0xFF0F3B2A)
private val IslandRim = Color(0xFF9FDB9A)
private val IslandLit = Color(0xFF3F8F5E)
private val IslandSand = Color(0xFFB9DFA6)
private val PalmTrunk = Color(0xFF0B2E20)
private val PalmLeaf = Color(0xFF1F6B45)
private val PalmLeafLit = Color(0xFF3A9A62)
private val Coconut = Color(0xFF2F3A1C)
private val SandTop = Color(0xFF66AF79)
private val SandBottom = Color(0xFF3B7C55)
private val CloudColor = Color(0xFFD4F2DC)

// Chuva de ícones.
private const val ICON_COUNT = 9
private const val ICON_LIFE_S = 2.7f      // de nascer no gráfico a sumir depois do quique
private const val ICON_SIZE = 40f
private const val GRAVITY = 520f          // px da arte / s²
private const val SPAWN_TOP = -30f        // sem o gráfico medido ainda: nascem logo acima da cena
private const val SCENE_LOOP_MS = 27_000  // múltiplo da vida dos ícones: o loop não "pula"

/** Quanto a cena sangra para os lados, até a borda da tela (a margem lateral das páginas). */
private val SCENE_BLEED: Dp = 24.dp

/** Quanto o céu sobe acima da arte, por trás do conteúdo de cima, clareando aos poucos. */
private val SCENE_TOP_GLOW: Dp = 72.dp

private enum class SocialIcon { INSTAGRAM, TIKTOK, FACEBOOK, YOUTUBE, X, SNAPCHAT, DISCORD }

/** De onde a chuva de ícones sai — o gráfico de rosca —, em coordenadas da raiz. */
class RainSource(val center: Offset, val radius: Float)

/**
 * Cena de descanso na praia, em tons de verde: o personagem na cadeira colorida, sob o
 * guarda-sol listrado, o cooler ao lado, o mar com uma ilha de coqueiros à frente. Ícones de
 * redes sociais saem do gráfico de rosca ([rainSource]), caem, batem no guarda-sol e quicam
 * para trás dele. A cena vai até a borda da tela e esmaece no fundo da página. Sem animações
 * do sistema, fica parada (sem a chuva).
 */
@Composable
fun LoungeScene(
    rainSource: RainSource?,
    modifier: Modifier = Modifier,
    bottomExtension: Dp = 0.dp,
    topExtension: Dp = SCENE_TOP_GLOW,
) {
    val foreground = ImageBitmap.imageResource(R.drawable.onb_lounge_fg)
    val animate = rememberAnimationsEnabled()
    val seconds = if (animate) {
        rememberInfiniteTransition(label = "lounge").animateFloat(
            initialValue = 0f,
            targetValue = SCENE_LOOP_MS / 1000f,
            animationSpec = infiniteRepeatable(tween(SCENE_LOOP_MS, easing = LinearEasing), RepeatMode.Restart),
            label = "loungeTime",
        ).value
    } else {
        null
    }
    // Altura total desenhada (com o céu que sobe por trás do conteúdo de cima), para a
    // camada da chuva ter exatamente a mesma área da praia.
    var fullHeightPx by remember { mutableIntStateOf(0) }

    // Geometria comum às duas camadas: a arte fica entre o céu extra de cima e a areia extra
    // de baixo (que passa por baixo do botão).
    fun DrawScope.artTop() = topExtension.toPx()
    fun DrawScope.artHeight() = size.height - topExtension.toPx() - bottomExtension.toPx()
    fun DrawScope.artScale() = artHeight() / ART_H

    // Camada de baixo (zIndex -1): o céu sobe por trás do texto acima da cena sem cobri-lo.
    Box(
        modifier = modifier
            .zIndex(-1f)
            // Sangra até a borda da tela e sobe [topExtension] por trás do conteúdo de cima.
            .layout { measurable, constraints ->
                val extra = SCENE_BLEED.roundToPx()
                val top = topExtension.roundToPx()
                val width = constraints.maxWidth + 2 * extra
                val height = constraints.maxHeight + top
                val placeable = measurable.measure(Constraints.fixed(width, height))
                fullHeightPx = height
                layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(-extra, -top) }
            },
    ) {
        // Praia: o céu clareia aos poucos desde bem acima da arte (sem degrau de tom com o
        // fundo da página); embaixo e à direita, dissolve no fundo.
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    val e = artTop() / size.height
                    val a = artHeight() / size.height
                    drawRect(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            e * 0.35f to Color.Black.copy(alpha = 0.08f),
                            e * 0.7f to Color.Black.copy(alpha = 0.3f),
                            e to Color.Black.copy(alpha = 0.62f),
                            e + 0.1f * a to Color.Black.copy(alpha = 0.9f),
                            e + 0.2f * a to Color.Black,
                            e + 0.86f * a to Color.Black,
                            1f to Color.Transparent,
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                    drawRect(
                        Brush.horizontalGradient(0.6f to Color.Black, 1f to Color.Transparent),
                        blendMode = BlendMode.DstIn,
                    )
                },
        ) {
            val scale = artScale()
            val topPx = artTop()
            val worldW = size.width / scale
            val worldTop = -artTop() / scale
            val worldH = (artHeight() + bottomExtension.toPx()) / scale
            withTransform({
                translate(0f, topPx)
                scale(scale, scale, pivot = Offset.Zero)
            }) { drawBeach(worldW, worldTop, worldH, seconds ?: 0f) }
        }
        // Primeiro plano (guarda-sol, personagem, cooler): só a base esmaece, para o topo do
        // guarda-sol não sumir junto com o céu.
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    val top = artTop()
                    val art = artHeight()
                    val bottom = size.height - top - art
                    drawRect(
                        Brush.verticalGradient(
                            (top + 0.82f * art) / size.height to Color.Black,
                            (top + art + 0.35f * bottom) / size.height to Color.Transparent,
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                },
        ) {
            val scale = artScale()
            val topPx = artTop()
            withTransform({
                translate(0f, topPx)
                scale(scale, scale, pivot = Offset.Zero)
            }) {
                drawImage(foreground, dstOffset = IntOffset.Zero, dstSize = IntSize(FG_W, FG_H))
            }
        }
    }

    // Camada de cima: a chuva, por cima do gráfico de onde sai. Ocupa altura zero no layout
    // e se desenha sobre a mesma área da praia.
    if (seconds != null) {
        var topLeftInRoot by remember { mutableStateOf(Offset.Zero) }
        Canvas(
            modifier = Modifier
                .layout { measurable, constraints ->
                    val extra = SCENE_BLEED.roundToPx()
                    val width = constraints.maxWidth + 2 * extra
                    val placeable = measurable.measure(Constraints.fixed(width, fullHeightPx))
                    layout(constraints.maxWidth, 0) { placeable.place(-extra, -fullHeightPx) }
                }
                .onGloballyPositioned { topLeftInRoot = it.positionInRoot() },
        ) {
            val scale = artScale()
            val topPx = artTop()
            val origin = topLeftInRoot + Offset(0f, artTop())
            val source = rainSource?.let {
                RainSourceArt(center = (it.center - origin) / scale, radius = it.radius / scale)
            }
            withTransform({
                translate(0f, topPx)
                scale(scale, scale, pivot = Offset.Zero)
            }) { drawSocialRain(seconds, source) }
        }
    }
}

/** [RainSource] já em coordenadas da arte. */
private class RainSourceArt(val center: Offset, val radius: Float)

// ---------------------------------------------------------------------------------------
// Praia
// ---------------------------------------------------------------------------------------

/** Céu de dia, sol alto, nuvens, gaivotas, mar com brilho, ilha com coqueiros e areia — em verdes. */
private fun DrawScope.drawBeach(w: Float, top: Float, h: Float, t: Float) {
    // Céu: claro perto do horizonte (dia limpo), escurecendo para cima desde [top] (acima da
    // arte, por trás do conteúdo de cima) até o tom do fundo da página.
    fun at(y: Float) = ((y - top) / (HORIZON - top)).coerceIn(0f, 1f)
    drawRect(
        Brush.verticalGradient(
            0f to SkyTop,
            at(20f) to lerp(SkyTop, SkyMid, 0.35f),
            at(140f) to SkyMid,
            1f to SkyHorizon,
            startY = top,
            endY = HORIZON,
        ),
        topLeft = Offset(0f, top),
        size = Size(w, HORIZON - top),
    )
    // Sol alto, com raios girando devagar e halo que respira.
    val sun = Offset(w * 0.8f, 96f)
    val pulse = 1f + 0.05f * sin(t * 1.3f)
    drawCircle(Brush.radialGradient(listOf(SunColor.copy(alpha = 0.5f), Color.Transparent), sun, 130f * pulse), 130f * pulse, sun)
    rotate(t * 6f, pivot = sun) {
        for (k in 0 until 12) {
            val ang = k * 30f * PI.toFloat() / 180f
            val long = if (k % 2 == 0) 62f else 52f
            drawLine(
                SunColor.copy(alpha = 0.4f),
                Offset(sun.x + cos(ang) * 40f, sun.y + sin(ang) * 40f),
                Offset(sun.x + cos(ang) * long, sun.y + sin(ang) * long),
                strokeWidth = 3.5f,
                cap = StrokeCap.Round,
            )
        }
    }
    drawCircle(SunColor, 28f, sun)
    drawCircle(Color.White.copy(alpha = 0.55f), 18f, Offset(sun.x - 5f, sun.y - 5f))
    // Nuvens fofas passando devagar.
    for ((i, fy) in listOf(70f, 150f, 40f).withIndex()) {
        val span = w + 300f
        val cx = ((t * (8f + i * 4f) + i * span / 3f) % span) - 150f
        drawCloud(Offset(cx, fy), 1f - i * 0.2f)
    }
    // Gaivotas.
    for (i in 0 until 2) {
        val gx = ((t * 22f + i * 70f) % (w + 200f)) - 100f + w * 0.3f
        val gy = 110f + i * 18f + sin(t * 2f + i) * 4f
        val flap = 5f + 3f * sin(t * 7f + i * 2f)
        drawPath(
            Path().apply {
                moveTo(gx - 9f, gy - flap * 0.4f)
                quadraticTo(gx - 4f, gy - flap, gx, gy)
                quadraticTo(gx + 4f, gy - flap, gx + 9f, gy - flap * 0.4f)
            },
            PalmTrunk,
            style = Stroke(2.2f, cap = StrokeCap.Round),
        )
    }

    // Mar.
    drawRect(
        Brush.verticalGradient(0f to SeaFar, 1f to SeaNear, startY = HORIZON, endY = 400f),
        topLeft = Offset(0f, HORIZON),
        size = Size(w, 400f - HORIZON),
    )
    // Brilho do sol na água: faíscas que piscam embaixo do sol.
    for (k in 0 until 8) {
        val y = HORIZON + 10f + k * 11f
        val half = 18f + k * 2.5f + 6f * sin(t * 3f + k)
        drawLine(SunColor.copy(alpha = 0.45f - k * 0.04f), Offset(sun.x - half, y), Offset(sun.x + half, y), strokeWidth = 2.5f, cap = StrokeCap.Round)
    }
    // Ondinhas.
    for (k in 0 until 7) {
        val y = HORIZON + 20f + (k % 4) * 18f
        val x = ((t * 14f + k * 160f) % (w + 120f)) - 60f
        drawLine(Foam.copy(alpha = 0.35f), Offset(x, y), Offset(x + 34f, y), strokeWidth = 2f, cap = StrokeCap.Round)
    }

    // Ilhas: a grande com coqueiros e uma pequena ao fundo.
    drawIsland(Offset(w * 0.92f, HORIZON + 2f), 0.42f, t, palms = false)
    drawIsland(Offset(w * 0.6f, HORIZON + 6f), 1f, t, palms = true)

    // Areia com a espuma da beira, até o fim da cena (inclusive a extensão por baixo do botão).
    val sand = Path().apply {
        moveTo(0f, 392f)
        cubicTo(w * 0.3f, 378f, w * 0.6f, 404f, w, 386f)
        lineTo(w, h)
        lineTo(0f, h)
        close()
    }
    drawPath(sand, Brush.verticalGradient(0f to SandTop, 1f to SandBottom, startY = 378f, endY = maxOf(h, ART_H)))
    val swash = 3f * sin(t * 1.6f)
    drawPath(
        Path().apply {
            moveTo(0f, 392f + swash)
            cubicTo(w * 0.3f, 378f + swash, w * 0.6f, 404f + swash, w, 386f + swash)
        },
        Foam.copy(alpha = 0.7f),
        style = Stroke(3.5f, cap = StrokeCap.Round),
    )
}

private fun DrawScope.drawCloud(c: Offset, s: Float) {
    val color = CloudColor.copy(alpha = 0.55f)
    drawRoundRect(color, Offset(c.x - 46f * s, c.y - 6f * s), Size(92f * s, 18f * s), CornerRadius(9f * s))
    drawCircle(color, 16f * s, Offset(c.x - 14f * s, c.y - 6f * s))
    drawCircle(color, 20f * s, Offset(c.x + 6f * s, c.y - 10f * s))
    drawCircle(color, 12f * s, Offset(c.x + 26f * s, c.y - 2f * s))
}

/**
 * Ilha: reflexo na água, faixa de areia clara com espuma, morro de dois cumes iluminado
 * pelo sol (da direita) e, na grande, coqueiros de tronco afinando, com folhas cheias
 * caídas balançando e cocos.
 */
private fun DrawScope.drawIsland(base: Offset, s: Float, t: Float, palms: Boolean) {
    val half = 135f * s
    // Reflexo escuro na água.
    drawOval(IslandDark.copy(alpha = 0.35f), Offset(base.x - half * 0.85f, base.y + 1f * s), Size(half * 1.7f, 16f * s))
    // Faixa de areia.
    drawPath(
        Path().apply {
            moveTo(base.x - half - 10f * s, base.y + 2f * s)
            cubicTo(base.x - half * 0.4f, base.y - 16f * s, base.x + half * 0.4f, base.y - 16f * s, base.x + half + 10f * s, base.y + 2f * s)
            close()
        },
        IslandSand,
    )
    drawLine(Foam.copy(alpha = 0.7f), Offset(base.x - half - 8f * s, base.y + 2f * s), Offset(base.x + half + 8f * s, base.y + 2f * s), strokeWidth = 2.5f * s, cap = StrokeCap.Round)
    // Morro de dois cumes.
    val ridge = Path().apply {
        moveTo(base.x - half * 0.92f, base.y - 4f * s)
        cubicTo(base.x - half * 0.72f, base.y - 34f * s, base.x - half * 0.38f, base.y - 52f * s, base.x - half * 0.08f, base.y - 46f * s)
        cubicTo(base.x + half * 0.16f, base.y - 42f * s, base.x + half * 0.26f, base.y - 32f * s, base.x + half * 0.44f, base.y - 32f * s)
        cubicTo(base.x + half * 0.64f, base.y - 32f * s, base.x + half * 0.84f, base.y - 20f * s, base.x + half * 0.92f, base.y - 4f * s)
    }
    val hill = Path().apply {
        addPath(ridge)
        close()
    }
    drawPath(hill, Brush.verticalGradient(0f to IslandLit, 1f to IslandDark, startY = base.y - 52f * s, endY = base.y))
    // Luz do sol batendo na encosta da direita.
    drawPath(
        hill,
        Brush.horizontalGradient(0.45f to Color.Transparent, 1f to IslandRim.copy(alpha = 0.45f), startX = base.x - half, endX = base.x + half),
    )
    drawPath(ridge, IslandRim.copy(alpha = 0.55f), style = Stroke(2f * s, cap = StrokeCap.Round))
    if (!palms) return

    // Coqueiros: posição no morro, altura, inclinação.
    val palmsAt = listOf(Triple(-48f, 58f, -1f), Triple(-12f, 66f, 1f), Triple(40f, 48f, 1f))
    for ((i, palm) in palmsAt.withIndex()) {
        val (px, height, lean) = palm
        val groundY = when {
            px < -20f -> base.y - 40f * s
            px < 20f -> base.y - 44f * s
            else -> base.y - 30f * s
        }
        val root = Offset(base.x + px * s, groundY + 4f * s)
        val top = Offset(root.x + lean * 16f * s, root.y - height * s)
        val ctrl = Offset(root.x + lean * 2f * s, (root.y + top.y) / 2f)
        // Tronco afinando: três passadas cada vez mais finas, da base para o alto.
        for ((k, width) in listOf(6.5f, 5f, 3.5f).withIndex()) {
            val from = k / 3f
            val p0 = quadPoint(root, ctrl, top, from)
            val c = quadPoint(root, ctrl, top, (from + 1f) / 2f)
            drawPath(
                Path().apply {
                    moveTo(p0.x, p0.y)
                    quadraticTo(c.x, c.y, top.x, top.y)
                },
                PalmTrunk,
                style = Stroke(width * s, cap = StrokeCap.Round),
            )
        }
        // Folhas cheias, caídas, balançando de leve.
        val sway = sin(t * 1.4f + i) * 5f
        for (k in 0 until 7) {
            val ang = (-175f + k * 28f + sway) * PI.toFloat() / 180f
            val len = (34f - (k % 2) * 5f) * s
            val tip = Offset(top.x + cos(ang) * len, top.y + sin(ang) * len * 0.55f + 16f * s)
            val mid = Offset((top.x + tip.x) / 2f, (top.y + tip.y) / 2f - 9f * s)
            val dx = tip.x - top.x
            val dy = tip.y - top.y
            val norm = sqrt(dx * dx + dy * dy).coerceAtLeast(0.001f)
            val perp = Offset(-dy / norm, dx / norm) * (6f * s)
            drawPath(
                Path().apply {
                    moveTo(top.x, top.y)
                    quadraticTo(mid.x + perp.x, mid.y + perp.y, tip.x, tip.y)
                    quadraticTo(mid.x - perp.x * 0.3f, mid.y - perp.y * 0.3f, top.x, top.y)
                    close()
                },
                if (k % 2 == 0) PalmLeaf else PalmLeafLit,
            )
        }
        // Cocos.
        drawCircle(Coconut, 3.2f * s, Offset(top.x - 3f * s, top.y + 4f * s))
        drawCircle(Coconut, 3.2f * s, Offset(top.x + 3f * s, top.y + 5f * s))
    }
}

/** Ponto da curva quadrática (a, c, b) em [f]. */
private fun quadPoint(a: Offset, c: Offset, b: Offset, f: Float): Offset {
    val u = 1f - f
    return Offset(u * u * a.x + 2f * u * f * c.x + f * f * b.x, u * u * a.y + 2f * u * f * c.y + f * f * b.y)
}

// ---------------------------------------------------------------------------------------
// Chuva de ícones
// ---------------------------------------------------------------------------------------

/** Altura da superfície do guarda-sol em [x], ou null fora dele. */
private fun canopyY(x: Float): Float? {
    for (i in 0 until CanopyTop.size - 1) {
        val a = CanopyTop[i]
        val b = CanopyTop[i + 1]
        if (x in a.x..b.x) return a.y + (b.y - a.y) * (x - a.x) / (b.x - a.x)
    }
    return null
}

/** Número pseudoaleatório estável em 0..1 para (ícone, rodada). */
private fun hash01(i: Int, round: Int, salt: Int): Float {
    var h = i * 73_856_093 xor round * 19_349_663 xor salt * 83_492_791
    h = h xor (h ushr 13)
    h *= 1_274_126_177
    h = h xor (h ushr 16)
    return (h and 0x7fffffff) / 2_147_483_647f
}

/**
 * Cada ícone nasce no gráfico de rosca ([source]; sem ele, logo acima da cena), cai em
 * direção a um ponto do guarda-sol, bate e quica sempre para trás do personagem (esquerda).
 */
private fun DrawScope.drawSocialRain(seconds: Float, source: RainSourceArt?) {
    val kinds = SocialIcon.entries
    for (i in 0 until ICON_COUNT) {
        val local = seconds + i * ICON_LIFE_S / ICON_COUNT
        val life = (local / ICON_LIFE_S).toInt()
        val age = local - life * ICON_LIFE_S
        // 10 vidas por volta do loop: ao reiniciar, cada ícone segue na mesma rodada (sem pular).
        val round = life % 10
        val kind = kinds[(hash01(i, round, 4) * kinds.size).toInt().coerceAtMost(kinds.size - 1)]
        val spin0 = hash01(i, round, 5) * 40f - 20f
        // Onde vai bater: qualquer ponto da parte de cima do guarda-sol.
        val landX = 40f + hash01(i, round, 2) * 425f
        val landY = canopyY(landX) ?: 60f
        // De onde sai: um ponto dentro da rosca.
        val spawn = if (source != null) {
            val a = hash01(i, round, 1) * 2f * PI.toFloat()
            val r = source.radius * 0.6f * hash01(i, round, 3)
            Offset(source.center.x + cos(a) * r, source.center.y + sin(a) * r)
        } else {
            Offset(landX, SPAWN_TOP)
        }

        // Integração simples: cai indo em direção ao ponto de impacto, bate e quica para trás.
        var x = spawn.x
        var y = spawn.y
        var vx = 0f
        var vy = 40f
        var bounceAt = -1f
        val dt = 1f / 60f
        var t = 0f
        while (t < age) {
            vy += GRAVITY * dt
            y += vy * dt
            if (bounceAt < 0f) {
                val p = ((y - spawn.y) / (landY - spawn.y)).coerceIn(0f, 1f)
                x = spawn.x + (landX - spawn.x) * p
                val cy = canopyY(x)
                if (cy != null && y + ICON_SIZE * 0.45f >= cy) {
                    y = cy - ICON_SIZE * 0.45f
                    // Sempre para trás do personagem (esquerda): nunca na frente dele.
                    vx = -(170f + hash01(i, round, 7) * 130f)
                    vy = -(170f + hash01(i, round, 8) * 90f)
                    bounceAt = t
                }
            } else {
                x += vx * dt
            }
            t += dt
        }
        // Sai do gráfico crescendo; some depois do quique.
        val grow = 0.35f + 0.65f * (age / 0.35f).coerceIn(0f, 1f)
        val fadeIn = (age / 0.2f).coerceIn(0f, 1f)
        val fadeOut = if (bounceAt >= 0f) 1f - ((age - bounceAt - 0.25f) / 0.45f).coerceIn(0f, 1f) else 1f
        val alpha = fadeIn * fadeOut
        if (alpha <= 0.01f) continue
        val spin = spin0 + if (bounceAt >= 0f) -420f * (age - bounceAt) else 0f
        rotate(spin, pivot = Offset(x, y)) {
            drawSocialIcon(kind, Offset(x, y), ICON_SIZE * grow, alpha)
        }
        // Faísca rápida no ponto do impacto.
        if (bounceAt >= 0f && age - bounceAt < 0.18f) {
            val k = (age - bounceAt) / 0.18f
            drawCircle(Color.White.copy(alpha = 0.7f * (1f - k)), ICON_SIZE * (0.3f + 0.5f * k), Offset(x, y + ICON_SIZE * 0.4f), style = Stroke(3f))
        }
    }
}

/** Ícone simplificado da rede social, centrado em [c], lado [s]. */
private fun DrawScope.drawSocialIcon(kind: SocialIcon, c: Offset, s: Float, alpha: Float) {
    val tl = Offset(c.x - s / 2, c.y - s / 2)
    val corner = CornerRadius(s * 0.24f)
    val white = Color.White.copy(alpha = alpha)
    when (kind) {
        SocialIcon.INSTAGRAM -> {
            drawRoundRect(
                Brush.linearGradient(
                    listOf(Color(0xFFFEDA75), Color(0xFFFA7E1E), Color(0xFFD62976), Color(0xFF962FBF), Color(0xFF4F5BD5)),
                    start = Offset(tl.x, tl.y + s),
                    end = Offset(tl.x + s, tl.y),
                ),
                tl, Size(s, s), corner, alpha = alpha,
            )
            drawRoundRect(white, Offset(c.x - s * 0.28f, c.y - s * 0.28f), Size(s * 0.56f, s * 0.56f), CornerRadius(s * 0.16f), style = Stroke(s * 0.07f))
            drawCircle(white, s * 0.13f, c, style = Stroke(s * 0.07f))
            drawCircle(white, s * 0.04f, Offset(c.x + s * 0.17f, c.y - s * 0.17f))
        }
        SocialIcon.TIKTOK -> {
            drawRoundRect(Color(0xFF111111).copy(alpha = alpha), tl, Size(s, s), corner)
            fun note(dx: Float, color: Color) {
                val stemX = c.x + s * 0.06f + dx
                drawLine(color, Offset(stemX, c.y - s * 0.27f), Offset(stemX, c.y + s * 0.12f), strokeWidth = s * 0.09f)
                drawCircle(color, s * 0.11f, Offset(stemX - s * 0.11f, c.y + s * 0.13f), style = Stroke(s * 0.08f))
                drawArc(color, 180f, 90f, false, Offset(stemX, c.y - s * 0.27f), Size(s * 0.24f, s * 0.24f), style = Stroke(s * 0.08f))
            }
            note(-s * 0.03f, Color(0xFF25F4EE).copy(alpha = alpha))
            note(s * 0.03f, Color(0xFFFE2C55).copy(alpha = alpha))
            note(0f, white)
        }
        SocialIcon.FACEBOOK -> {
            drawRoundRect(Color(0xFF1877F2).copy(alpha = alpha), tl, Size(s, s), corner)
            drawLine(white, Offset(c.x + s * 0.04f, c.y - s * 0.12f), Offset(c.x + s * 0.04f, c.y + s * 0.38f), strokeWidth = s * 0.12f)
            drawLine(white, Offset(c.x - s * 0.13f, c.y + s * 0.02f), Offset(c.x + s * 0.2f, c.y + s * 0.02f), strokeWidth = s * 0.1f)
            drawArc(white, 180f, 90f, false, Offset(c.x + s * 0.04f, c.y - s * 0.3f), Size(s * 0.36f, s * 0.36f), style = Stroke(s * 0.12f))
        }
        SocialIcon.YOUTUBE -> {
            drawRoundRect(Color(0xFFFF0000).copy(alpha = alpha), Offset(tl.x, c.y - s * 0.36f), Size(s, s * 0.72f), CornerRadius(s * 0.2f))
            drawPath(
                Path().apply {
                    moveTo(c.x - s * 0.12f, c.y - s * 0.17f)
                    lineTo(c.x + s * 0.2f, c.y)
                    lineTo(c.x - s * 0.12f, c.y + s * 0.17f)
                    close()
                },
                white,
            )
        }
        SocialIcon.X -> {
            drawRoundRect(Color(0xFF000000).copy(alpha = alpha), tl, Size(s, s), corner)
            // O "X": traço grosso descendo e traço fino subindo.
            drawLine(white, Offset(c.x - s * 0.22f, c.y - s * 0.25f), Offset(c.x + s * 0.22f, c.y + s * 0.25f), strokeWidth = s * 0.13f)
            drawLine(white, Offset(c.x + s * 0.22f, c.y - s * 0.25f), Offset(c.x - s * 0.22f, c.y + s * 0.25f), strokeWidth = s * 0.05f)
            drawLine(Color(0xFF000000).copy(alpha = alpha), Offset(c.x - s * 0.2f, c.y - s * 0.24f), Offset(c.x + s * 0.2f, c.y + s * 0.24f), strokeWidth = s * 0.04f)
        }
        SocialIcon.SNAPCHAT -> {
            drawRoundRect(Color(0xFFFFFC00).copy(alpha = alpha), tl, Size(s, s), corner)
            // Fantasminha branco com contorno preto.
            val ghost = Path().apply {
                moveTo(c.x - s * 0.2f, c.y + s * 0.02f)
                cubicTo(c.x - s * 0.22f, c.y - s * 0.32f, c.x + s * 0.22f, c.y - s * 0.32f, c.x + s * 0.2f, c.y + s * 0.02f)
                lineTo(c.x + s * 0.28f, c.y + s * 0.1f)
                lineTo(c.x + s * 0.18f, c.y + s * 0.16f)
                quadraticTo(c.x + s * 0.12f, c.y + s * 0.26f, c.x, c.y + s * 0.24f)
                quadraticTo(c.x - s * 0.12f, c.y + s * 0.26f, c.x - s * 0.18f, c.y + s * 0.16f)
                lineTo(c.x - s * 0.28f, c.y + s * 0.1f)
                close()
            }
            drawPath(ghost, white)
            drawPath(ghost, Color(0xFF111111).copy(alpha = alpha), style = Stroke(s * 0.035f))
        }
        SocialIcon.DISCORD -> {
            drawRoundRect(Color(0xFF5865F2).copy(alpha = alpha), tl, Size(s, s), corner)
            // Carinha de controle: corpo branco com dois olhos.
            drawRoundRect(white, Offset(c.x - s * 0.28f, c.y - s * 0.17f), Size(s * 0.56f, s * 0.36f), CornerRadius(s * 0.16f))
            drawCircle(Color(0xFF5865F2).copy(alpha = alpha), s * 0.06f, Offset(c.x - s * 0.1f, c.y))
            drawCircle(Color(0xFF5865F2).copy(alpha = alpha), s * 0.06f, Offset(c.x + s * 0.1f, c.y))
        }
    }
}
