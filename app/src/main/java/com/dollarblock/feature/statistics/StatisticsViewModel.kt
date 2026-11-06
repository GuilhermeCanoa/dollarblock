package com.dollarblock.feature.statistics

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dollarblock.data.apps.InstalledAppsProvider
import com.dollarblock.data.local.db.DailyUsageDao
import com.dollarblock.data.local.db.MonitoredAppDao
import com.dollarblock.data.local.db.MonitoredAppEntity
import com.dollarblock.data.local.db.dao.EventDao
import com.dollarblock.data.local.prefs.MoneyPreferences
import com.dollarblock.data.usage.epochDayAt
import com.dollarblock.data.usage.localEpochDayFlow
import com.dollarblock.domain.model.AppDay
import com.dollarblock.domain.model.MoneySettings
import com.dollarblock.domain.model.UnlockDay
import com.dollarblock.feature.home.HomeMetrics
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/** Períodos do Extrato. "Hoje" não existe aqui: a Home já é o dia. */
enum class StatPeriod { WEEKLY, MONTHLY, TOTAL }

/** Linha de app do Extrato: a tira de dias do período e quantos ficaram dentro. */
data class StatementAppRow(
    val packageName: String,
    val appName: String,
    val icon: ImageBitmap?,
    val tones: List<DayTone>,
    val within: Int,
    val counted: Int,
)

/** Um app no extrato de um dia (bottom sheet ao tocar no calendário). */
data class DayDetailRow(
    val appName: String,
    val icon: ImageBitmap?,
    val usedMillis: Long,
    val cost: Double,
    val limitMinutes: Int?,
    val tone: DayTone,
)

data class DayDetail(
    val day: CalendarDay,
    val rows: List<DayDetailRow>,
    val cost: Double,
)

data class StatisticsUiState(
    val period: StatPeriod = StatPeriod.WEEKLY,
    val today: Long = 0L,
    /** Dias do período, inclusive os futuros da semana/mês (desenhados vazios). */
    val days: List<CalendarDay> = emptyList(),
    val within: Int = 0,
    val counted: Int = 0,
    val streak: Int = 0,
    /** Média por dia com dado no período; null sem dado. */
    val averageMillis: Long? = null,
    val averageCost: Double? = null,
    /** Dia mais caro do período (maior uso × preço do minuto); null sem uso. */
    val priciestDay: CalendarDay? = null,
    val priciestCost: Double? = null,
    val apps: List<StatementAppRow> = emptyList(),
    val hasLimits: Boolean = false,
    val selected: DayDetail? = null,
    val moneySettings: MoneySettings = MoneySettings(),
)

@HiltViewModel
class StatisticsViewModel @Inject constructor(
    dailyUsageDao: DailyUsageDao,
    monitoredAppDao: MonitoredAppDao,
    eventDao: EventDao,
    private val installedAppsProvider: InstalledAppsProvider,
    moneyPreferences: MoneyPreferences,
) : ViewModel() {

    val period = MutableStateFlow(StatPeriod.WEEKLY)
    private val selectedDay = MutableStateFlow<Long?>(null)
    private val iconCache = MutableStateFlow<Map<String, ImageBitmap?>>(emptyMap())
    private val zone: ZoneId get() = ZoneId.systemDefault()

    /** Tudo o que veio do banco, já reduzido a chaves app/dia. */
    private data class Ledger(
        val apps: List<MonitoredAppEntity>,
        val usage: Map<AppDay, Long>,
        val blocks: Set<AppDay>,
        val unlocks: List<UnlockDay>,
    )

    private val ledger = combine(
        dailyUsageDao.observeRange(0L, Long.MAX_VALUE),
        monitoredAppDao.observeMonitored(),
        eventDao.observeBlockStamps(),
        eventDao.observeUnlockReceiptsSince(0L),
    ) { usageRows, apps, blockStamps, receipts ->
        val z = zone
        Ledger(
            apps = apps,
            usage = usageRows.associate { AppDay(it.packageName, it.epochDay) to it.usedMillis },
            blocks = blockStamps.map { AppDay(it.packageName, epochDayAt(it.timestamp, z)) }.toSet(),
            unlocks = receipts.map {
                UnlockDay(AppDay(it.packageName, epochDayAt(it.timestamp, z)), it.amount, it.method)
            },
        )
    }

    private val controls = combine(period, selectedDay, localEpochDayFlow()) { p, sel, today -> Triple(p, sel, today) }

    val uiState: StateFlow<StatisticsUiState> = combine(
        ledger,
        controls,
        iconCache,
        moneyPreferences.settings,
    ) { ledger, (p, sel, today), icons, money ->
        build(ledger, p, sel, today, icons, money)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatisticsUiState())

    init {
        viewModelScope.launch {
            monitoredAppDao.observeMonitored().collect { apps ->
                val missing = apps.map { it.packageName }.toSet() - iconCache.value.keys
                if (missing.isNotEmpty()) {
                    iconCache.value = iconCache.value + missing.associateWith { installedAppsProvider.getIconForPackage(it) }
                }
            }
        }
    }

    fun selectDay(epochDay: Long?) {
        selectedDay.value = epochDay
    }

    private fun build(
        ledger: Ledger,
        p: StatPeriod,
        sel: Long?,
        today: Long,
        icons: Map<String, ImageBitmap?>,
        money: MoneySettings,
    ): StatisticsUiState {
        val z = zone
        val apps = ledger.apps.map {
            CalendarApp(it.packageName, it.dailyLimitMinutes, epochDayAt(it.createdAt, z))
        }
        val names = ledger.apps.associate { it.packageName to it.appName }
        val rate = HomeMetrics.perMinuteRate(money.monthlySalary)
        fun cost(millis: Long) = millis / 60_000.0 * rate

        // Histórico inteiro (para a sequência e para a aba Tudo), do primeiro dia com dado.
        val firstDay = (ledger.usage.keys.map { it.epochDay } + apps.map { it.firstMonitoredDay })
            .minOrNull()?.coerceAtMost(today) ?: today
        val history = StatementCalendar.days(firstDay..today, today, apps, ledger.usage, ledger.blocks, ledger.unlocks)

        val range = periodRange(p, today, firstDay)
        val days = StatementCalendar.days(range, today, apps, ledger.usage, ledger.blocks, ledger.unlocks)
        val (within, counted) = StatementCalendar.withinCount(days)
        val measured = days.filter { it.epochDay <= today && it.usedMillis > 0 }
        val average = if (measured.isEmpty()) null else measured.sumOf { it.usedMillis } / measured.size
        val priciest = measured.maxByOrNull { it.usedMillis }

        val rows = apps.filter { it.dailyLimitMinutes != null }.map { app ->
            val tones = StatementCalendar.appTones(app, range, today, ledger.usage, ledger.blocks, ledger.unlocks)
            StatementAppRow(
                packageName = app.packageName,
                appName = names[app.packageName] ?: app.packageName,
                icon = icons[app.packageName],
                tones = tones,
                within = tones.count { it.isWithin },
                counted = tones.count { it != DayTone.EMPTY },
            )
        }.sortedWith(compareBy<StatementAppRow> { it.within.toFloat() / it.counted.coerceAtLeast(1) }.thenBy { it.appName })

        val selected = sel?.let { day ->
            val calendarDay = history.firstOrNull { it.epochDay == day } ?: days.firstOrNull { it.epochDay == day }
                ?: return@let null
            val detailRows = apps.mapNotNull { app ->
                val key = AppDay(app.packageName, day)
                val used = ledger.usage[key] ?: 0L
                val tone = StatementCalendar.appTones(app, day..day, today, ledger.usage, ledger.blocks, ledger.unlocks).single()
                if (used == 0L && tone == DayTone.EMPTY) return@mapNotNull null
                DayDetailRow(names[app.packageName] ?: app.packageName, icons[app.packageName], used, cost(used), app.dailyLimitMinutes, tone)
            }.sortedByDescending { it.usedMillis }
            DayDetail(calendarDay, detailRows, cost(calendarDay.usedMillis))
        }

        return StatisticsUiState(
            period = p,
            today = today,
            days = days,
            within = within,
            counted = counted,
            streak = StatementCalendar.streak(history, today),
            averageMillis = average,
            averageCost = average?.let(::cost),
            priciestDay = priciest,
            priciestCost = priciest?.let { cost(it.usedMillis) },
            apps = rows,
            hasLimits = apps.any { it.dailyLimitMinutes != null },
            selected = selected,
            moneySettings = money,
        )
    }

    private fun periodRange(p: StatPeriod, today: Long, firstDay: Long): LongRange {
        val date = LocalDate.ofEpochDay(today)
        return when (p) {
            StatPeriod.WEEKLY -> {
                val monday = today - (date.dayOfWeek.value - 1)
                monday..(monday + 6)
            }
            StatPeriod.MONTHLY -> {
                val first = date.withDayOfMonth(1)
                first.toEpochDay()..first.plusMonths(1).minusDays(1).toEpochDay()
            }
            StatPeriod.TOTAL -> firstDay..today
        }
    }
}
