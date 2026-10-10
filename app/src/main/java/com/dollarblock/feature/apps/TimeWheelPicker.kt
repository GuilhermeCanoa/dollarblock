package com.dollarblock.feature.apps

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/** Teto do limite diário: o dia só tem 24 horas. */
internal const val MAX_DAILY_LIMIT_MINUTES = 24 * 60

private val ItemHeight = 44.dp

/**
 * Seletor de duração em rolo: uma roda de horas (0–24) e outra de minutos (0–59). Em 24h a
 * roda de minutos só tem o 0 — o total nunca passa de [MAX_DAILY_LIMIT_MINUTES].
 */
@Composable
internal fun DurationWheelPicker(
    totalMinutes: Int,
    onTotalMinutesChange: (Int) -> Unit,
    hoursLabel: String,
    minutesLabel: String,
    modifier: Modifier = Modifier,
) {
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    val minuteCount = if (hours >= 24) 1 else 60

    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        // Faixa de seleção atrás das rodas.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(ItemHeight)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Wheel(
                count = 25,
                selected = hours,
                onSelected = { h ->
                    val m = if (h >= 24) 0 else minutes
                    onTotalMinutesChange(h * 60 + m)
                },
                unit = hoursLabel,
            )
            Wheel(
                count = minuteCount,
                selected = minutes.coerceAtMost(minuteCount - 1),
                onSelected = { m -> onTotalMinutesChange(hours * 60 + m) },
                unit = minutesLabel,
            )
        }
    }
}

@Composable
private fun Wheel(
    count: Int,
    selected: Int,
    onSelected: (Int) -> Unit,
    unit: String,
    modifier: Modifier = Modifier,
) {
    val state = rememberLazyListState(initialFirstVisibleItemIndex = selected)
    val itemPx = with(LocalDensity.current) { ItemHeight.toPx() }

    // Item central = o primeiro visível (+1 se já passou da metade). O padding de uma linha
    // acima/abaixo deixa o item selecionado no meio da roda de 3 linhas.
    val centered by remember(state, itemPx) {
        androidx.compose.runtime.derivedStateOf {
            val extra = if (state.firstVisibleItemScrollOffset > itemPx / 2) 1 else 0
            (state.firstVisibleItemIndex + extra).coerceIn(0, count - 1)
        }
    }

    // Roda → estado externo, só quando o rolo assenta (evita brigar com o gesto).
    LaunchedEffect(state, count) {
        androidx.compose.runtime.snapshotFlow { state.isScrollInProgress to centered }
            .collect { (scrolling, value) -> if (!scrolling && value != selected) onSelected(value) }
    }
    // Estado externo → roda (ex.: horas viraram 24 e os minutos precisam voltar a 0).
    LaunchedEffect(selected, count) {
        if (!state.isScrollInProgress && centered != selected) state.scrollToItem(selected)
    }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        LazyColumn(
            state = state,
            flingBehavior = rememberSnapFlingBehavior(lazyListState = state),
            contentPadding = PaddingValues(vertical = ItemHeight),
            modifier = Modifier
                .width(64.dp)
                .height(ItemHeight * 3),
        ) {
            items(count) { index ->
                Box(
                    modifier = Modifier
                        .height(ItemHeight)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    val isCenter = index == centered
                    Text(
                        text = index.toString(),
                        style = if (isCenter) MaterialTheme.typography.headlineSmall
                        else MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.alpha(if (isCenter) 1f else 0.4f),
                    )
                }
            }
        }
        Text(
            text = unit,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
