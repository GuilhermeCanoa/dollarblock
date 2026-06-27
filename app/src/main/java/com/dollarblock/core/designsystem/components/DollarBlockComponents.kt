package com.dollarblock.core.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dollarblock.R
import com.dollarblock.core.designsystem.DollarBlockTheme

/**
 * Halo de brilho suave (Mint Glow) atrás de um componente, simulando a borda
 * iluminada / vidro fosco do styleguide (§4). Use em cards e botões de destaque.
 */
fun Modifier.glow(
    color: Color,
    shape: Shape,
    radius: Dp = 24.dp,
    alpha: Float = 0.35f,
): Modifier = this.shadow(
    elevation = radius,
    shape = shape,
    ambientColor = color.copy(alpha = alpha),
    spotColor = color.copy(alpha = alpha),
)

/**
 * Card de vidro fosco controlado: superfície levemente translúcida com stroke
 * fino do Mint Glow a ~15% (styleguide §4 — glassmorphism controlado).
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    contentPadding: Dp = 16.dp,
    glowColor: Color? = null,
    content: @Composable () -> Unit,
) {
    val glassModifier = if (glowColor != null) {
        modifier.glow(glowColor, shape, radius = 18.dp, alpha = 0.25f)
    } else modifier

    Card(
        modifier = glassModifier,
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        ),
        border = BorderStroke(1.dp, DollarBlockTheme.colors.glow.copy(alpha = 0.15f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Box(Modifier.padding(contentPadding)) { content() }
    }
}

/**
 * Cartão de métrica da Home/Statistics — vidro fosco com ícone em chip emerald
 * e número em destaque. Toque opcional.
 */
@Composable
fun MetricCard(
    title: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(20.dp)
    Card(
        onClick = onClick ?: {},
        enabled = onClick != null,
        modifier = modifier,
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        ),
        border = BorderStroke(1.dp, DollarBlockTheme.colors.glow.copy(alpha = 0.15f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/**
 * Botão primário do DollarBlock — pílula emerald com halo de brilho, para ações
 * afirmativas (Proteger, Continuar, Resgatar).
 */
@Composable
fun PrimaryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(16.dp)
    val enabledColor = MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .height(54.dp)
            .then(if (enabled) Modifier.glow(enabledColor, shape, radius = 20.dp, alpha = 0.4f) else Modifier)
            .clip(shape)
            .background(
                if (enabled) Brush.horizontalGradient(
                    listOf(enabledColor, DollarBlockTheme.colors.glow),
                ) else Brush.horizontalGradient(
                    listOf(
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                    ),
                ),
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Botão de penalidade — vermelho de bloqueio, usado no fluxo de desbloqueio (Unlock). */
@Composable
fun PenaltyButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .height(54.dp)
            .clip(shape)
            .background(DollarBlockTheme.colors.penalty)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = DollarBlockTheme.colors.onPenalty,
        )
    }
}

/** Cabeçalho de seção — Jakarta semibold, tom direto. */
@Composable
fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier.fillMaxWidth(),
    )
}

/** Emblema do escudo DollarBlock (asset de marca), com halo opcional. */
@Composable
fun BrandShield(
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    cornerRadius: Dp = 10.dp,
    glow: Boolean = true,
) {
    val shape = RoundedCornerShape(cornerRadius)
    Image(
        painter = painterResource(R.drawable.db_shield),
        contentDescription = null,
        modifier = modifier
            .size(size)
            .then(if (glow) Modifier.glow(DollarBlockTheme.colors.glow, shape, radius = size / 3, alpha = 0.5f) else Modifier)
            .clip(shape),
    )
}

/**
 * Cabeçalho de tela — emblema do escudo + título grande (Jakarta ExtraBold) e
 * subtítulo opcional no tom de voz da marca.
 */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    subtitleStyle: androidx.compose.ui.text.TextStyle? = null,
    subtitleColor: androidx.compose.ui.graphics.Color? = null,
    subtitleTextAlign: androidx.compose.ui.text.style.TextAlign? = null,
    showShield: Boolean = true,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showShield) {
                BrandShield(size = 34.dp)
                Spacer(Modifier.size(12.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = subtitleStyle ?: MaterialTheme.typography.bodyMedium,
                color = subtitleColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = subtitleTextAlign,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
