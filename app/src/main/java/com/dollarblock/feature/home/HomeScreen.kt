package com.dollarblock.feature.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dollarblock.R
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import com.dollarblock.core.designsystem.BlockingRed
import com.dollarblock.core.designsystem.DollarBlockTheme
import com.dollarblock.core.designsystem.NeutralWhite
import com.dollarblock.core.designsystem.TabularNumerals
import com.dollarblock.core.designsystem.components.MetricCard
import com.dollarblock.core.designsystem.components.glow
import com.dollarblock.core.designsystem.components.ScreenHeader
import com.dollarblock.core.designsystem.components.SectionHeader
import com.dollarblock.domain.model.PaymentMethod
import com.dollarblock.domain.model.RecentEvent
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onNavigateToApps: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenHeader(
            title = stringResource(R.string.home_title),
            subtitle = stringResource(R.string.msg_on_track),
        )

        MoneyLostHero(moneyLost = uiState.moneyLostToday)

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard(
                title = stringResource(R.string.home_currently_blocked),
                value = uiState.currentlyBlockedCount.toString(),
                icon = Icons.Filled.Lock,
                modifier = Modifier.weight(1f),
                onClick = onNavigateToApps,
            )
            MetricCard(
                title = stringResource(R.string.home_addiction_tracker),
                value = uiState.addictionAttempts.toString(),
                icon = Icons.Filled.Warning,
                modifier = Modifier.weight(1f),
            )
        }

        SectionHeader(text = stringResource(R.string.home_recent_events))
        if (uiState.recentEvents.isEmpty()) {
            EmptyEventsCard()
        } else {
            RecentEventsCard(events = uiState.recentEvents)
        }
    }
}

@Composable
private fun MoneyLostHero(
    moneyLost: Double?,
    modifier: Modifier = Modifier,
) {
    // O taxímetro: bloco-herói com count-up de odômetro. Verde (marca) quando o
    // dia está limpo; "sangra" vermelho quando há dinheiro sendo doado.
    val shape = RoundedCornerShape(24.dp)
    val burning = (moneyLost ?: 0.0) >= 0.01
    val gradient = Brush.linearGradient(
        colors = if (burning) {
            listOf(Color(0xFF2A0E12), Color(0xFF6E1D1D), BlockingRed)
        } else {
            DollarBlockTheme.colors.gradientStops
        },
        start = Offset(0f, 0f),
        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
    )
    val glowColor = if (burning) BlockingRed else DollarBlockTheme.colors.glow

    // Count-up de taxímetro: o valor sobe de 0 até o prejuízo ao entrar na tela.
    val animatedValue = remember { Animatable(0f) }
    LaunchedEffect(moneyLost) {
        animatedValue.animateTo(
            targetValue = (moneyLost ?: 0.0).toFloat(),
            animationSpec = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .glow(glowColor, shape, radius = 28.dp, alpha = 0.4f)
            .clip(shape)
            .background(gradient)
            .border(1.dp, glowColor.copy(alpha = 0.35f), shape),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.home_money_lost_today).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                letterSpacing = 1.5.sp,
                color = NeutralWhite.copy(alpha = 0.85f),
            )
            Text(
                text = if (moneyLost != null) formatReais(animatedValue.value.toDouble()) else "—",
                style = TabularNumerals,
                fontSize = 56.sp,
                fontWeight = FontWeight.ExtraBold,
                color = NeutralWhite,
                lineHeight = 62.sp,
            )
            val equivalence = moneyLost?.let { HomeMetrics.equivalence(it) }
            if (equivalence != null) {
                Text(
                    text = when (equivalence) {
                        is MoneyEquivalence.Pizzas ->
                            pluralStringResource(R.plurals.home_equiv_pizzas, equivalence.count, equivalence.count)
                        is MoneyEquivalence.Coffees ->
                            pluralStringResource(R.plurals.home_equiv_coffees, equivalence.count, equivalence.count)
                        is MoneyEquivalence.CoffeeFraction ->
                            stringResource(R.string.home_equiv_coffee_fraction, equivalence.percent)
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = NeutralWhite.copy(alpha = 0.9f),
                )
            }
            Text(
                text = if (moneyLost != null)
                    stringResource(R.string.home_money_lost_subtitle)
                else
                    stringResource(R.string.home_money_lost_no_data),
                style = MaterialTheme.typography.bodyMedium,
                color = NeutralWhite.copy(alpha = 0.75f),
            )
        }
    }
}

private fun formatReais(value: Double): String {
    return "R$ %,.2f".format(value).replace(',', 'X').replace('.', ',').replace('X', '.')
}

@Composable
private fun RecentEventsCard(
    events: List<RecentEvent>,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        ),
        border = BorderStroke(1.dp, DollarBlockTheme.colors.glow.copy(alpha = 0.15f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            events.forEach { event -> RecentEventRow(event) }
        }
    }
}

@Composable
private fun RecentEventRow(event: RecentEvent) {
    val unlocked = event is RecentEvent.Unlocked
    val accent = if (unlocked) DollarBlockTheme.colors.success else MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (unlocked) Icons.Filled.LockOpen else Icons.Filled.Lock,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(
                    if (unlocked) R.string.home_event_unlocked else R.string.home_event_blocked,
                    event.appLabel,
                ),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (event is RecentEvent.Unlocked) {
                val method = if (event.method == PaymentMethod.GOOGLE_PAY) {
                    stringResource(R.string.pay_method_google)
                } else {
                    stringResource(R.string.pay_method_simulated)
                }
                Text(
                    text = stringResource(R.string.home_event_unlock_detail, event.amount, method),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.size(8.dp))
        Text(
            text = formatEventTime(event.timestamp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun formatEventTime(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("HH:mm"))

@Composable
private fun EmptyEventsCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        ),
        border = BorderStroke(1.dp, DollarBlockTheme.colors.glow.copy(alpha = 0.15f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                tint = DollarBlockTheme.colors.success,
            )
            Spacer(Modifier.size(12.dp))
            Text(
                text = stringResource(R.string.home_no_events),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    DollarBlockTheme {
        HomeScreen()
    }
}
