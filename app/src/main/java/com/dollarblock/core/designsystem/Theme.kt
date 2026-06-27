package com.dollarblock.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Cores de marca do DollarBlock que vão além do Material 3 padrão: o brilho neon
 * (Mint Glow), o texto secundário "muted", as semânticas (sucesso/alerta/bloqueio)
 * e o gradiente linear de marca (135°). Expostas via [DollarBlockTheme.colors].
 */
@Immutable
data class DollarBlockColors(
    val success: Color,
    val alert: Color,
    val penalty: Color,
    val onSuccess: Color,
    val onAlert: Color,
    val onPenalty: Color,
    /** Brilho neon para bordas/halos (Mint Glow). */
    val glow: Color,
    /** Texto secundário esmaecido. */
    val muted: Color,
    /** Gradiente de marca a 135°: Velvet → Emerald → Mint Glow. */
    val brandGradient: Brush,
    /** Cores cru do gradiente (para halos/realces pontuais). */
    val gradientStops: List<Color>,
)

private val brandGradientColors = listOf(DeepGreenVelvet, EmeraldMid, MintGlow)

private val DarkExtraColors = DollarBlockColors(
    success = EmeraldPremium,
    alert = AlertAmber,
    penalty = BlockingRed,
    onSuccess = DeepGreenVelvet,
    onAlert = DeepGreenVelvet,
    onPenalty = TextPrimary,
    glow = MintGlow,
    muted = TextMuted,
    brandGradient = Brush.linearGradient(brandGradientColors),
    gradientStops = brandGradientColors,
)

private val LightExtraColors = DarkExtraColors.copy(
    onSuccess = TextPrimary,
    onAlert = DeepGreenVelvet,
    muted = TextMutedLight,
)

private val LocalDollarBlockColors = staticCompositionLocalOf { DarkExtraColors }

private val DarkColorScheme = darkColorScheme(
    primary = EmeraldPremium,
    onPrimary = DeepGreenVelvet,
    primaryContainer = VelvetSurfaceHigh,
    onPrimaryContainer = MintGlow,
    secondary = MintGlow,
    onSecondary = DeepGreenVelvet,
    secondaryContainer = VelvetSurfaceVariant,
    onSecondaryContainer = MintGlow,
    tertiary = EmeraldMid,
    onTertiary = TextPrimary,
    error = BlockingRed,
    onError = TextPrimary,
    errorContainer = Color(0xFF4A1416),
    onErrorContainer = Color(0xFFFFD9D9),
    background = DeepGreenVelvet,
    onBackground = TextPrimary,
    surface = VelvetSurface,
    onSurface = TextPrimary,
    surfaceVariant = VelvetSurfaceVariant,
    onSurfaceVariant = TextMuted,
    outline = Color(0xFF2E5C4F),
    outlineVariant = Color(0xFF234A3E),
)

private val LightColorScheme = lightColorScheme(
    primary = EmeraldOnLight,
    onPrimary = TextPrimary,
    primaryContainer = EmeraldContainerLight,
    onPrimaryContainer = OnEmeraldContainerLight,
    secondary = EmeraldMid,
    onSecondary = TextPrimary,
    secondaryContainer = EmeraldContainerLight,
    onSecondaryContainer = OnEmeraldContainerLight,
    tertiary = EmeraldOnLight,
    onTertiary = TextPrimary,
    error = BlockingRed,
    onError = TextPrimary,
    background = LightBackground,
    onBackground = DeepGreenVelvet,
    surface = LightSurface,
    onSurface = Color(0xFF13231D),
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = TextMutedLight,
    outline = Color(0xFFAEC3B9),
    outlineVariant = Color(0xFFCBDDD4),
)

/**
 * Tema raiz do DollarBlock. Aplica o color scheme do Material 3 (claro/escuro
 * conforme o sistema — o escuro é o nativo da marca) e disponibiliza as cores
 * e o gradiente de marca extras via [DollarBlockTheme.colors].
 */
@Composable
fun DollarBlockTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val extraColors = if (darkTheme) DarkExtraColors else LightExtraColors

    CompositionLocalProvider(LocalDollarBlockColors provides extraColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = DollarBlockTypography,
            content = content,
        )
    }
}

/** Acesso conveniente às cores de marca extras: `DollarBlockTheme.colors.glow`. */
object DollarBlockTheme {
    val colors: DollarBlockColors
        @Composable
        @ReadOnlyComposable
        get() = LocalDollarBlockColors.current
}
