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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.unit.sp
import com.dollarblock.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// Unidade da cena: px da arte `onb_lounge_fg` (460×460, primeiro plano recortado da
// ilustração original: guarda-sol, personagem, cadeira, cooler). A cena tem sempre
// ART_H de altura; a largura acompanha a tela. O fundo de praia é desenhado aqui.
private const val ART_H = 460f
private const val FG_SIZE = 460

/** Superfície de cima do guarda-sol (onde os ícones batem), da ponta esquerda à direita. */
private val CanopyTop = listOf(
    Offset(12f, 207f),
    Offset(22f, 172f),
    Offset(54f, 120f),
    Offset(86f, 82f),
    Offset(113f, 57f),
    Offset(140f, 33f),
    Offset(240f, 32f),
    Offset(310f, 40f),
    Offset(387f, 63f),
    Offset(398f, 85f),
)

// Praia (tudo em verdes, como a arte da 1ª página).
private const val HORIZON = 300f
private val SkyTop = Color(0xFF0A2A1E)
private val SkyMid = Color(0xFF1A5038)
private val SkyHorizon = Color(0xFF4F9C69)
private val SunColor = Color(0xFFE3F7A8)
private val SeaFar = Color(0xFF1D6047)
private val SeaNear = Color(0xFF2E8261)
private val Foam = Color(0xFFA6EBC0)
private val IslandDark = Color(0xFF0F3B2A)
private val IslandRim = Color(0xFF5FA06A)
private val PalmTrunk = Color(0xFF0B2E20)
private val PalmLeaf = Color(0xFF16553A)
private val SandTop = Color(0xFF66AF79)
private val SandBottom = Color(0xFF3B7C55)
private val CloudColor = Color(0xFF2F6E50)

// Chuva de ícones.
private const val ICON_COUNT = 9
private const val ICON_LIFE_S = 2.7f      // de nascer no gráfico a sumir depois do quique
private const val ICON_SIZE = 40f
private const val GRAVITY = 520f          // px da arte / s²
private const val SPAWN_TOP = -30f        // sem o gráfico medido ainda: nascem logo acima da cena
private const val SCENE_LOOP_MS = 27_000  // múltiplo da vida dos ícones: o loop não "pula"

/** Quanto a cena sangra para os lados, até a borda da tela (a margem lateral das páginas). */
private val SCENE_BLEED: Dp = 24.dp

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
fun LoungeScene(rainSource: RainSource?, modifier: Modifier = Modifier) {
    val foreground = ImageBitmap.imageResource(R.drawable.onb_lounge_fg)
    val textMeasurer = rememberTextMeasurer()
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
    var topLeftInRoot by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            // Sangra até a borda da tela, para fundir com a página.
            .layout { measurable, constraints ->
                val extra = SCENE_BLEED.roundToPx()
                val width = constraints.maxWidth + 2 * extra
                val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
                layout(constraints.maxWidth, placeable.height) { placeable.place(-extra, 0) }
            }
            .onGloballyPositioned { topLeftInRoot = it.positionInRoot() },
    ) {
        // Praia: esmaecimento longo em cima, embaixo e à direita, dissolvendo no fundo da página.
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.3f to Color.Black,
                            0.7f to Color.Black,
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
            val scale = size.height / ART_H
            val worldW = size.width / scale
            withTransform({ scale(scale, scale, pivot = Offset.Zero) }) { drawBeach(worldW, seconds ?: 0f) }
        }
        // Primeiro plano (guarda-sol, personagem, cooler): só a base esmaece, para o topo do
        // guarda-sol não sumir junto com o céu.
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        Brush.verticalGradient(0.82f to Color.Black, 1f to Color.Transparent),
                        blendMode = BlendMode.DstIn,
                    )
                },
        ) {
            val scale = size.height / ART_H
            withTransform({ scale(scale, scale, pivot = Offset.Zero) }) {
                drawImage(foreground, dstOffset = IntOffset.Zero, dstSize = IntSize(FG_SIZE, FG_SIZE))
                drawNeonTitle(textMeasurer, seconds ?: 0f)
            }
        }
        // A chuva, fora da camada: pode desenhar acima da cena, desde o gráfico.
        if (seconds != null) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val scale = size.height / ART_H
                val source = rainSource?.let {
                    RainSourceArt(center = (it.center - topLeftInRoot) / scale, radius = it.radius / scale)
                }
                withTransform({ scale(scale, scale, pivot = Offset.Zero) }) { drawSocialRain(seconds, source) }
            }
        }
    }
}

/** [RainSource] já em coordenadas da arte. */
private class RainSourceArt(val center: Offset, val radius: Float)

// ---------------------------------------------------------------------------------------
// Letreiro neon no guarda-sol
// ---------------------------------------------------------------------------------------

/** Uma palavra do letreiro: centrada entre as bordas do gomo escuro, inclinada como elas. */
private class BandWord(val text: String, val center: Offset, val tilt: Float, val size: Float)

/**
 * "Dollar" no gomo escuro da esquerda e "Block" na faixa escura da frente (as listras azuis
 * da ilustração). Centro e inclinação vêm das bordas de cada faixa, medidas coluna a coluna
 * no `onb_lounge_fg`: a da frente tem ~48 px de altura entre x 165 e 245 e sobe ~15,6°; o
 * gomo da esquerda, entre a borda de cima e a bainha, tem a linha do meio a ~38° (a palavra
 * fica 10° mais deitada que ela, que lê melhor).
 */
private val BandWords = listOf(
    BandWord("Dollar", Offset(101f, 111f), tilt = -28f, size = 30f),
    BandWord("Block", Offset(205f, 71f), tilt = -15.6f, size = 27f),
)

private val NeonCore = Color(0xFFCFFFE0)
private val NeonGlow = Color(0xFF39FF88)

/** Letreiro em negrito neon; o brilho sobe e desce devagar, como luz de neon respirando. */
private fun DrawScope.drawNeonTitle(textMeasurer: TextMeasurer, t: Float) {
    val glow = 0.6f + 0.4f * (0.5f + 0.5f * sin(t * 1.5f))
    for (word in BandWords) {
        val c = word.center
        rotate(word.tilt, pivot = c) {
            // Achatado como o tecido, que está inclinado em relação a quem olha.
            withTransform({ scale(1f, 0.9f, pivot = c) }) {
                fun layout(color: Color, blur: Float) = textMeasurer.measure(
                    word.text,
                    TextStyle(
                        color = color,
                        fontSize = word.size.toSp(),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.4.sp,
                        shadow = if (blur > 0f) Shadow(NeonGlow.copy(alpha = glow), Offset.Zero, blur) else null,
                    ),
                )
                // Halo largo, halo justo e o miolo claro do tubo de neon.
                val wide = layout(NeonGlow.copy(alpha = 0.5f * glow), 16f * glow)
                val tight = layout(NeonGlow.copy(alpha = 0.9f), 4f)
                val core = layout(NeonCore, 0f)
                // Centro visual = meio da altura das maiúsculas (≈ 0,7 do tamanho), não da caixa do texto.
                val capMiddle = core.firstBaseline - word.size * 0.35f
                val topLeft = Offset(c.x - core.size.width / 2f, c.y - capMiddle)
                drawText(wide, topLeft = topLeft)
                drawText(tight, topLeft = topLeft)
                drawText(core, topLeft = topLeft, alpha = 0.8f + 0.2f * glow)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------
// Praia
// ---------------------------------------------------------------------------------------

/** Céu, sol, nuvens, gaivotas, mar com reflexo, ilha com coqueiros e areia — em verdes. */
private fun DrawScope.drawBeach(w: Float, t: Float) {
    // Céu.
    drawRect(
        Brush.verticalGradient(0f to SkyTop, 0.45f to SkyMid, 1f to SkyHorizon, startY = 0f, endY = HORIZON),
        size = Size(w, HORIZON),
    )
    // Sol baixo, com halo que respira.
    val sun = Offset(w * 0.74f, HORIZON - 52f)
    val pulse = 1f + 0.05f * sin(t * 1.3f)
    drawCircle(Brush.radialGradient(listOf(SunColor.copy(alpha = 0.45f), Color.Transparent), sun, 110f * pulse), 110f * pulse, sun)
    drawCircle(SunColor, 30f, sun)
    // Nuvens chapadas passando devagar.
    for ((i, fy) in listOf(70f, 130f, 45f).withIndex()) {
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
    // Reflexo do sol: tracinhos que piscam.
    for (k in 0 until 9) {
        val y = HORIZON + 8f + k * 9f
        val half = 26f - k * 1.8f + 6f * sin(t * 3f + k)
        drawLine(SunColor.copy(alpha = 0.55f - k * 0.04f), Offset(sun.x - half, y), Offset(sun.x + half, y), strokeWidth = 2.5f, cap = StrokeCap.Round)
    }
    // Ondinhas.
    for (k in 0 until 7) {
        val y = HORIZON + 20f + (k % 4) * 18f
        val x = ((t * 14f + k * 160f) % (w + 120f)) - 60f
        drawLine(Foam.copy(alpha = 0.35f), Offset(x, y), Offset(x + 34f, y), strokeWidth = 2f, cap = StrokeCap.Round)
    }

    // Ilhas: a grande com coqueiros e uma pequena ao fundo.
    drawIsland(Offset(w * 0.9f, HORIZON + 2f), 0.45f, t, palms = false)
    drawIsland(Offset(w * 0.6f, HORIZON + 4f), 1f, t, palms = true)

    // Areia com a espuma da beira.
    val sand = Path().apply {
        moveTo(0f, 392f)
        cubicTo(w * 0.3f, 378f, w * 0.6f, 404f, w, 386f)
        lineTo(w, ART_H)
        lineTo(0f, ART_H)
        close()
    }
    drawPath(sand, Brush.verticalGradient(0f to SandTop, 1f to SandBottom, startY = 378f, endY = ART_H))
    val swash = 3f * sin(t * 1.6f)
    drawPath(
        Path().apply {
            moveTo(0f, 392f + swash)
            cubicTo(w * 0.3f, 378f + swash, w * 0.6f, 404f + swash, w, 386f + swash)
        },
        Foam.copy(alpha = 0.7f),
        style = Stroke(3.5f, cap = StrokeCap.Round),
    )
    // Sombra do guarda-sol e da cadeira na areia.
    drawOval(Color.Black.copy(alpha = 0.28f), Offset(40f, 428f), Size(440f, 34f))
}

private fun DrawScope.drawCloud(c: Offset, s: Float) {
    val color = CloudColor.copy(alpha = 0.7f)
    drawRoundRect(color, Offset(c.x - 44f * s, c.y - 8f * s), Size(88f * s, 18f * s), CornerRadius(9f * s))
    drawCircle(color, 15f * s, Offset(c.x - 12f * s, c.y - 8f * s))
    drawCircle(color, 11f * s, Offset(c.x + 12f * s, c.y - 6f * s))
}

private fun DrawScope.drawIsland(base: Offset, s: Float, t: Float, palms: Boolean) {
    val half = 135f * s
    drawOval(IslandRim, Offset(base.x - half - 8f * s, base.y - 6f * s), Size(2 * half + 16f * s, 12f * s))
    drawPath(
        Path().apply {
            moveTo(base.x - half, base.y)
            cubicTo(base.x - half * 0.5f, base.y - 40f * s, base.x + half * 0.3f, base.y - 46f * s, base.x + half, base.y)
            close()
        },
        IslandDark,
    )
    if (!palms) return
    for ((i, px) in listOf(-30f, 22f, 52f).withIndex()) {
        val root = Offset(base.x + px * s, base.y - 26f * s)
        val lean = if (i == 1) -1f else 1f
        val top = Offset(root.x + lean * 14f * s, root.y - (52f - i * 8f) * s)
        drawPath(
            Path().apply {
                moveTo(root.x, root.y)
                quadraticTo(root.x + lean * 2f * s, (root.y + top.y) / 2, top.x, top.y)
            },
            PalmTrunk,
            style = Stroke(5f * s, cap = StrokeCap.Round),
        )
        // Folhas balançando de leve.
        val sway = sin(t * 1.4f + i) * 6f
        for (k in 0 until 6) {
            val ang = (-160f + k * 28f + sway) * PI.toFloat() / 180f
            val tip = Offset(top.x + cos(ang) * 30f * s, top.y + sin(ang) * 30f * s + 14f * s)
            val ctrl = Offset(top.x + cos(ang) * 16f * s, top.y + sin(ang) * 16f * s - 8f * s)
            drawPath(
                Path().apply {
                    moveTo(top.x, top.y)
                    quadraticTo(ctrl.x, ctrl.y, tip.x, tip.y)
                },
                PalmLeaf,
                style = Stroke(5f * s, cap = StrokeCap.Round),
            )
        }
    }
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
        val landX = 30f + hash01(i, round, 2) * 355f
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
