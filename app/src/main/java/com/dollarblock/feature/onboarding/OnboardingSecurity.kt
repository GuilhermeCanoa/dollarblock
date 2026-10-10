package com.dollarblock.feature.onboarding

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dollarblock.R
import com.dollarblock.core.designsystem.DeepGreenVelvet
import com.dollarblock.core.designsystem.MintGlow
import com.dollarblock.core.designsystem.VelvetSurface
import com.dollarblock.core.designsystem.VelvetSurfaceHigh

/** Uma garantia do cofre: ícone próprio + texto. */
data class SecurityPoint(val icon: ImageVector, val textRes: Int)

/**
 * O "cofre" de segurança da página da Tranca: cartão com borda neon que corre devagar,
 * escudo com halo pulsando, selo "SIGILO BANCÁRIO" e cada garantia com ícone próprio,
 * entrando uma a uma. Tudo o que ele afirma precisa continuar verdade no app (E19).
 */
@Composable
fun SecurityVault(
    active: Boolean,
    titleRes: Int,
    points: List<SecurityPoint>,
    modifier: Modifier = Modifier,
    firstOrder: Int = 0,
) {
    val animate = rememberAnimationsEnabled()
    val transition = rememberInfiniteTransition(label = "securityVault")
    // Brilho da borda percorrendo o cartão em diagonal.
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3_600, easing = LinearEasing), RepeatMode.Restart),
        label = "borderSweep",
    )
    // Halo do escudo: um anel que cresce e some.
    val halo by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2_200, easing = LinearEasing), RepeatMode.Restart),
        label = "shieldHalo",
    )

    val shape = RoundedCornerShape(20.dp)
    val travel = if (animate) sweep else 0.5f
    val borderBrush = Brush.linearGradient(
        colorStops = arrayOf(
            0f to MintGlow.copy(alpha = 0.25f),
            (travel - 0.15f).coerceIn(0f, 1f) to MintGlow.copy(alpha = 0.25f),
            travel to MintGlow,
            (travel + 0.15f).coerceIn(0f, 1f) to MintGlow.copy(alpha = 0.25f),
            1f to MintGlow.copy(alpha = 0.25f),
        ),
        start = Offset(0f, 0f),
        end = Offset(1_000f, 1_000f),
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(VelvetSurfaceHigh, VelvetSurface)))
            .border(1.5.dp, borderBrush, shape)
            .padding(horizontal = 18.dp, vertical = 20.dp),
    ) {
        // O cabeçalho "vaza" para cima e para a esquerda do padding: o escudo fica quase na
        // quina superior esquerda do cartão, com o selo ao lado.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.bleed(start = 14.dp, top = 22.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(64.dp),
            ) {
                if (animate) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .graphicsLayer {
                                val s = 1f + halo * 0.45f
                                scaleX = s
                                scaleY = s
                                alpha = (1f - halo) * 0.55f
                            }
                            .border(2.dp, MintGlow, CircleShape),
                    )
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(DeepGreenVelvet)
                        .border(1.dp, MintGlow.copy(alpha = 0.6f), CircleShape),
                ) {
                    // O escudo do ícone do app (o foreground do launcher, sem o fundo).
                    Image(
                        painter = painterResource(R.drawable.ic_launcher_foreground),
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                    )
                }
            }
            Text(
                text = stringResource(R.string.privacy_title).uppercase(),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.6.sp,
                color = MintGlow,
                modifier = Modifier
                    .padding(start = 6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MintGlow.copy(alpha = 0.12f))
                    .padding(horizontal = 10.dp, vertical = 3.dp),
            )
        }
        // A frase ocupa a largura toda do cartão, centralizada de verdade (fora da linha do
        // escudo). Mesma cor do selo "OBRIGATÓRIO" do cabeçalho da papelada.
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )
        Box(
            modifier = Modifier
                .padding(top = 16.dp, bottom = 4.dp)
                .fillMaxWidth()
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, MintGlow.copy(alpha = 0.4f), Color.Transparent),
                    ),
                ),
        )
        points.forEachIndexed { index, point ->
            Reveal(active, order = firstOrder + 1 + index, stepMs = 160L) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 12.dp),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MintGlow.copy(alpha = 0.12f)),
                    ) {
                        Icon(
                            imageVector = point.icon,
                            contentDescription = null,
                            tint = MintGlow,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Text(
                        text = stringResource(point.textRes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 14.dp),
                    )
                }
            }
        }
    }
}

/**
 * Margem negativa: o conteúdo se estende [start]/[top] para fora do padding do pai, ocupando
 * de fato esse espaço no layout (diferente de `offset`, que só desloca o desenho).
 */
private fun Modifier.bleed(start: Dp, top: Dp): Modifier = layout { measurable, constraints ->
    val s = start.roundToPx()
    val t = top.roundToPx()
    val placeable = measurable.measure(
        constraints.copy(
            minWidth = constraints.minWidth + s,
            maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth + s else constraints.maxWidth,
        ),
    )
    layout(placeable.width - s, (placeable.height - t).coerceAtLeast(0)) {
        placeable.place(-s, -t)
    }
}
