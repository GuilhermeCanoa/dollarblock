package com.dollarblock.feature.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dollarblock.R
import com.dollarblock.core.designsystem.DollarBlockTheme
import com.dollarblock.core.designsystem.TabularNumerals
import com.dollarblock.domain.model.PaymentMethod
import com.dollarblock.domain.model.RecentEvent
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Histórico completo de bloqueios e desbloqueios (E8), agrupado por dia.
 * Reaproveita o estilo de linha da Home; lê os eventos reais via [HistoryViewModel].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val events by viewModel.events.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.history_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        if (events.isEmpty()) {
            EmptyHistory(modifier = Modifier.padding(innerPadding))
        } else {
            HistoryList(events = events, modifier = Modifier.padding(innerPadding))
        }
    }
}

@Composable
private fun HistoryList(
    events: List<RecentEvent>,
    modifier: Modifier = Modifier,
) {
    // Agrupa por dia (epochDay), preservando a ordem decrescente já vinda do repositório.
    val grouped = events.groupBy { dayOf(it.timestamp) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp),
    ) {
        grouped.forEach { (epochDay, dayEvents) ->
            item(key = "header-$epochDay") {
                Text(
                    text = formatDayHeader(epochDay),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                )
            }
            items(
                items = dayEvents,
                key = { "${it.timestamp}-${it.appLabel}" },
            ) { event ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    ),
                    border = BorderStroke(1.dp, DollarBlockTheme.colors.glow.copy(alpha = 0.15f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    HistoryEventRow(event)
                }
            }
        }
    }
}

/**
 * Um evento do histórico é um lançamento de recibo: ícone + descrição em mono à
 * esquerda, valor (ou "—" para bloqueios sem cobrança) em numerais tabulares à direita.
 */
@Composable
private fun HistoryEventRow(event: RecentEvent) {
    val unlocked = event is RecentEvent.Unlocked
    val accent = if (unlocked) DollarBlockTheme.colors.success else MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
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
                text = event.appLabel,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = if (unlocked) {
                    val method = if ((event as RecentEvent.Unlocked).method == PaymentMethod.GOOGLE_PAY) {
                        stringResource(R.string.pay_method_google)
                    } else {
                        stringResource(R.string.pay_method_simulated)
                    }
                    "${stringResource(R.string.history_entry_paid)} · $method"
                } else {
                    stringResource(R.string.history_entry_locked)
                },
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = formatTime(event.timestamp),
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
        Spacer(Modifier.size(8.dp))
        Text(
            text = if (event is RecentEvent.Unlocked) "R$ ${event.amount}" else "—",
            style = TabularNumerals.copy(fontSize = 16.sp),
            color = accent,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun EmptyHistory(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.history_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

private fun dayOf(epochMillis: Long): Long =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()

private fun formatTime(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("HH:mm"))

private val dayHeaderFormatter =
    DateTimeFormatter.ofPattern("EEE, d 'de' MMM", Locale("pt", "BR"))

private fun formatDayHeader(epochDay: Long): String =
    LocalDate.ofEpochDay(epochDay)
        .format(dayHeaderFormatter)
        .replaceFirstChar { it.uppercase() }

@Preview(showBackground = true)
@Composable
private fun HistoryEventRowPreview() {
    DollarBlockTheme {
        Column {
            HistoryEventRow(RecentEvent.Blocked("YouTube", System.currentTimeMillis()))
            HistoryEventRow(
                RecentEvent.Unlocked(
                    appLabel = "Instagram",
                    timestamp = System.currentTimeMillis(),
                    amount = "4.99",
                    currency = "BRL",
                    method = PaymentMethod.GOOGLE_PAY,
                ),
            )
        }
    }
}
