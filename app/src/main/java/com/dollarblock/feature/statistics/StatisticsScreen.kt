package com.dollarblock.feature.statistics

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dollarblock.R
import com.dollarblock.core.designsystem.DollarBlockTheme
import com.dollarblock.core.designsystem.components.MetricCard
import com.dollarblock.core.designsystem.components.ScreenHeader
import com.dollarblock.core.designsystem.components.SectionHeader

@Composable
fun StatisticsScreen(
    modifier: Modifier = Modifier,
    viewModel: StatisticsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val period by viewModel.period.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenHeader(
            title = stringResource(R.string.statistics_title),
            subtitle = stringResource(R.string.statistics_subtitle),
        )

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            StatPeriod.entries.forEachIndexed { index, entry ->
                SegmentedButton(
                    selected = period == entry,
                    onClick = { viewModel.period.value = entry },
                    shape = SegmentedButtonDefaults.itemShape(index, StatPeriod.entries.size),
                ) {
                    Text(stringResource(entry.labelRes))
                }
            }
        }

        SectionHeader(text = stringResource(R.string.stat_usage_overview))

        if (uiState.chartValues.isEmpty()) {
            // Diário sem nenhum app monitorado ainda
            EmptyChartCard()
        } else {
            // Barras com altura zero quando sem uso — preenchem de baixo para cima conforme o uso cresce
            UsageChartCard(values = uiState.chartValues, labels = uiState.chartLabels)
        }

        MetricCard(
            title = stringResource(R.string.stat_most_used),
            value = uiState.mostUsed,
            icon = Icons.Filled.EmojiEvents,
            modifier = Modifier.fillMaxWidth(),
        )
        MetricCard(
            title = stringResource(uiState.period.timeSpentRes),
            value = uiState.timeSpent,
            icon = Icons.Filled.Schedule,
            modifier = Modifier.fillMaxWidth(),
        )
        uiState.moneyLost?.let { lost ->
            MetricCard(
                title = stringResource(uiState.period.moneyLostRes!!),
                value = formatReais(lost),
                icon = Icons.Filled.AttachMoney,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun UsageChartCard(
    values: List<Float>,
    labels: List<String>,
    modifier: Modifier = Modifier,
) {
    val barColor = MaterialTheme.colorScheme.primary
    val highlightColor = DollarBlockTheme.colors.success
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            val maxValue = (values.maxOrNull() ?: 1f).coerceAtLeast(1f)
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
            ) {
                val slot = size.width / values.size
                val barWidth = slot * 0.5f
                val radius = CornerRadius(barWidth / 2, barWidth / 2)
                values.forEachIndexed { index, value ->
                    val barHeight = (value / maxValue) * size.height
                    val left = index * slot + (slot - barWidth) / 2f
                    drawRoundRect(
                        color = trackColor,
                        topLeft = Offset(left, 0f),
                        size = Size(barWidth, size.height),
                        cornerRadius = radius,
                    )
                    drawRoundRect(
                        color = if (value >= maxValue) highlightColor else barColor,
                        topLeft = Offset(left, size.height - barHeight),
                        size = Size(barWidth, barHeight),
                        cornerRadius = radius,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                labels.forEach { label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyChartCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Text(
            text = stringResource(R.string.stat_no_data),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
        )
    }
}

private val StatPeriod.labelRes: Int
    @StringRes get() = when (this) {
        StatPeriod.DAILY -> R.string.stat_period_daily
        StatPeriod.WEEKLY -> R.string.stat_period_weekly
        StatPeriod.MONTHLY -> R.string.stat_period_monthly
    }

private val StatPeriod.timeSpentRes: Int
    @StringRes get() = when (this) {
        StatPeriod.DAILY -> R.string.stat_time_spent_daily
        StatPeriod.WEEKLY -> R.string.stat_time_spent_weekly
        StatPeriod.MONTHLY -> R.string.stat_time_spent_monthly
    }

private val StatPeriod.moneyLostRes: Int?
    @StringRes get() = when (this) {
        StatPeriod.DAILY -> null
        StatPeriod.WEEKLY -> R.string.stat_money_lost_week
        StatPeriod.MONTHLY -> R.string.stat_money_lost_month
    }

private fun formatReais(value: Double): String =
    "R$ %,.2f".format(value).replace(',', 'X').replace('.', ',').replace('X', '.')

@Preview(showBackground = true)
@Composable
private fun StatisticsScreenPreview() {
    DollarBlockTheme {
        StatisticsScreen()
    }
}
