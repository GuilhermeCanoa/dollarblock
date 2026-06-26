package com.dollarblock.feature.statistics

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dollarblock.data.apps.InstalledAppsProvider
import com.dollarblock.data.local.db.DailyUsageDao
import com.dollarblock.data.local.db.MonitoredAppDao
import com.dollarblock.feature.home.HomeMetrics
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject

enum class StatPeriod { DAILY, WEEKLY, MONTHLY }

data class AppChartLine(val appName: String, val points: List<Float>)

data class TopAppEntry(
    val appName: String,
    val icon: ImageBitmap?,
    val usedMillis: Long,
    val percentage: Float,
)

data class StatisticsUiState(
    val period: StatPeriod = StatPeriod.WEEKLY,
    val chartLines: List<AppChartLine> = emptyList(),
    val chartXLabels: List<String> = emptyList(),
    val topApps: List<TopAppEntry> = emptyList(),
    val totalPeriodTime: String = "—",
    val timeSpent: String = "—",
    val mostUsed: String = "—",
    /** null when period == DAILY (shown on Home instead). */
    val moneyLost: Double? = null,
)

@HiltViewModel
class StatisticsViewModel @Inject constructor(
    private val dailyUsageDao: DailyUsageDao,
    private val monitoredAppDao: MonitoredAppDao,
    private val installedAppsProvider: InstalledAppsProvider,
) : ViewModel() {

    private companion object {
        val REAIS_PER_MINUTE = HomeMetrics.REAIS_PER_MINUTE
    }

    val period = MutableStateFlow(StatPeriod.WEEKLY)
    private val _uiState = MutableStateFlow(StatisticsUiState())
    val uiState: StateFlow<StatisticsUiState> = _uiState

    private val iconCache = MutableStateFlow<Map<String, ImageBitmap?>>(emptyMap())

    init {
        period.onEach { resubscribe(it) }.launchIn(viewModelScope)
        // Load icons for monitored apps, caching them as they arrive
        viewModelScope.launch {
            monitoredAppDao.observeMonitored().collect { apps ->
                val missing = apps.map { it.packageName }.toSet() - iconCache.value.keys
                if (missing.isNotEmpty()) {
                    val newIcons = missing.associateWith { installedAppsProvider.getIconForPackage(it) }
                    iconCache.value = iconCache.value + newIcons
                }
            }
        }
    }

    private var dataJob: kotlinx.coroutines.Job? = null

    private fun resubscribe(p: StatPeriod) {
        dataJob?.cancel()
        val today = LocalDate.now(ZoneId.systemDefault())
        val todayEpoch = today.toEpochDay()
        val zone = ZoneId.systemDefault()

        val mondayEpoch = todayEpoch - (today.dayOfWeek.value - 1)
        val (startDay, endDay) = when (p) {
            StatPeriod.DAILY -> todayEpoch to todayEpoch
            StatPeriod.WEEKLY -> mondayEpoch to todayEpoch
            StatPeriod.MONTHLY -> (todayEpoch - 27) to todayEpoch
        }

        dataJob = combine(
            dailyUsageDao.observeRange(startDay, endDay),
            monitoredAppDao.observeMonitored(),
            iconCache,
        ) { usageRows, monitoredApps, icons ->

            val appNames = monitoredApps.associate { it.packageName to it.appName }

            // Baseline por app: subtrai uso pré-DollarBlock apenas no dia em que o app foi adicionado.
            val baselineByApp = monitoredApps.associate { app ->
                val createdDay = java.time.Instant.ofEpochMilli(app.createdAt)
                    .atZone(zone)
                    .toLocalDate()
                    .toEpochDay()
                app.packageName to (createdDay to app.usageBaselineMillis)
            }

            fun effectiveMillis(packageName: String, epochDay: Long, rawMillis: Long): Long {
                val (createdDay, baseline) = baselineByApp[packageName] ?: return rawMillis
                return if (epochDay == createdDay) (rawMillis - baseline).coerceAtLeast(0L) else rawMillis
            }

            // ── Métricas globais ────────────────────────────────────────────
            val totalMillis = usageRows.sumOf { effectiveMillis(it.packageName, it.epochDay, it.usedMillis) }
            val timeSpent = formatMillis(totalMillis)

            val mostUsedPkg = usageRows
                .groupBy { it.packageName }
                .mapValues { (pkg, rows) -> rows.sumOf { effectiveMillis(pkg, it.epochDay, it.usedMillis) } }
                .filter { it.value > 0 }
                .maxByOrNull { it.value }?.key
            val mostUsed = mostUsedPkg?.let { appNames[it] ?: it } ?: ""

            // ── Money lost (weekly/monthly only — daily is shown on Home) ───
            val moneyLost = if (p == StatPeriod.DAILY) null
            else (totalMillis / 60_000.0) * REAIS_PER_MINUTE

            // ── Top apps (donut chart) ──────────────────────────────────────
            val usageByPkg = usageRows
                .groupBy { it.packageName }
                .mapValues { (pkg, rows) -> rows.sumOf { effectiveMillis(pkg, it.epochDay, it.usedMillis) } }
                .filter { it.value > 0 }
            val sorted = usageByPkg.entries.sortedByDescending { it.value }
            val top5 = sorted.take(5)
            val othersMillis = sorted.drop(5).sumOf { it.value }
            val topApps = buildList {
                top5.forEach { (pkg, millis) ->
                    add(TopAppEntry(
                        appName = appNames[pkg] ?: pkg,
                        icon = icons[pkg],
                        usedMillis = millis,
                        percentage = if (totalMillis > 0) millis.toFloat() / totalMillis else 0f,
                    ))
                }
                if (othersMillis > 0) {
                    add(TopAppEntry(
                        appName = "Others",
                        icon = null,
                        usedMillis = othersMillis,
                        percentage = if (totalMillis > 0) othersMillis.toFloat() / totalMillis else 0f,
                    ))
                }
            }

            // ── Dados do gráfico (linha por app; DAILY não exibe gráfico) ───
            val (chartLines, chartXLabels) = when (p) {
                StatPeriod.DAILY -> emptyList<AppChartLine>() to emptyList()
                StatPeriod.WEEKLY -> buildWeeklyChartPerApp(usageRows, monitoredApps, appNames, ::effectiveMillis, todayEpoch)
                StatPeriod.MONTHLY -> buildMonthlyChartPerApp(usageRows, monitoredApps, appNames, ::effectiveMillis, todayEpoch)
            }

            StatisticsUiState(
                period = p,
                chartLines = chartLines,
                chartXLabels = chartXLabels,
                topApps = topApps,
                totalPeriodTime = timeSpent,
                timeSpent = timeSpent,
                mostUsed = mostUsed,
                moneyLost = moneyLost,
            )
        }.onEach { _uiState.value = it }.launchIn(viewModelScope)
    }

    // ── Chart builders ──────────────────────────────────────────────────────

    private fun buildWeeklyChartPerApp(
        rows: List<com.dollarblock.data.local.db.DailyUsageEntity>,
        monitoredApps: List<com.dollarblock.data.local.db.MonitoredAppEntity>,
        appNames: Map<String, String>,
        effective: (String, Long, Long) -> Long,
        todayEpoch: Long,
    ): Pair<List<AppChartLine>, List<String>> {
        if (monitoredApps.isEmpty()) return emptyList<AppChartLine>() to emptyList()
        val locale = Locale("pt", "BR")
        val today = LocalDate.ofEpochDay(todayEpoch)
        val mondayEpoch = todayEpoch - (today.dayOfWeek.value - 1)
        val epochs = (0..6).map { mondayEpoch + it }
        val xLabels = epochs.map { epoch ->
            LocalDate.ofEpochDay(epoch)
                .dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
                .replaceFirstChar { it.uppercase() }
        }
        val lines = monitoredApps.map { app ->
            val points = epochs.map { epoch ->
                rows.filter { it.packageName == app.packageName && it.epochDay == epoch }
                    .sumOf { effective(it.packageName, it.epochDay, it.usedMillis) }
                    .toFloat()
            }
            AppChartLine(appName = appNames[app.packageName] ?: app.packageName, points = points)
        }.filter { line -> line.points.any { it > 0f } }
        return lines to xLabels
    }

    private fun buildMonthlyChartPerApp(
        rows: List<com.dollarblock.data.local.db.DailyUsageEntity>,
        monitoredApps: List<com.dollarblock.data.local.db.MonitoredAppEntity>,
        appNames: Map<String, String>,
        effective: (String, Long, Long) -> Long,
        todayEpoch: Long,
    ): Pair<List<AppChartLine>, List<String>> {
        if (monitoredApps.isEmpty()) return emptyList<AppChartLine>() to emptyList()
        // Sem 1 (mais antiga) → Sem 4 (mais recente)
        val weekRanges = (3 downTo 0).mapIndexed { index, weekOffset ->
            val weekEnd = todayEpoch - weekOffset * 7
            val weekStart = weekEnd - 6
            Triple(index + 1, weekStart, weekEnd)
        }
        val xLabels = weekRanges.map { (num, _, _) -> "Sem $num" }
        val lines = monitoredApps.map { app ->
            val points = weekRanges.map { (_, weekStart, weekEnd) ->
                rows.filter { it.packageName == app.packageName && it.epochDay in weekStart..weekEnd }
                    .sumOf { effective(it.packageName, it.epochDay, it.usedMillis) }
                    .toFloat()
            }
            AppChartLine(appName = appNames[app.packageName] ?: app.packageName, points = points)
        }.filter { line -> line.points.any { it > 0f } }
        return lines to xLabels
    }

    // ── Formatação ──────────────────────────────────────────────────────────

    private fun formatMillis(millis: Long): String {
        val totalMinutes = millis / 60_000L
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            else -> "${minutes}m"
        }
    }
}
