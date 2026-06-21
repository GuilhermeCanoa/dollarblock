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
import androidx.compose.ui.graphics.Color

/**
 * Cores semânticas do DollarBlock que não existem no Material 3 padrão
 * (sucesso, alerta e penalidade). Expostas via [DollarBlockTheme.colors].
 */
@Immutable
data class DollarBlockColors(
    val success: Color,
    val alert: Color,
    val penalty: Color,
    val onSuccess: Color,
    val onAlert: Color,
    val onPenalty: Color,
)

private val LightExtraColors = DollarBlockColors(
    success = SuccessGreen,
    alert = AlertYellow,
    penalty = PenaltyRed,
    onSuccess = NeutralWhite,
    onAlert = NeutralDarkest,
    onPenalty = NeutralWhite,
)

private val DarkExtraColors = LightExtraColors

private val LocalDollarBlockColors = staticCompositionLocalOf {
    LightExtraColors
}

private val LightColorScheme = lightColorScheme(
    primary = DollarGreen,
    onPrimary = NeutralWhite,
    primaryContainer = DollarGreenContainer,
    onPrimaryContainer = OnDollarGreenContainer,
    secondary = DollarGreenDark,
    onSecondary = NeutralWhite,
    secondaryContainer = DollarGreenContainer,
    onSecondaryContainer = OnDollarGreenContainer,
    tertiary = SuccessGreen,
    onTertiary = NeutralWhite,
    error = PenaltyRed,
    onError = NeutralWhite,
    background = NeutralLight,
    onBackground = NeutralDarkest,
    surface = NeutralWhite,
    onSurface = NeutralDark,
    surfaceVariant = NeutralLight,
    onSurfaceVariant = NeutralGray,
    outline = NeutralGray,
)

private val DarkColorScheme = darkColorScheme(
    primary = SuccessGreen,
    onPrimary = NeutralDarkest,
    primaryContainer = DollarGreenDark,
    onPrimaryContainer = NeutralWhite,
    secondary = DollarGreen,
    onSecondary = NeutralWhite,
    secondaryContainer = DollarGreenDark,
    onSecondaryContainer = NeutralWhite,
    tertiary = SuccessGreen,
    onTertiary = NeutralDarkest,
    error = PenaltyRed,
    onError = NeutralWhite,
    background = NeutralDarkest,
    onBackground = OnSurfaceDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = NeutralDark,
    onSurfaceVariant = NeutralGray,
    outline = NeutralGray,
)

/**
 * Tema raiz do DollarBlock. Aplica o color scheme do Material 3 (claro/escuro
 * conforme o sistema) e disponibiliza as cores semânticas extras.
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

/** Acesso conveniente às cores semânticas extras: `DollarBlockTheme.colors.success`. */
object DollarBlockTheme {
    val colors: DollarBlockColors
        @Composable
        @ReadOnlyComposable
        get() = LocalDollarBlockColors.current
}
