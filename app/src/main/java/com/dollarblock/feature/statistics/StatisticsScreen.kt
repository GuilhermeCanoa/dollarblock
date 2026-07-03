package com.dollarblock.feature.statistics

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dollarblock.R
import com.dollarblock.core.designsystem.DollarBlockTheme
import com.dollarblock.core.designsystem.TabularNumerals
import com.dollarblock.core.designsystem.components.MetricCard
import com.dollarblock.core.designsystem.components.ScreenHeader
import com.dollarblock.core.designsystem.components.SectionHeader
import kotlin.math.roundToInt

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

        if (uiState.topApps.isNotEmpty()) {
            DonutChartCard(
                topApps = uiState.topApps,
                totalTime = uiState.totalPeriodTime,
            )
        }

        if (uiState.chartLines.isNotEmpty() && uiState.chartXLabels.isNotEmpty()) {
            LineChartCard(
                lines = uiState.chartLines,
                xLabels = uiState.chartXLabels,
            )
        }

        if (uiState.bestDay != null || uiState.worstDay != null) {
            StatementCard(bestDay = uiState.bestDay, worstDay = uiState.worstDay)
        }

        uiState.moneyLost?.let { lost ->
            MetricCard(
                title = stringResource(uiState.period.moneyLostRes!!),
                value = formatReais(lost),
                icon = Icons.Filled.AttachMoney,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        MetricCard(
            title = stringResource(uiState.period.timeSpentRes),
            value = uiState.timeSpent,
            icon = Icons.Filled.Schedule,
            modifier = Modifier.fillMaxWidth(),
        )
        MetricCard(
            title = stringResource(R.string.stat_most_used),
            value = uiState.mostUsed,
            icon = Icons.Filled.EmojiEvents,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun DonutChartCard(
    topApps: List<TopAppEntry>,
    totalTime: String,
    modifier: Modifier = Modifier,
) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        ),
        border = BorderStroke(1.dp, DollarBlockTheme.colors.glow.copy(alpha = 0.15f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Top Apps",
                style = MaterialTheme.typography.titleSmall,
                color = onSurface,
            )

            // Donut + total time centered
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                val strokeWidth = 28.dp
                Canvas(modifier = Modifier.size(180.dp)) {
                    val sw = strokeWidth.toPx()
                    val radius = (size.minDimension - sw) / 2f
                    val topLeft = Offset((size.width - radius * 2) / 2f, (size.height - radius * 2) / 2f)
                    val arcSize = Size(radius * 2, radius * 2)
                    var startAngle = -90f

                    // Background track
                    drawArc(
                        color = trackColor,
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = sw, cap = StrokeCap.Butt),
                    )

                    topApps.forEachIndexed { idx, app ->
                        val sweep = 360f * app.percentage
                        drawArc(
                            color = chartPalette[idx % chartPalette.size],
                            startAngle = startAngle,
                            sweepAngle = sweep,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = sw, cap = StrokeCap.Butt),
                        )
                        startAngle += sweep
                    }
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Total",
                        style = MaterialTheme.typography.labelSmall,
                        color = onSurfaceVariant,
                    )
                    Text(
                        text = totalTime,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = onSurface,
                    )
                }
            }

            // App list
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                topApps.forEachIndexed { idx, app ->
                    val color = chartPalette[idx % chartPalette.size]
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        // Icon or colored placeholder
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(color.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (app.icon != null) {
                                Image(
                                    bitmap = app.icon,
                                    contentDescription = app.appName,
                                    modifier = Modifier.size(36.dp).clip(CircleShape),
                                    contentScale = ContentScale.Crop,
                                )
                            } else {
                                Text(
                                    text = app.appName.first().uppercaseChar().toString(),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = color,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }

                        Spacer(Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = app.appName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = "${(app.percentage * 100).roundToInt()}%",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = onSurfaceVariant,
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { app.percentage },
                                modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                                color = color,
                                trackColor = trackColor,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Extrato: melhor e pior dia do período lado a lado, numerais tabulares
 * reforçando a estética de painel financeiro (styleguide — voz "gerente do
 * banco de tempo").
 */
@Composable
private fun StatementCard(
    bestDay: DayHighlight?,
    worstDay: DayHighlight?,
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
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.stat_statement_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DayHighlightEntry(
                    label = stringResource(R.string.stat_best_day),
                    caption = stringResource(R.string.stat_best_day_caption),
                    highlight = bestDay,
                    accent = DollarBlockTheme.colors.success,
                    modifier = Modifier.weight(1f),
                )
                DayHighlightEntry(
                    label = stringResource(R.string.stat_worst_day),
                    caption = stringResource(R.string.stat_worst_day_caption),
                    highlight = worstDay,
                    accent = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun DayHighlightEntry(
    label: String,
    caption: String,
    highlight: DayHighlight?,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(accent.copy(alpha = 0.08f))
            .padding(12.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = highlight?.let { formatReais(it.amount) } ?: "—",
            style = TabularNumerals.copy(fontSize = 20.sp),
            color = accent,
        )
        Text(
            text = highlight?.label ?: caption,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// Paleta de gráficos alinhada à marca: emerald, mint glow e tons análogos de
// verde/teal, com âmbar e vermelho reservados para realces semânticos.
private val chartPalette = listOf(
    Color(0xFF00E676), // Emerald Premium
    Color(0xFF64FFDA), // Mint Glow
    Color(0xFF00A86B), // Emerald médio
    Color(0xFF2DD4BF), // Teal
    Color(0xFFA7F432), // Lime
    Color(0xFFFFC24B), // Amber
    Color(0xFF4ECDC4), // Aqua
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LineChartCard(
    lines: List<AppChartLine>,
    xLabels: List<String>,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(true) }

    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val bgColor = MaterialTheme.colorScheme.surface

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor.copy(alpha = 0.92f)),
        border = BorderStroke(1.dp, DollarBlockTheme.colors.glow.copy(alpha = 0.15f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.stat_usage_overview),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (expanded) "Recolher" else "Expandir",
                        tint = labelColor,
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Column {
                    val pointCount = xLabels.size
                    val maxVal = lines.flatMap { it.points }.maxOrNull()?.coerceAtLeast(1f) ?: 1f

                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                    ) {
                        val leftPad = 44.dp.toPx()
                        val chartW = size.width - leftPad
                        val chartH = size.height

                        // Y-axis labels and grid lines at 0%, 50%, 100%
                        val gridFractions = listOf(1f, 0.5f, 0f)
                        gridFractions.forEach { fraction ->
                            val y = chartH * (1f - fraction)
                            drawLine(
                                color = gridColor,
                                start = Offset(leftPad, y),
                                end = Offset(size.width, y),
                                strokeWidth = 1.dp.toPx(),
                            )
                            drawIntoCanvas { canvas ->
                                val paint = android.graphics.Paint().apply {
                                    isAntiAlias = true
                                    textSize = 9.sp.toPx()
                                    color = android.graphics.Color.argb(
                                        (labelColor.alpha * 255).toInt(),
                                        (labelColor.red * 255).toInt(),
                                        (labelColor.green * 255).toInt(),
                                        (labelColor.blue * 255).toInt(),
                                    )
                                    textAlign = android.graphics.Paint.Align.RIGHT
                                }
                                val label = formatMsShort(maxVal * fraction)
                                canvas.nativeCanvas.drawText(
                                    label,
                                    leftPad - 6.dp.toPx(),
                                    y + paint.textSize / 3f,
                                    paint,
                                )
                            }
                        }

                        // Lines per app
                        lines.forEachIndexed { idx, appLine ->
                            val color = chartPalette[idx % chartPalette.size]
                            if (pointCount < 2) return@forEachIndexed

                            val path = Path()
                            appLine.points.forEachIndexed { i, value ->
                                val x = leftPad + (i.toFloat() / (pointCount - 1)) * chartW
                                val y = chartH * (1f - value / maxVal)
                                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                            }
                            drawPath(
                                path = path,
                                color = color,
                                style = Stroke(
                                    width = 2.dp.toPx(),
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round,
                                ),
                            )

                            // Dots at each point
                            appLine.points.forEachIndexed { i, value ->
                                val x = leftPad + (i.toFloat() / (pointCount - 1)) * chartW
                                val y = chartH * (1f - value / maxVal)
                                drawCircle(color = color, radius = 4.dp.toPx(), center = Offset(x, y))
                                drawCircle(color = Color.White, radius = 2.dp.toPx(), center = Offset(x, y))
                            }
                        }
                    }

                    // X-axis labels
                    Row(modifier = Modifier.fillMaxWidth().padding(start = 44.dp)) {
                        xLabels.forEach { label ->
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = labelColor,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // Legend
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        lines.forEachIndexed { idx, appLine ->
                            val color = chartPalette[idx % chartPalette.size]
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    modifier = Modifier.size(8.dp),
                                    shape = CircleShape,
                                    color = color,
                                ) {}
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = appLine.appName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = labelColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
}

private fun formatMsShort(ms: Float): String {
    val totalMinutes = (ms / 60_000).toLong()
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}h${if (minutes > 0) "${minutes}m" else ""}" else "${minutes}m"
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
    // Locale.US fixa "1,234.56"; o swap converte para "1.234,56". Sem locale explícito,
    // um device pt-BR já formata com vírgula e o swap inverte errado.
    "R$ %,.2f".format(java.util.Locale.US, value)
        .replace(',', 'X').replace('.', ',').replace('X', '.')

@Preview(showBackground = true)
@Composable
private fun StatisticsScreenPreview() {
    DollarBlockTheme {
        StatisticsScreen()
    }
}
