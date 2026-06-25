package com.dollarblock.feature.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dollarblock.data.local.db.DailyUsageDao
import com.dollarblock.data.local.db.MonitoredAppDao
import com.dollarblock.feature.home.HomeMetrics
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject

enum class StatPeriod { DAILY, WEEKLY, MONTHLY }

data class StatisticsUiState(
    val period: StatPeriod = StatPeriod.WEEKLY,
    val chartValues: List<Float> = emptyList(),
    val chartLabels: List<String> = emptyList(),
    val timeSpent: String = "—",
    val mostUsed: String = "—",
    /** null when period == DAILY (shown on Home instead). */
    val moneyLost: Double? = null,
)

@HiltViewModel
class StatisticsViewModel @Inject constructor(
    private val dailyUsageDao: DailyUsageDao,
    private val monitoredAppDao: MonitoredAppDao,
) : ViewModel() {

    private companion object {
        val REAIS_PER_MINUTE = HomeMetrics.REAIS_PER_MINUTE
    }

    val period = MutableStateFlow(StatPeriod.WEEKLY)
    private val _uiState = MutableStateFlow(StatisticsUiState())
    val uiState: StateFlow<StatisticsUiState> = _uiState

    init {
        period.onEach { resubscribe(it) }.launchIn(viewModelScope)
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
        ) { usageRows, monitoredApps ->

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

            // ── Dados do gráfico ────────────────────────────────────────────
            val (values, labels) = when (p) {
                StatPeriod.DAILY -> buildDailyChart(usageRows, monitoredApps, appNames, ::effectiveMillis, todayEpoch)
                StatPeriod.WEEKLY -> buildWeeklyChart(usageRows, todayEpoch, ::effectiveMillis)
                StatPeriod.MONTHLY -> buildMonthlyChart(usageRows, todayEpoch, ::effectiveMillis)
            }

            StatisticsUiState(
                period = p,
                chartValues = values,
                chartLabels = labels,
                timeSpent = timeSpent,
                mostUsed = mostUsed,
                moneyLost = moneyLost,
            )
        }.onEach { _uiState.value = it }.launchIn(viewModelScope)
    }

    // ── Chart builders ──────────────────────────────────────────────────────

    private fun buildDailyChart(
        rows: List<com.dollarblock.data.local.db.DailyUsageEntity>,
        monitoredApps: List<com.dollarblock.data.local.db.MonitoredAppEntity>,
        appNames: Map<String, String>,
        effective: (String, Long, Long) -> Long,
        todayEpoch: Long,
    ): Pair<List<Float>, List<String>> {
        // Uma barra por app monitorado (mesmo com uso zero), ordenado por uso decrescente.
        if (monitoredApps.isEmpty()) return emptyList<Float>() to emptyList()
        val usageByPkg = rows.filter { it.epochDay == todayEpoch }
            .associate { it.packageName to effective(it.packageName, it.epochDay, it.usedMillis) }
        val byApp = monitoredApps
            .map { app -> app.packageName to (usageByPkg[app.packageName] ?: 0L) }
            .sortedByDescending { it.second }
            .take(7)
        val values = byApp.map { it.second.toFloat() }
        val labels = byApp.map { (pkg, _) -> (appNames[pkg] ?: pkg).take(5) }
        return values to labels
    }

    private fun buildWeeklyChart(
        rows: List<com.dollarblock.data.local.db.DailyUsageEntity>,
        todayEpoch: Long,
        effective: (String, Long, Long) -> Long,
    ): Pair<List<Float>, List<String>> {
        val locale = Locale("pt", "BR")
        // Começa sempre na segunda-feira da semana atual (ISO: segunda = 1)
        val today = LocalDate.ofEpochDay(todayEpoch)
        val mondayEpoch = todayEpoch - (today.dayOfWeek.value - 1)
        return (0..6).map { offset ->
            val epoch = mondayEpoch + offset
            val dayMillis = rows
                .filter { it.epochDay == epoch }
                .sumOf { effective(it.packageName, it.epochDay, it.usedMillis) }
                .toFloat()
            val label = LocalDate.ofEpochDay(epoch)
                .dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
                .replaceFirstChar { it.uppercase() }
            dayMillis to label
        }.unzip()
    }

    private fun buildMonthlyChart(
        rows: List<com.dollarblock.data.local.db.DailyUsageEntity>,
        todayEpoch: Long,
        effective: (String, Long, Long) -> Long,
    ): Pair<List<Float>, List<String>> {
        // Sem 1 (mais antiga) → Sem 4 (mais recente), da esquerda para direita
        return (3 downTo 0).mapIndexed { index, weekOffset ->
            val weekEnd = todayEpoch - weekOffset * 7
            val weekStart = weekEnd - 6
            val weekMillis = rows
                .filter { it.epochDay in weekStart..weekEnd }
                .sumOf { effective(it.packageName, it.epochDay, it.usedMillis) }
                .toFloat()
            weekMillis to "Sem ${index + 1}"
        }.unzip()
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
