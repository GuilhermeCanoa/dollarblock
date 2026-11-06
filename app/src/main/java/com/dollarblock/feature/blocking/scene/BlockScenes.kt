package com.dollarblock.feature.blocking.scene

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dollarblock.R
import com.dollarblock.core.designsystem.MintGlow
import com.dollarblock.core.designsystem.NeutralWhite
import com.dollarblock.core.designsystem.mascot.drawShieldMascot
import com.dollarblock.core.designsystem.mascot.drawShieldMascotHands
import com.dollarblock.core.designsystem.mascot.shieldMascotHand
import com.dollarblock.feature.blocking.InvoiceReceipt
import com.dollarblock.feature.blocking.scene.BlockSceneChoreography.Held
import com.dollarblock.feature.blocking.scene.BlockSceneChoreography.Hero
import com.dollarblock.feature.blocking.scene.BlockSceneChoreography.Stamp
import kotlinx.coroutines.launch
import kotlin.math.hypot

/*
 * As três cenas da tela de bloqueio (E25). Cada uma é só o slot visual acima dos botões: a
 * BlockActivity monta o scaffold (cena + BlockControls + rodapé) e a lógica de pagamento não
 * passa por aqui. O mascote é decorativo: Canvas sem semântica. Tamanhos e poses: os do
 * mock (docs/design/block-screen-variants/src/mock.html).
 */

/** O que vai no recibo desta abertura: muda a cada bloqueio. */
@Immutable
internal data class BlockReceiptContent(
    val appLabel: String,
    val priceText: String,
    val message: String,
    val invoiceNumber: String,
    val issuedAt: String,
)

/** Fundo da tela de bloqueio (o piso escuro da arte antiga) e o brilho do centro. */
internal val BlockBackground = Color(0xFF01120E)
internal val BlockGlowCenter = Color(0xFF0B3A2C)
internal val BlockGlowMid = Color(0xFF03201A)

/** Largura do recibo: estreito como papel de maquininha. */
private val ReceiptWidth = 248.dp

/** Desenha o mascote num Canvas cujo canto superior esquerdo é ([left], [top]) em unidades da arte. */
private fun DrawScope.inStage(left: Float, top: Float, unit: Float, block: DrawScope.() -> Unit) =
    translate(-left * unit, -top * unit, block)

// ── A — o mascote é a tela ─────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MascotHeroScene(
    receipt: BlockReceiptContent,
    clock: BlockSceneClock,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val pop = remember { Animatable(0f) }
    var sheetOpen by remember { mutableStateOf(false) }
    val openReceipt: () -> Unit = {
        if (!sheetOpen) {
            scope.launch {
                // O recibinho salta e some na mão; aí a fatura completa sobe.
                if (clock.animate) pop.animateTo(1f, tween(260)) else pop.snapTo(1f)
                sheetOpen = true
            }
        }
    }
    val stageW = Hero.RIGHT - Hero.LEFT
    val stageH = Hero.BOTTOM - Hero.TOP

    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(
            Modifier
                .size(276.dp, 276.dp * (stageH / stageW))
                .pointerInput(Unit) {
                    detectTapGestures { tap ->
                        val unit = size.width / stageW
                        val hand = shieldMascotHand(clock.pose(), front = true)
                        // O papel fica logo abaixo e à direita da mão.
                        val center = Offset((hand.x + 3f - Hero.LEFT) * unit, (hand.y + 11f - Hero.TOP) * unit)
                        if (hypot(tap.x - center.x, tap.y - center.y) < 26f * unit) openReceipt()
                    }
                },
        ) {
            val unit = size.width / stageW
            inStage(Hero.LEFT, Hero.TOP, unit) {
                drawShieldMascot(clock.pose().copy(propOpen = pop.value), unit)
            }
        }
        Spacer(Modifier.height(18.dp))
        Text(
            text = stringResource(R.string.block_scene_title, receipt.appLabel),
            fontSize = 26.sp,
            lineHeight = 32.sp,
            fontWeight = FontWeight.Medium,
            color = NeutralWhite,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .graphicsLayer { alpha = Hero.textAlpha(clock.t, 0) },
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.block_scene_subtitle),
            fontSize = 15.sp,
            lineHeight = 21.sp,
            color = NeutralWhite.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .widthIn(max = 300.dp)
                .padding(horizontal = 24.dp)
                .graphicsLayer { alpha = Hero.textAlpha(clock.t, 1) },
        )
        Spacer(Modifier.height(26.dp))
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.graphicsLayer { alpha = Hero.textAlpha(clock.t, 2) },
        ) {
            Text(
                text = receipt.priceText,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 42.sp,
                lineHeight = 48.sp,
                color = MintGlow,
            )
            Text(
                text = stringResource(R.string.block_scene_valid_until),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = NeutralWhite.copy(alpha = 0.55f),
            )
        }
        Spacer(Modifier.height(14.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .graphicsLayer { alpha = Hero.textAlpha(clock.t, 3) }
                .clip(RoundedCornerShape(14.dp))
                .border(1.dp, NeutralWhite.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
                .clickable(role = Role.Button, onClick = openReceipt)
                .padding(horizontal = 12.dp, vertical = 5.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ReceiptLong,
                contentDescription = null,
                tint = NeutralWhite.copy(alpha = 0.55f),
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.block_scene_view_receipt),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = NeutralWhite.copy(alpha = 0.55f),
            )
        }
    }

    if (sheetOpen) {
        val haptic = LocalHapticFeedback.current
        val stampScale = remember { Animatable(if (clock.animate) 2.4f else 1f) }
        val stampAlpha = remember { Animatable(if (clock.animate) 0f else 1f) }
        LaunchedEffect(Unit) {
            if (!clock.animate) return@LaunchedEffect
            // O carimbo cai como na tela de antes: escala 2,4 → 1 com mola e haptic no impacto.
            kotlinx.coroutines.delay(250)
            stampAlpha.animateTo(1f, tween(120))
            stampScale.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium))
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        ModalBottomSheet(
            onDismissRequest = {
                sheetOpen = false
                scope.launch { if (clock.animate) pop.animateTo(0f, tween(220)) else pop.snapTo(0f) }
            },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = BlockGlowMid,
            contentColor = NeutralWhite,
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(top = 8.dp, bottom = 28.dp),
            ) {
                InvoiceReceipt(
                    appLabel = receipt.appLabel,
                    priceText = receipt.priceText,
                    message = receipt.message,
                    invoiceNumber = receipt.invoiceNumber,
                    issuedAt = receipt.issuedAt,
                    stampScale = { stampScale.value },
                    stampAlpha = { stampAlpha.value },
                    modifier = Modifier.width(ReceiptWidth),
                )
            }
        }
    }
}

// ── B — atrás do recibo ────────────────────────────────────────────────────────────────────

@Composable
internal fun HeldReceiptScene(
    receipt: BlockReceiptContent,
    clock: BlockSceneClock,
    modifier: Modifier = Modifier,
) {
    val stageW = Held.RIGHT - Held.LEFT
    val stageH = Held.BOTTOM - Held.TOP
    val unitDp = ReceiptWidth / stageW
    val screenH = LocalConfiguration.current.screenHeightDp.dp
    Box(
        modifier = modifier
            .width(ReceiptWidth)
            .graphicsLayer {
                // Recibo + mascote sobem juntos de baixo da tela, oscilando como papel.
                translationY = Held.rise(clock.t) * screenH.toPx() -
                    Held.tugDp(clock.gesture, clock.gestureT).dp.toPx()
                translationX = Held.tremorDp(clock.t).dp.toPx()
                rotationZ = Held.paperRotation(clock.t)
            },
    ) {
        // Corpo atrás do papel (as mãos vêm depois, por cima dele).
        Canvas(Modifier.size(ReceiptWidth, unitDp * stageH)) {
            val unit = size.width / stageW
            inStage(Held.LEFT, Held.TOP, unit) { drawShieldMascot(clock.pose(), unit, hands = false) }
        }
        Column {
            Spacer(Modifier.height(unitDp * (Held.RECEIPT_TOP - Held.TOP)))
            InvoiceReceipt(
                appLabel = receipt.appLabel,
                priceText = receipt.priceText,
                message = receipt.message,
                invoiceNumber = receipt.invoiceNumber,
                issuedAt = receipt.issuedAt,
                stampScale = { Held.stampScale(clock.t) },
                stampAlpha = { Held.stampAlpha(clock.t) },
                showMeta = false,
            )
        }
        Canvas(Modifier.size(ReceiptWidth, unitDp * stageH)) {
            val unit = size.width / stageW
            inStage(Held.LEFT, Held.TOP, unit) { drawShieldMascotHands(clock.pose(), unit) }
        }
    }
}

// ── C — o carimbo ──────────────────────────────────────────────────────────────────────────

@Composable
internal fun StampScene(
    receipt: BlockReceiptContent,
    shortMessage: String,
    clock: BlockSceneClock,
    modifier: Modifier = Modifier,
) {
    val stageW = Stamp.RIGHT - Stamp.LEFT
    val stageH = Stamp.BOTTOM - Stamp.TOP
    val mascotW = 156.dp
    Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { translationX = Stamp.shakeDp(clock.t).dp.toPx() },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.block_screen_title),
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium,
            color = NeutralWhite.copy(alpha = 0.8f),
            modifier = Modifier.graphicsLayer { alpha = Stamp.titleAlpha(clock.t) },
        )
        Spacer(Modifier.height(22.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(430.dp),
        ) {
            Box(
                Modifier
                    .offset(x = 4.dp, y = 10.dp)
                    .width(252.dp)
                    .graphicsLayer { rotationZ = -1.5f }
                    .printReveal { Stamp.reveal(clock.t) },
            ) {
                InvoiceReceipt(
                    appLabel = receipt.appLabel,
                    priceText = receipt.priceText,
                    message = shortMessage,
                    invoiceNumber = receipt.invoiceNumber,
                    issuedAt = receipt.issuedAt,
                    stampScale = { Stamp.stampScale(clock.t) },
                    stampAlpha = { Stamp.stampAlpha(clock.t) },
                    emphasizedStamp = true,
                )
            }
            Canvas(
                Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 22.dp, y = 34.dp)
                    .size(mascotW, mascotW * (stageH / stageW)),
            ) {
                val unit = size.width / stageW
                inStage(Stamp.LEFT, Stamp.TOP, unit) { drawShieldMascot(clock.pose(), unit) }
            }
        }
    }
}

/**
 * O recibo saindo da impressora: só a fração [reveal] de cima fica visível, e o papel desce
 * por ela — a borda serrilhada de baixo aparece primeiro e está sempre à vista. Completo,
 * desenha normal (com a sombra).
 */
private fun Modifier.printReveal(reveal: () -> Float): Modifier = drawWithContent {
    val r = reveal()
    if (r >= 1f) {
        drawContent()
    } else if (r > 0f) {
        clipRect(bottom = size.height * r) {
            translate(top = -(1f - r) * size.height) { this@drawWithContent.drawContent() }
        }
    }
}

// ── Encaixe ────────────────────────────────────────────────────────────────────────────────

/**
 * Centraliza a cena no espaço que sobrou acima dos botões; se ela não couber (tela baixa,
 * fonte grande), encolhe por igual em vez de cortar.
 */
@Composable
internal fun FitScene(modifier: Modifier = Modifier, minScale: Float = 0.55f, content: @Composable () -> Unit) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        val p = measurables.first().measure(
            Constraints(minWidth = 0, maxWidth = w, minHeight = 0, maxHeight = Constraints.Infinity),
        )
        val scale = if (p.height > h && p.height > 0) (h.toFloat() / p.height).coerceAtLeast(minScale) else 1f
        layout(w, h) {
            p.placeWithLayer((w - p.width) / 2, (h - p.height) / 2) {
                scaleX = scale
                scaleY = scale
            }
        }
    }
}

/** Respiro lateral dos controles por cena (a C usa um pouco menos, como no mock). */
internal fun controlsPaddingFor(scene: BlockScene): Dp = if (scene == BlockScene.C_STAMP) 24.dp else 32.dp
