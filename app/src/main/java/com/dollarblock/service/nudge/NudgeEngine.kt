package com.dollarblock.service.nudge

import android.content.Context
import android.text.format.DateFormat
import android.util.Log
import com.dollarblock.BuildConfig
import com.dollarblock.R
import com.dollarblock.data.local.db.DailyUsageDao
import com.dollarblock.data.local.db.MonitoredAppDao
import com.dollarblock.data.local.prefs.MoneyPreferences
import com.dollarblock.data.local.prefs.NudgePreferences
import com.dollarblock.data.usage.UsageStatsProvider
import com.dollarblock.domain.model.AppCurrency
import com.dollarblock.domain.model.MoneyFormat
import com.dollarblock.feature.home.HomeMetrics
import com.dollarblock.feature.home.text
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalTime
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Orquestra as cobranças do gerente (E22): recebe os gatilhos (sessão longa, abertura do
 * app, fechamento do dia, passe pago), aplica as regras de [NudgePolicy], monta o texto
 * com os números reais do usuário e posta via [NudgeNotifier].
 *
 * Quem chama: o serviço de acessibilidade (sessão e abertura), o `UsageSyncWorker`
 * (fechamento do dia) e a `BlockActivity` (passe pago).
 */
@Singleton
class NudgeEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: NudgePreferences,
    private val notifier: NudgeNotifier,
    private val moneyPreferences: MoneyPreferences,
    private val dailyUsageDao: DailyUsageDao,
    private val monitoredAppDao: MonitoredAppDao,
    private val usageStatsProvider: UsageStatsProvider,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Serializa "checa o teto → posta → registra" para dois gatilhos não furarem o teto juntos. */
    private val mutex = Mutex()

    // --- Gatilhos ------------------------------------------------------------------

    /** A cada poll do tracking: dispara A/B/C quando a sessão contínua passa do limiar. */
    suspend fun onSessionTick(packageName: String, label: () -> String, sessionStartMs: Long, todayUsedMs: Long) {
        val threshold = sessionThresholdMinutes()
        val sessionMinutes = ((System.currentTimeMillis() - sessionStartMs) / 60_000L).toInt()
        if (sessionMinutes < threshold) return
        val today = usageStatsProvider.currentEpochDay()
        val trigger = "session_$packageName"
        if (prefs.lastFired(trigger) == today) return

        sendCapped(today, trigger) {
            val appLabel = label()
            val money = moneyPreferences.settings.first()
            val rate = HomeMetrics.perMinuteRate(money.monthlySalary)
            val avg = averageDailyMinutes(packageName, today)
            val annualItem = avg?.let { annualItemText(NudgeMath.annualValue(it, rate), money.currency) }
            val pool = SESSION_POOL + NudgeMath.eligibleProjections(avg, annualItem != null)
            val nudge = NudgePolicy.pick(pool, lastNudge()) ?: return@sendCapped null
            render(
                nudge,
                Facts(
                    app = appLabel,
                    session = duration(sessionMinutes),
                    sessionValue = MoneyFormat.format(sessionMinutes * rate, money.currency),
                    today = duration((todayUsedMs / 60_000L).toInt()),
                    perMinute = MoneyFormat.format(rate, money.currency),
                    avgDaily = avg,
                    annualValue = avg?.let { MoneyFormat.format(NudgeMath.annualValue(it, rate), money.currency, cents = false) },
                    annualItem = annualItem,
                ),
            )
        }
    }

    /**
     * App monitorado entrou em foreground: "desbloqueou e foi direto" (E22), "débito
     * automático" no horário habitual (C17) ou "primeira abertura antes das 9h" (E21).
     */
    suspend fun onMonitoredAppForeground(packageName: String, label: () -> String, nowMs: Long, lastUserPresentMs: Long) {
        val today = usageStatsProvider.currentEpochDay()
        val firstOpenTrigger = "first_open_$packageName"
        val firstOpenToday = prefs.lastFired(firstOpenTrigger) != today
        if (firstOpenToday) prefs.markFired(firstOpenTrigger, today)

        val straightFromUnlock = lastUserPresentMs > 0 &&
            nowMs - lastUserPresentMs in 0..NudgePolicy.STRAIGHT_FROM_UNLOCK_WINDOW_MS
        if (straightFromUnlock &&
            NudgePolicy.cooledDown(prefs.lastFired(TRIGGER_UNLOCK), today, NudgePolicy.STRAIGHT_FROM_UNLOCK_COOLDOWN_DAYS)
        ) {
            sendCapped(today, TRIGGER_UNLOCK) { render(Nudge.E22_STRAIGHT_FROM_UNLOCK, Facts(app = label())) }
            return
        }
        if (!firstOpenToday) return

        val nowMinute = LocalTime.now().let { it.hour * 60 + it.minute }
        val autoDebitTrigger = "auto_debit_$packageName"
        if (NudgePolicy.cooledDown(prefs.lastFired(autoDebitTrigger), today, NudgePolicy.AUTO_DEBIT_COOLDOWN_DAYS)) {
            val habit = NudgeMath.habitMinuteOfDay(usageStatsProvider.firstOpenMinutesOfDay(packageName, 7))
            if (habit != null && kotlin.math.abs(nowMinute - habit) <= NudgePolicy.AUTO_DEBIT_TOLERANCE_MIN) {
                sendCapped(today, autoDebitTrigger) {
                    render(Nudge.C17_AUTO_DEBIT, Facts(app = label(), clock = clock(habit)))
                }
                return
            }
        }
        if (nowMinute < NudgePolicy.EARLY_OPEN_BEFORE_MINUTE &&
            NudgePolicy.cooledDown(prefs.lastFired(TRIGGER_EARLY), today, NudgePolicy.EARLY_OPEN_COOLDOWN_DAYS)
        ) {
            sendCapped(today, TRIGGER_EARLY) { render(Nudge.E21_EARLY_OPEN, Facts(app = label(), clock = clock(nowMinute))) }
        }
    }

    /** Chamado pelo worker periódico: a partir das 21h, uma vez por dia, o fechamento (D18–D20). */
    suspend fun onDailyCloseCheck() {
        val now = LocalTime.now()
        if (now.hour * 60 + now.minute < NudgePolicy.DAILY_CLOSE_FROM_MINUTE) return
        val today = usageStatsProvider.currentEpochDay()
        if (prefs.lastFired(TRIGGER_DAILY_CLOSE) == today) return

        val monitored = monitoredAppDao.observeMonitored().first()
        if (monitored.isEmpty()) return
        val usage = dailyUsageDao.observeForDay(today).first().associate { it.packageName to it.usedMillis }
        val used = monitored.associateWith { usage[it.packageName] ?: 0L }
        val totalMinutes = (used.values.sum() / 60_000L).toInt()
        if (totalMinutes < 1) return

        val anyExceeded = used.any { (app, ms) -> app.dailyLimitMinutes?.let { ms >= it * 60_000L } == true }
        val hasLimits = monitored.any { it.dailyLimitMinutes != null }
        val top = used.maxByOrNull { it.value }?.takeIf { it.value >= 60_000L }

        send(today, TRIGGER_DAILY_CLOSE, countsTowardCap = false) {
            val money = moneyPreferences.settings.first()
            val rate = HomeMetrics.perMinuteRate(money.monthlySalary)
            val nudge = NudgePolicy.pick(NudgePolicy.dailyClosePool(anyExceeded, hasLimits, top != null), lastNudge())
                ?: return@send null
            render(
                nudge,
                Facts(
                    app = top?.key?.appName.orEmpty(),
                    today = duration(totalMinutes),
                    todayValue = MoneyFormat.format(totalMinutes * rate, money.currency),
                    topAppTime = top?.let { duration((it.value / 60_000L).toInt()) },
                ),
            )
        }
    }

    /** Passe pago (não a cortesia): recibo E23 no 1º do dia, E24 no 2º; do 3º em diante, silêncio. */
    fun onPassPaid(packageName: String, appLabel: String, passesPaidBefore: Int) {
        val nudge = when (passesPaidBefore) {
            0 -> Nudge.E23_PASS_FIRST
            1 -> Nudge.E24_PASS_SECOND
            else -> return
        }
        scope.launch {
            val today = usageStatsProvider.currentEpochDay()
            sendCapped(today, "pass_$packageName") { render(nudge, Facts(app = appLabel)) }
        }
    }

    /** Só debug: posta a cobrança [nudge] com números reais de [packageName], sem teto. */
    suspend fun debugPreview(nudge: Nudge, packageName: String, appLabel: String) {
        val today = usageStatsProvider.currentEpochDay()
        val money = moneyPreferences.settings.first()
        val rate = HomeMetrics.perMinuteRate(money.monthlySalary)
        val avg = averageDailyMinutes(packageName, today) ?: 45.0
        val todayMs = dailyUsageDao.getForDay(packageName, today)?.usedMillis ?: 0L
        val text = render(
            nudge,
            Facts(
                app = appLabel,
                session = duration(32),
                sessionValue = MoneyFormat.format(32 * rate, money.currency),
                today = duration((todayMs / 60_000L).toInt()),
                todayValue = MoneyFormat.format(todayMs / 60_000.0 * rate, money.currency),
                topAppTime = duration((todayMs / 60_000L).toInt()),
                perMinute = MoneyFormat.format(rate, money.currency),
                avgDaily = avg,
                annualValue = MoneyFormat.format(NudgeMath.annualValue(avg, rate), money.currency, cents = false),
                annualItem = annualItemText(NudgeMath.annualValue(avg, rate), money.currency) ?: "—",
                clock = clock(LocalTime.now().let { it.hour * 60 + it.minute }),
            ),
        )
        notifier.post(text.title, text.body, NOTIFICATION_ID_BASE + 1000 + nudge.ordinal)
    }

    // --- Envio ---------------------------------------------------------------------

    private suspend fun sendCapped(today: Long, trigger: String, build: suspend () -> Message?) =
        send(today, trigger, countsTowardCap = true, build)

    private suspend fun send(
        today: Long,
        trigger: String,
        countsTowardCap: Boolean,
        build: suspend () -> Message?,
    ) = mutex.withLock {
        if (!prefs.isEnabled() || !notifier.hasPermission()) return@withLock
        if (countsTowardCap && NudgePolicy.capReached(prefs.sentOn(today))) return@withLock
        val message = runCatching { build() }.onFailure { Log.w(TAG, "nudge $trigger", it) }.getOrNull()
            ?: return@withLock
        prefs.markFired(trigger, today)
        prefs.recordSent(today, message.nudge.name, countsTowardCap)
        notifier.post(message.title, message.body, NOTIFICATION_ID_BASE + Math.floorMod(trigger.hashCode(), 1000))
        Log.i(TAG, "nudge ${message.nudge.name} ($trigger)")
    }


    private suspend fun lastNudge(): Nudge? =
        prefs.lastNudgeName()?.let { name -> Nudge.entries.firstOrNull { it.name == name } }

    // --- Dados ---------------------------------------------------------------------

    private suspend fun sessionThresholdMinutes(): Int =
        (if (BuildConfig.DEBUG) prefs.debugSessionThresholdMinutes() else null) ?: NudgePolicy.SESSION_THRESHOLD_MIN

    /** Média diária dos últimos 7 dias completos com registro (hoje fora). */
    private suspend fun averageDailyMinutes(packageName: String, today: Long): Double? {
        val rows = dailyUsageDao.observeRange(today - 7, today - 1).first()
            .filter { it.packageName == packageName && it.usedMillis > 0 }
        return NudgeMath.averageDailyMinutes(rows.map { it.usedMillis })
    }

    /** "2 PS5" para o valor anual — só comparação em unidades inteiras, senão null. */
    private fun annualItemText(annualValue: Double, currency: AppCurrency): String? {
        val eq = HomeMetrics.equivalence(annualValue, currency, Random.nextInt()) ?: return null
        val count = eq.count ?: return null
        return context.resources.getQuantityString(eq.item.text.countRes, count, count)
            .removePrefix("=").trim()
    }

    private fun duration(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return if (h > 0) context.getString(R.string.nudge_duration_hm, h, m)
        else context.getString(R.string.nudge_duration_m, m)
    }

    private fun clock(minuteOfDay: Int): String {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, minuteOfDay / 60)
            set(Calendar.MINUTE, minuteOfDay % 60)
        }
        return DateFormat.getTimeFormat(context).format(cal.time)
    }

    // --- Texto ---------------------------------------------------------------------

    private data class Facts(
        val app: String,
        val session: String = "",
        val sessionValue: String = "",
        val today: String = "",
        val todayValue: String = "",
        val topAppTime: String? = null,
        val perMinute: String = "",
        val avgDaily: Double? = null,
        val annualValue: String? = null,
        val annualItem: String? = null,
        val clock: String = "",
    )

    private data class Message(val nudge: Nudge, val title: String, val body: String)

    /** Título + corpo da cobrança, com os números de [f]. */
    private fun render(nudge: Nudge, f: Facts): Message {
        val res = context.resources
        val avg = f.avgDaily ?: 0.0
        val hours = NudgeMath.annualHours(avg)
        val days = NudgeMath.annualDays(avg)
        val body = when (nudge) {
            Nudge.A1_WALK -> context.getString(R.string.nudge_a1, f.app)
            Nudge.A2_JUST_CHECKING -> context.getString(R.string.nudge_a2, f.app, f.session)
            Nudge.A3_NO_PAUSE -> context.getString(R.string.nudge_a3, f.app, f.session)
            Nudge.A4_MOVIE -> context.getString(R.string.nudge_a4, f.app, f.session)
            Nudge.A5_ROBBED -> context.getString(R.string.nudge_a5, f.app, f.session, f.sessionValue)
            Nudge.B6_DAYS_AWAKE -> res.getQuantityString(R.plurals.nudge_b6, days, f.app, duration(avg.roundToInt()), hours, days)
            Nudge.B7_BOOKS -> context.getString(R.string.nudge_b7, f.app, NudgeMath.books(avg))
            Nudge.B8_MARATHONS -> NudgeMath.marathons(avg).let { res.getQuantityString(R.plurals.nudge_b8, it, f.app, it) }
            Nudge.B9_VACATION -> res.getQuantityString(R.plurals.nudge_b9, days, f.app, days)
            Nudge.B10_ANNUAL_BILL -> context.getString(R.string.nudge_b10, f.app, f.annualValue.orEmpty(), f.annualItem.orEmpty())
            Nudge.B11_SPANISH -> context.getString(R.string.nudge_b11, f.app, hours, NudgeMath.spanishPercent(avg))
            Nudge.C13_THEFT -> context.getString(R.string.nudge_c13, f.app, f.sessionValue)
            Nudge.C14_CRUMBS -> context.getString(R.string.nudge_c14, f.app, f.perMinute)
            Nudge.C15_BILLIONS -> context.getString(R.string.nudge_c15, f.app, f.today)
            Nudge.C16_SALARY -> context.getString(R.string.nudge_c16, f.app, f.today)
            Nudge.C17_AUTO_DEBIT -> context.getString(R.string.nudge_c17, f.app, f.clock)
            Nudge.D18_CLOSING -> context.getString(R.string.nudge_d18, f.today, f.todayValue)
            Nudge.D19_WONT_RETURN -> context.getString(R.string.nudge_d19, f.app, f.topAppTime.orEmpty())
            Nudge.D20_CLEAN_DAY -> context.getString(R.string.nudge_d20)
            Nudge.E21_EARLY_OPEN -> context.getString(R.string.nudge_e21, f.app, f.clock)
            Nudge.E22_STRAIGHT_FROM_UNLOCK -> context.getString(R.string.nudge_e22, f.app)
            Nudge.E23_PASS_FIRST -> context.getString(R.string.nudge_e23, f.app)
            Nudge.E24_PASS_SECOND -> context.getString(R.string.nudge_e24, f.app)
        }
        val title = when (nudge.group) {
            Nudge.Group.SESSION -> context.getString(R.string.nudge_title_session, f.app)
            Nudge.Group.PROJECTION -> context.getString(R.string.nudge_title_projection, f.app)
            Nudge.Group.HABIT -> context.getString(R.string.nudge_title_habit, f.app)
            Nudge.Group.DAILY_CLOSE -> context.getString(R.string.nudge_title_daily_close)
            Nudge.Group.PASS -> context.getString(R.string.nudge_title_pass)
        }
        return Message(nudge, title, body)
    }

    private companion object {
        const val TAG = "DollarBlockNudge"
        const val NOTIFICATION_ID_BASE = 20_000
        const val TRIGGER_UNLOCK = "straight_from_unlock"
        const val TRIGGER_EARLY = "early_open"
        const val TRIGGER_DAILY_CLOSE = "daily_close"

        /** Frases do gatilho da sessão longa que não dependem de histórico (A + C13–C16). */
        val SESSION_POOL = listOf(
            Nudge.A1_WALK, Nudge.A2_JUST_CHECKING, Nudge.A3_NO_PAUSE, Nudge.A4_MOVIE, Nudge.A5_ROBBED,
            Nudge.C13_THEFT, Nudge.C14_CRUMBS, Nudge.C15_BILLIONS, Nudge.C16_SALARY,
        )
    }
}
