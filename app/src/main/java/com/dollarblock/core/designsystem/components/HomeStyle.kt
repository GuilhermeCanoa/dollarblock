package com.dollarblock.core.designsystem.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dollarblock.core.designsystem.AlertAmber
import com.dollarblock.core.designsystem.BlockingRed
import com.dollarblock.core.designsystem.EmeraldPremium
import com.dollarblock.core.designsystem.InterFamily
import com.dollarblock.core.designsystem.JakartaFamily
import com.dollarblock.core.designsystem.MintGlow

/*
 * Linguagem visual da Home (redesenho de out/2026), compartilhada com o Extrato (E24):
 * paleta clara/escura com o brilho do topo, os tiles de atalho e o ícone de app.
 */

// ── Paleta ────────────────────────────────────────────────────────────────────────────────

internal data class TileColors(val background: Color, val border: Color, val title: Color, val value: Color)

/** Cores do mockup (escuro) com o equivalente no tema claro. */
internal data class HomePalette(
    val dark: Boolean,
    val background: Color,
    val text: Color,
    val muted: Color,
    val heroLabel: Color,
    val heroEquiv: Color,
    val track: Color,
    val divider: Color,
    val chip: Color,
    val triesTile: TileColors,
    val nextTile: TileColors,
    val walletBg: Color,
    val walletBorder: Color,
    val paidTile: TileColors,
    val savedTile: TileColors,
    val fieldBg: Color,
    val emerald: Color,
) {
    /**
     * Brilho verde (marca) no topo, atrás da conta. [extendUp] estende o desenho para cima,
     * por baixo da barra de status, para a tela fundir com o fundo sem corte no topo.
     */
    fun DrawScope.drawHeroGlow(centerY: Float, radius: Float, extendUp: Float) {
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(EmeraldPremium.copy(alpha = if (dark) 0.22f else 0.14f), Color.Transparent),
                center = Offset(size.width / 2, centerY),
                radius = radius,
            ),
            topLeft = Offset(0f, -extendUp),
            size = Size(size.width, size.height + extendUp),
        )
    }
}

@Composable
internal fun homePalette(): HomePalette {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminance() < 0.5f
    return if (dark) {
        HomePalette(
            dark = true,
            background = scheme.background,
            text = Color.White,
            muted = scheme.onSurfaceVariant,
            heroLabel = MintGlow,
            heroEquiv = Color(0xFFD3E6DF),
            track = Color.White.copy(alpha = 0.08f),
            divider = Color.White.copy(alpha = 0.06f),
            chip = Color.White.copy(alpha = 0.06f),
            triesTile = TileColors(Color(0xFF3A2E10), AlertAmber.copy(alpha = 0.25f), Color(0xFFFFD98A), Color.White),
            nextTile = TileColors(Color(0xFF0F3A3A), MintGlow.copy(alpha = 0.22f), Color(0xFF8FF3E2), Color.White),
            walletBg = Color.White.copy(alpha = 0.04f),
            walletBorder = MintGlow.copy(alpha = 0.10f),
            paidTile = TileColors(BlockingRed.copy(alpha = 0.10f), Color.Transparent, Color(0xFFFFB4AB), Color.White),
            savedTile = TileColors(EmeraldPremium.copy(alpha = 0.10f), Color.Transparent, Color(0xFF8CF5BB), Color.White),
            fieldBg = scheme.background.copy(alpha = 0.6f),
            emerald = EmeraldPremium,
        )
    } else {
        val ink = scheme.onSurface
        HomePalette(
            dark = false,
            background = scheme.background,
            text = ink,
            muted = scheme.onSurfaceVariant,
            heroLabel = scheme.primary,
            heroEquiv = Color(0xFF3E534B),
            track = ink.copy(alpha = 0.08f),
            divider = ink.copy(alpha = 0.08f),
            chip = ink.copy(alpha = 0.06f),
            triesTile = TileColors(Color(0xFFFFF1CC), AlertAmber.copy(alpha = 0.6f), Color(0xFF7A5300), ink),
            nextTile = TileColors(Color(0xFFD9F7F0), MintGlow.copy(alpha = 0.8f), Color(0xFF0B5C50), ink),
            walletBg = scheme.surface,
            walletBorder = scheme.outlineVariant,
            paidTile = TileColors(BlockingRed.copy(alpha = 0.10f), Color.Transparent, Color(0xFFB3261E), ink),
            savedTile = TileColors(EmeraldPremium.copy(alpha = 0.14f), Color.Transparent, Color(0xFF00693A), ink),
            fieldBg = scheme.surfaceVariant.copy(alpha = 0.5f),
            emerald = scheme.primary,
        )
    }
}

internal val Mono = FontFamily.Monospace

// ── Atalhos ───────────────────────────────────────────────────────────────────────────────

@Composable
internal fun StatTile(
    title: String,
    value: String?,
    /** Mostrado no lugar do número quando [value] é null (ex.: nenhum limite ainda). */
    emptyText: String? = null,
    unit: String?,
    icon: ImageBitmap?,
    colors: TileColors,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier = modifier
            .height(104.dp)
            .clip(shape)
            .background(colors.background)
            .border(1.dp, colors.border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = colors.title)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            if (value == null) {
                Text(
                    emptyText.orEmpty(),
                    fontFamily = InterFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = colors.value.copy(alpha = 0.8f),
                )
                return@Row
            }
            Text(
                text = buildAnnotatedString {
                    append(value)
                    if (unit != null) {
                        withStyle(SpanStyle(fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, letterSpacing = 0.sp)) {
                            append(" $unit")
                        }
                    }
                },
                fontFamily = JakartaFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 28.sp,
                letterSpacing = (-0.5).sp,
                color = colors.value,
                style = TextStyle(fontFeatureSettings = "tnum"),
            )
            if (icon != null) AppIcon(icon, size = 26, corner = 8)
        }
    }
}

@Composable
internal fun AppIcon(icon: ImageBitmap?, size: Int, corner: Int) {
    val shape = RoundedCornerShape(corner.dp)
    if (icon != null) {
        Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(size.dp).clip(shape))
    } else {
        Box(Modifier.size(size.dp).clip(shape).background(MaterialTheme.colorScheme.surfaceVariant))
    }
}
