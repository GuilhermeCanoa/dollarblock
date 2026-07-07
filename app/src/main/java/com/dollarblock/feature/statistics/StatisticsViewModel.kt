package com.dollarblock.feature.statistics

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dollarblock.data.apps.InstalledAppsProvider
import com.dollarblock.data.local.db.DailyUsageDao
import com.dollarblock.data.local.db.MonitoredAppDao
import com.dollarblock.data.local.prefs.MoneyPreferences
import com.dollarblock.domain.model.MoneySettings
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

enum class StatPeriod { DAILY, WEEKLY, MONTHLY, TOTAL }

data class AppChartLine(val appName: String, val points: List<Float>)

data class TopAppEntry(
    val appName: String,
    val icon: ImageBitmap?,
    val usedMillis: Long,
    val percentage: Float,
)

data class DayHighlight(val label: String, val amount: Double)

/** Uma linha do detalhamento dia a dia exibido nos popups de Extrato/Prejuízo. */
data class DayBreakdownEntry(
    val label: String,
    val usedMillis: Long,
    val amount: Double,
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
    /** Melhor/pior dia do período — null quando DAILY (não há o que comparar). */
    val bestDay: DayHighlight? = null,
    val worstDay: DayHighlight? = null,
    /** Gasto dia a dia do período (popups de Extrato/Prejuízo) — vazio quando DAILY. */
    val dayBreakdown: List<DayBreakdownEntry> = emptyList(),
    /** Salário/moeda vigentes — a moeda decide como formatar os valores na tela. */
    val moneySettings: MoneySettings = MoneySettings(),
)

@HiltViewModel
class StatisticsViewModel @Inject constructor(
    private val dailyUsageDao: DailyUsageDao,
    private val monitoredAppDao: MonitoredAppDao,
    private val installedAppsProvider: InstalledAppsProvider,
    private val moneyPreferences: MoneyPreferences,
) : ViewModel() {

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

        val mondayEpoch = todayEpoch - (today.dayOfWeek.value - 1)
        val (startDay, endDay) = when (p) {
            StatPeriod.DAILY -> todayEpoch to todayEpoch
            StatPeriod.WEEKLY -> mondayEpoch to todayEpoch
            StatPeriod.MONTHLY -> (todayEpoch - 27) to todayEpoch
            // Consolidado de tudo desde o início; o range efetivo é ajustado
            // depois para o primeiro dia com dados.
            StatPeriod.TOTAL -> 0L to todayEpoch
        }

        dataJob = combine(
            dailyUsageDao.observeRange(startDay, endDay),
            monitoredAppDao.observeMonitored(),
            iconCache,
            moneyPreferences.settings,
        ) { usageRows, monitoredApps, icons, moneySettings ->
            val perMinuteRate = HomeMetrics.perMinuteRate(moneySettings.monthlySalary)

            val appNames = monitoredApps.associate { it.packageName to it.appName }

            // ── Métricas globais ────────────────────────────────────────────
            val totalMillis = usageRows.sumOf { it.usedMillis }
            val timeSpent = formatMillis(totalMillis)

            val mostUsedPkg = usageRows
                .groupBy { it.packageName }
                .mapValues { (_, rows) -> rows.sumOf { it.usedMillis } }
                .filter { it.value > 0 }
                .maxByOrNull { it.value }?.key
            val mostUsed = mostUsedPkg?.let { appNames[it] ?: it } ?: ""

            // ── Money lost (weekly/monthly only — daily is shown on Home) ───
            val moneyLost = if (p == StatPeriod.DAILY) null
            else (totalMillis / 60_000.0) * perMinuteRate

            // ── Melhor/pior dia + detalhamento dia a dia (extrato) ───────────
            var bestDay: DayHighlight? = null
            var worstDay: DayHighlight? = null
            var dayBreakdown: List<DayBreakdownEntry> = emptyList()
            if (p != StatPeriod.DAILY) {
                // No TOTAL o range efetivo começa no primeiro dia com dados,
                // não no epoch 0 — senão o "melhor dia" seria sempre um dia vazio.
                val effectiveStart = if (p == StatPeriod.TOTAL) {
                    usageRows.minOfOrNull { it.epochDay } ?: todayEpoch
                } else {
                    startDay
                }
                val millisByDay = usageRows
                    .groupBy { it.epochDay }
                    .mapValues { (_, rows) -> rows.sumOf { it.usedMillis } }
                val labelFormatter = { day: Long ->
                    val date = LocalDate.ofEpochDay(day)
                    val weekday = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale("pt", "BR"))
                        .replaceFirstChar { it.uppercase() }
                    // Semana atual: só o dia da semana já identifica; períodos
                    // maiores precisam da data para não ficar ambíguo.
                    if (p == StatPeriod.WEEKLY) {
                        weekday
                    } else {
                        "$weekday %02d/%02d".format(date.dayOfMonth, date.monthValue)
                    }
                }
                val daySpends = (effectiveStart..endDay).map { day ->
                    com.dollarblock.feature.home.DaySpend(
                        day,
                        (millisByDay[day] ?: 0L) / 60_000.0 * perMinuteRate,
                    )
                }
                val result = HomeMetrics.bestAndWorstDay(daySpends)
                bestDay = result.best?.let { DayHighlight(labelFormatter(it.epochDay), it.amount) }
                worstDay = result.worst?.let { DayHighlight(labelFormatter(it.epochDay), it.amount) }
                dayBreakdown = daySpends.map { spend ->
                    DayBreakdownEntry(
                        label = labelFormatter(spend.epochDay),
                        usedMillis = millisByDay[spend.epochDay] ?: 0L,
                        amount = spend.amount,
                    )
                }
            }

            // ── Top apps (donut chart) ──────────────────────────────────────
            val usageByPkg = usageRows
                .groupBy { it.packageName }
                .mapValues { (_, rows) -> rows.sumOf { it.usedMillis } }
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
                StatPeriod.WEEKLY -> buildWeeklyChartPerApp(usageRows, monitoredApps, appNames, todayEpoch)
                StatPeriod.MONTHLY -> buildMonthlyChartPerApp(usageRows, monitoredApps, appNames, todayEpoch)
                // TOTAL: tendência semana a semana desde o primeiro registro — é
                // onde a queda de uso após adotar o DollarBlock fica visível.
                StatPeriod.TOTAL -> buildTotalChartPerApp(usageRows, monitoredApps, appNames, todayEpoch)
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
                bestDay = bestDay,
                worstDay = worstDay,
                dayBreakdown = dayBreakdown,
                moneySettings = moneySettings,
            )
        }.onEach { _uiState.value = it }.launchIn(viewModelScope)
    }

    // ── Chart builders ──────────────────────────────────────────────────────

    private fun buildWeeklyChartPerApp(
        rows: List<com.dollarblock.data.local.db.DailyUsageEntity>,
        monitoredApps: List<com.dollarblock.data.local.db.MonitoredAppEntity>,
        appNames: Map<String, String>,
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
                    .sumOf { it.usedMillis }
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
                    .sumOf { it.usedMillis }
                    .toFloat()
            }
            AppChartLine(appName = appNames[app.packageName] ?: app.packageName, points = points)
        }.filter { line -> line.points.any { it > 0f } }
        return lines to xLabels
    }

    /**
     * Tendência de longo prazo para a aba Total: uma linha por app monitorado com o
     * tempo de uso somado por semana, da primeira semana com registro até a atual.
     * É a visão em que o usuário enxerga a queda de uso depois de adotar o app. Para
     * não estourar o eixo X, no máximo as 12 semanas mais recentes são plotadas.
     */
    private fun buildTotalChartPerApp(
        rows: List<com.dollarblock.data.local.db.DailyUsageEntity>,
        monitoredApps: List<com.dollarblock.data.local.db.MonitoredAppEntity>,
        appNames: Map<String, String>,
        todayEpoch: Long,
    ): Pair<List<AppChartLine>, List<String>> {
        if (monitoredApps.isEmpty() || rows.isEmpty()) return emptyList<AppChartLine>() to emptyList()
        val locale = Locale("pt", "BR")
        // Semana ancorada na segunda-feira da semana atual, recuando de 7 em 7 até
        // cobrir o dia mais antigo com uso (limitado a 12 janelas).
        val mondayThisWeek = todayEpoch - (LocalDate.ofEpochDay(todayEpoch).dayOfWeek.value - 1)
        val firstDay = rows.minOf { it.epochDay }
        val weeksBack = ((mondayThisWeek - firstDay) / 7).toInt().coerceIn(0, 11)
        val weekStarts = (weeksBack downTo 0).map { mondayThisWeek - it * 7L }
        val xLabels = weekStarts.map { start ->
            val d = LocalDate.ofEpochDay(start)
            "%02d/%02d".format(d.dayOfMonth, d.monthValue)
        }
        val lines = monitoredApps.map { app ->
            val points = weekStarts.map { start ->
                rows.filter { it.packageName == app.packageName && it.epochDay in start..(start + 6) }
                    .sumOf { it.usedMillis }
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
