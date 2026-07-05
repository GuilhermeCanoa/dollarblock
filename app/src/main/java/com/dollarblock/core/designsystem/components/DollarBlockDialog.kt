package com.dollarblock.core.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.dollarblock.core.designsystem.DollarBlockTheme

/**
 * Diálogo padrão do DollarBlock — regra da casa: **nenhum** pop-up genérico do Android.
 * Todo aviso/confirmação/formulário em janela usa este componente: superfície de vidro
 * com stroke Mint Glow, overline mono estilo recibo ("DOLLARBLOCK · AVISO") e divisor
 * tracejado, ecoando a estética da fatura da tela de bloqueio.
 *
 * - [body] cobre o caso comum de texto; [content] injeta campos/listas customizadas.
 * - [confirmText]/[onConfirm] renderizam a ação primária; [confirmDestructive] a pinta
 *   com a cor de penalidade (exclusões, sabotagens).
 * - [dismissText] renderiza a ação secundária como texto.
 */
@Composable
fun DollarBlockDialog(
    onDismissRequest: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    overline: String = "DOLLARBLOCK",
    body: String? = null,
    confirmText: String? = null,
    onConfirm: () -> Unit = {},
    confirmEnabled: Boolean = true,
    confirmDestructive: Boolean = false,
    dismissText: String? = null,
    onDismiss: (() -> Unit)? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(24.dp)
    Dialog(onDismissRequest = onDismissRequest) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .glow(DollarBlockTheme.colors.glow, shape, radius = 24.dp, alpha = 0.35f)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, DollarBlockTheme.colors.glow.copy(alpha = 0.35f), shape)
                .padding(horizontal = 24.dp, vertical = 22.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = overline.uppercase(),
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                letterSpacing = 2.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(12.dp))
            DialogDashedDivider(DollarBlockTheme.colors.glow.copy(alpha = 0.3f))
            Spacer(Modifier.height(14.dp))
            if (body != null) {
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (content != null) {
                if (body != null) Spacer(Modifier.height(12.dp))
                content()
            }
            Spacer(Modifier.height(20.dp))
            if (confirmText != null) {
                if (confirmDestructive) {
                    PenaltyButton(
                        text = confirmText,
                        onClick = onConfirm,
                        enabled = confirmEnabled,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    PrimaryActionButton(
                        text = confirmText,
                        onClick = onConfirm,
                        enabled = confirmEnabled,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            if (dismissText != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss ?: onDismissRequest) {
                        Text(
                            text = dismissText,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DialogDashedDivider(color: Color, modifier: Modifier = Modifier) {
    Canvas(
        modifier
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
