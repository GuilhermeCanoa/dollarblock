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
import androidx.compose.material.icons.filled.Block
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dollarblock.R
import com.dollarblock.core.designsystem.DollarBlockTheme
import com.dollarblock.core.designsystem.components.MetricCard
import com.dollarblock.core.designsystem.components.PreviewBanner
import com.dollarblock.core.designsystem.components.ScreenHeader
import com.dollarblock.core.designsystem.components.SectionHeader

/**
 * Statistics — uso por período com gráfico simples e resumo.
 * No E0.5 usa dados mock; o E7 substitui por agregações reais de DailyUsage.
 */
@Composable
fun StatisticsScreen(modifier: Modifier = Modifier) {
    var period by remember { mutableStateOf(StatPeriod.WEEKLY) }
    val data = previewData.getValue(period)

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
        PreviewBanner()

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            StatPeriod.entries.forEachIndexed { index, entry ->
                SegmentedButton(
                    selected = period == entry,
                    onClick = { period = entry },
                    shape = SegmentedButtonDefaults.itemShape(index, StatPeriod.entries.size),
                ) {
                    Text(stringResource(entry.labelRes))
                }
            }
        }

        SectionHeader(text = stringResource(R.string.stat_usage_overview))
        UsageChartCard(values = data.values, labels = data.labels)

        MetricCard(
            title = stringResource(R.string.stat_most_used),
            value = data.mostUsed,
            icon = Icons.Filled.EmojiEvents,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard(
                title = stringResource(R.string.stat_total_time),
                value = data.totalTime,
                icon = Icons.Filled.Schedule,
                modifier = Modifier.weight(1f),
            )
            MetricCard(
                title = stringResource(R.string.stat_blocks),
                value = data.blocks.toString(),
                icon = Icons.Filled.Block,
                modifier = Modifier.weight(1f),
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
                    // trilho de fundo
                    drawRoundRect(
                        color = trackColor,
                        topLeft = Offset(left, 0f),
                        size = Size(barWidth, size.height),
                        cornerRadius = radius,
                    )
                    // barra
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
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

private enum class StatPeriod(@StringRes val labelRes: Int) {
    DAILY(R.string.stat_period_daily),
    WEEKLY(R.string.stat_period_weekly),
    MONTHLY(R.string.stat_period_monthly),
}

private data class StatData(
    val values: List<Float>,
    val labels: List<String>,
    val totalTime: String,
    val mostUsed: String,
    val blocks: Int,
)

private val previewData: Map<StatPeriod, StatData> = mapOf(
    StatPeriod.DAILY to StatData(
        values = listOf(10f, 25f, 18f, 42f, 30f, 55f),
        labels = listOf("0h", "4h", "8h", "12h", "16h", "20h"),
        totalTime = "3h 10m",
        mostUsed = "Instagram",
        blocks = 2,
    ),
    StatPeriod.WEEKLY to StatData(
        values = listOf(45f, 60f, 38f, 72f, 90f, 120f, 80f),
        labels = listOf("Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom"),
        totalTime = "8h 25m",
        mostUsed = "TikTok",
        blocks = 9,
    ),
    StatPeriod.MONTHLY to StatData(
        values = listOf(320f, 410f, 280f, 500f),
        labels = listOf("Sem 1", "Sem 2", "Sem 3", "Sem 4"),
        totalTime = "25h 10m",
        mostUsed = "YouTube",
        blocks = 31,
    ),
)

@Preview(showBackground = true)
@Composable
private fun StatisticsScreenPreview() {
    DollarBlockTheme {
        StatisticsScreen()
    }
}
