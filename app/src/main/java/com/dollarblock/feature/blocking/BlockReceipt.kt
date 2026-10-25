package com.dollarblock.feature.blocking

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dollarblock.R
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random

/*
 * A fatura da tela de bloqueio: papel térmico com bordas serrilhadas, escudo no topo,
 * nº e data, valor grande, código de barras e o carimbo "BLOQUEADO" (o momento-assinatura
 * do app). Referência de layout: docs/art/block_receipt_reference.webp — só referência; tudo aqui é desenhado
 * em código porque o conteúdo (app, mensagem, preço, data) muda a cada bloqueio.
 */

private val Paper = Color(0xFFF4F1E8)
private val Ink = Color(0xFF14231E)
private val LogoGreen = Color(0xFF0B4033)
/** Vermelho de tinta de carimbo (mais fechado que o BlockingRed da interface). */
private val StampInk = Color(0xFFC62828)

private val ZigzagTooth = 9.dp
private val ZigzagDepth = 5.dp

@Composable
internal fun InvoiceReceipt(
    appLabel: String,
    priceText: String,
    message: String,
    invoiceNumber: String,
    issuedAt: String,
    stampScale: Animatable<Float, AnimationVector1D>,
    stampAlpha: Animatable<Float, AnimationVector1D>,
    modifier: Modifier = Modifier,
) {
    val shape = remember { ZigzagEdgeShape(ZigzagTooth, ZigzagDepth) }
    val divider = Ink.copy(alpha = 0.35f)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(10.dp, shape, clip = false)
            .clip(shape)
            .background(Paper)
            .paperGrain()
            .padding(horizontal = 14.dp, vertical = 12.dp + ZigzagDepth),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_notification),
            contentDescription = null,
            tint = LogoGreen,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.block_receipt_header),
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            letterSpacing = 1.sp,
            color = Ink,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = stringResource(R.string.block_receipt_meta, invoiceNumber, issuedAt),
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            color = Ink.copy(alpha = 0.7f),
        )
        ReceiptDivider(divider)
        // Nome do app à esquerda e o carimbo ao lado, torto e um pouco por cima da borda,
        // como um carimbo de verdade batido sobre o papel.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = appLabel,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = Ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            StampText(
                stampScale = stampScale,
                stampAlpha = stampAlpha,
                modifier = Modifier.offset(x = 6.dp),
            )
        }
        ReceiptDivider(divider)
        Text(
            text = message,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            color = Ink.copy(alpha = 0.85f),
            textAlign = TextAlign.Center,
        )
        ReceiptDivider(divider)
        Text(
            text = priceText,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 28.sp,
            color = Ink,
        )
        Text(
            text = stringResource(R.string.block_receipt_caption),
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            lineHeight = 12.sp,
            color = Ink.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
        )
        ReceiptDivider(divider)
        Barcode(
            seed = invoiceNumber.hashCode(),
            color = Ink,
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .height(24.dp),
        )
    }
}

@Composable
private fun StampText(
    stampScale: Animatable<Float, AnimationVector1D>,
    stampAlpha: Animatable<Float, AnimationVector1D>,
    modifier: Modifier = Modifier,
) {
    Text(
        text = stringResource(R.string.block_stamp),
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Black,
        fontSize = 11.sp,
        letterSpacing = 1.sp,
        color = StampInk,
        maxLines = 1,
        modifier = modifier
            .graphicsLayer {
                rotationZ = -12f
                scaleX = stampScale.value
                scaleY = stampScale.value
                // Tinta de carimbo nunca sai 100% chapada.
                alpha = stampAlpha.value * 0.88f
            }
            .border(2.dp, StampInk, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
private fun ReceiptDivider(color: Color) {
    Canvas(
        Modifier
            .padding(vertical = 8.dp)
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

/** Código de barras decorativo: barras de 1–3 módulos, sempre o mesmo para o mesmo [seed]. */
@Composable
private fun Barcode(seed: Int, color: Color, modifier: Modifier = Modifier) {
    val modules = remember(seed) {
        val rnd = Random(seed)
        // Guardas nas pontas, como num EAN; miolo sorteado, alternando barra e espaço.
        listOf(1, 1, 1) + List(52) { rnd.nextInt(1, 4) } + listOf(1, 1, 1)
    }
    Canvas(modifier) {
        val unit = size.width / modules.sum()
        var x = 0f
        modules.forEachIndexed { i, m ->
            val w = m * unit
            if (i % 2 == 0) drawRect(color, topLeft = Offset(x, 0f), size = Size(w, size.height))
            x += w
        }
    }
}

/** Grão de papel: pontinhos de tinta quase invisíveis, sempre no mesmo lugar. */
private fun Modifier.paperGrain(): Modifier = drawWithCache {
    val rnd = Random(7)
    val count = (size.width * size.height / 90f).roundToInt()
    val dots = List(count) { Offset(rnd.nextFloat() * size.width, rnd.nextFloat() * size.height) }
    onDrawBehind {
        drawPoints(dots, PointMode.Points, Ink.copy(alpha = 0.06f), strokeWidth = 1.5f)
    }
}

/** Retângulo com borda serrilhada em cima e embaixo, como papel destacado da maquininha. */
private class ZigzagEdgeShape(private val tooth: Dp, private val depth: Dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val d = with(density) { depth.toPx() }
        val n = max(1, (size.width / with(density) { tooth.toPx() }).roundToInt())
        val step = size.width / n
        val path = Path().apply {
            moveTo(0f, d)
            for (i in 0 until n) {
                lineTo(i * step + step / 2, 0f)
                lineTo((i + 1) * step, d)
            }
            lineTo(size.width, size.height - d)
            for (i in n downTo 1) {
                lineTo(i * step - step / 2, size.height)
                lineTo((i - 1) * step, size.height - d)
            }
            close()
        }
        return Outline.Generic(path)
    }
}
