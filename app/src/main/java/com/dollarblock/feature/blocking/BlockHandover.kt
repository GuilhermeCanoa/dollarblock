package com.dollarblock.feature.blocking

import androidx.compose.foundation.Image
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import com.dollarblock.R

/*
 * Cena da tela de bloqueio: o guardião do cofre entrega o recibo ao usuário.
 *
 * Arte: drawable-nodpi/block_handover.jpg (1024×1536, gerada por scripts/build-block-handover.ps1
 * a partir de docs/art/block_handover_source.webp). O guardião segura um recibo EM BRANCO; o
 * recibo de verdade (InvoiceReceipt, com app, mensagem e preço) nasce em cima dele e vem para
 * a frente da tela. Animado em código, nesta ordem:
 *   1. entrega   — o recibo sai da mão (ArtReceipt) e cresce até o lugar dele; a arte recua e
 *                  escurece um pouco;
 *   2. carimbo   — o "BLOQUEADO" cai no recibo (BlockActivity.StampText);
 *   3. epílogo   — a arte escurece de vez e a frase aparece devagar, como a frase da tela
 *                  preta depois de perder num jogo.
 * Pose parada (escala de animação 0): tudo no estado final.
 */

/** Tamanho da arte, em px. */
internal const val ART_W = 1024f
internal const val ART_H = 1536f

/** O recibo em branco na mão do guardião, em px da arte (block_handover.jpg 1024×1536). */
internal val ArtReceipt = Rect(left = 382f, top = 724f, right = 561f, bottom = 981f)

/** Cor da base da arte (piso escuro); é o fundo da tela abaixo dela. */
internal val BlockArtBackground = Color(0xFF01120E)

/** Quanto a arte encolhe enquanto o recibo vem para a frente (dá a ideia de profundidade). */
private const val ART_RECEDE_SCALE = 0.96f
/** Escurecimento da arte ao fim da entrega e, somado, ao fim do epílogo (máx. 1). */
private const val HANDOVER_DIM = 0.25f
private const val EPILOGUE_DIM = 0.45f

internal const val HANDOVER_DELAY_MS = 350L
internal const val HANDOVER_MS = 800
internal const val STAMP_DELAY_MS = 150L
internal const val EPILOGUE_DELAY_MS = 1100L
internal const val EPILOGUE_MS = 2400

/** Leva o recibo da mão do guardião ([from]) até o lugar dele no layout ([to]), por [progress]. */
internal fun Modifier.receiptHandover(progress: Float, from: Rect?, to: Rect?): Modifier =
    graphicsLayer {
        if (from == null || to == null || to.width == 0f) {
            alpha = if (progress >= 1f) 1f else 0f
            return@graphicsLayer
        }
        val startScale = from.width / to.width
        val scale = lerp(startScale, 1f, progress)
        scaleX = scale
        scaleY = scale
        translationX = lerp(from.center.x - to.center.x, 0f, progress)
        translationY = lerp(from.center.y - to.center.y, 0f, progress)
        // O papel da arte está levemente torto; endireita no caminho.
        rotationZ = lerp(-3f, 0f, progress)
        alpha = (progress / 0.3f).coerceIn(0f, 1f)
        transformOrigin = TransformOrigin.Center
    }

/**
 * A arte, ocupando a largura da tela a partir do topo. [handover] e [epilogue] vão de 0 a 1.
 * Um degradê no topo protege o título da luz da luminária e outro na base funde o piso ao fundo.
 */
@Composable
internal fun BlockHandoverArt(
    handover: Float,
    epilogue: Float,
    modifier: Modifier = Modifier,
) {
    Image(
        painter = painterResource(R.drawable.block_handover),
        contentDescription = null,
        contentScale = ContentScale.FillBounds,
        modifier = modifier
            .graphicsLayer {
                val s = lerp(1f, ART_RECEDE_SCALE, handover)
                scaleX = s
                scaleY = s
                // Recua em direção à mão: o ponto de onde o recibo sai fica parado.
                transformOrigin = TransformOrigin(
                    ArtReceipt.center.x / ART_W,
                    ArtReceipt.center.y / ART_H,
                )
            }
            .drawWithContent {
                drawContent()
                drawRect(
                    Brush.verticalGradient(
                        0f to BlockArtBackground.copy(alpha = 0.85f),
                        1f to Color.Transparent,
                        endY = size.height * 0.16f,
                    ),
                )
                drawRect(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        1f to BlockArtBackground,
                        startY = size.height * 0.85f,
                        endY = size.height,
                    ),
                    topLeft = Offset(0f, size.height * 0.85f),
                    size = Size(size.width, size.height * 0.15f),
                )
                val dim = (HANDOVER_DIM * handover + EPILOGUE_DIM * epilogue).coerceAtMost(1f)
                drawRect(BlockArtBackground.copy(alpha = dim))
            },
    )
}

/**
 * Posiciona a tela de bloqueio sobre a arte: [title] no topo, [footer] na base e [stack]
 * (recibo + botões) com o recibo começando um pouco acima de [receiptAnchorTop] — o topo do
 * recibo da arte —, para cobrir a mão e o papel em branco quando chegar. Se não couber, o
 * stack sobe. [quote] fica centrada no vão entre o título e o recibo; se não couber, não
 * aparece.
 */
@Composable
internal fun BlockHandoverLayout(
    receiptAnchorTop: Dp,
    title: @Composable () -> Unit,
    quote: @Composable () -> Unit,
    stack: @Composable () -> Unit,
    footer: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Layout(contents = listOf(title, quote, stack, footer), modifier = modifier) {
            (titleM, quoteM, stackM, footerM), constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        val gap = 12.dp.roundToPx()
        val topPad = 8.dp.roundToPx()

        val t = titleM.first().measure(loose)
        val f = footerM.first().measure(loose)
        val s = stackM.first().measure(loose)
        val q = quoteM.first().measure(loose)

        val titleBottom = topPad + t.height
        val desired = (receiptAnchorTop - 12.dp).roundToPx()
        val stackY = desired
            .coerceAtMost(h - f.height - s.height)
            .coerceAtLeast(titleBottom + gap)
        val quoteRoom = stackY - titleBottom

        layout(w, h) {
            t.placeRelative((w - t.width) / 2, topPad)
            s.placeRelative((w - s.width) / 2, stackY)
            f.placeRelative((w - f.width) / 2, h - f.height)
            if (q.height <= quoteRoom - 2 * gap) {
                q.placeRelative((w - q.width) / 2, titleBottom + (quoteRoom - q.height) / 2)
            }
        }
    }
}

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
