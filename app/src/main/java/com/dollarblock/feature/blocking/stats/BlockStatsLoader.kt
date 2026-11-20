package com.dollarblock.feature.blocking.stats

import com.dollarblock.data.local.db.DailyUsageDao
import com.dollarblock.data.local.db.MonitoredAppDao
import com.dollarblock.data.local.db.dao.EventDao
import com.dollarblock.data.local.prefs.MoneyPreferences
import com.dollarblock.data.usage.UsageStatsProvider
import com.dollarblock.data.usage.epochDayAt
import com.dollarblock.domain.model.AppCurrency
import com.dollarblock.domain.model.AppDay
import com.dollarblock.domain.model.UnlockDay
import com.dollarblock.domain.repository.MoneySummaryRepository
import com.dollarblock.feature.statistics.CalendarApp
import com.dollarblock.feature.statistics.StatementCalendar
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject

/** O que a tela de bloqueio carregou para a estatística, mais a moeda para formatar dinheiro. */
data class BlockStatData(val input: BlockStatInput, val currency: AppCurrency)

/**
 * Lê, para um app bloqueado, os números que a estatística da tela de bloqueio pode mostrar
 * (E27). Roda depois do primeiro frame; nada aqui pode atrasar a tela (ADR-001).
 */
class BlockStatsLoader @Inject constructor(
    private val eventDao: EventDao,
    private val monitoredAppDao: MonitoredAppDao,
    private val dailyUsageDao: DailyUsageDao,
    private val usageStatsProvider: UsageStatsProvider,
    private val moneySummaryRepository: MoneySummaryRepository,
    private val moneyPreferences: MoneyPreferences,
) {

    suspend fun load(packageName: String): BlockStatData {
        val zone = ZoneId.systemDefault()
        val now = ZonedDateTime.now(zone)
        val today = now.toLocalDate()
        val startOfToday = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val app = monitoredAppDao.getByPackage(packageName)

        val attempts = eventDao.observeBlockStampsSince(startOfToday).first()
            .count { it.packageName == packageName }
            // O registro desta tentativa corre em paralelo com a abertura da tela.
            .coerceAtLeast(1)
        // Medição real (a mesma do serviço), não a última sincronização do banco.
        val usedMinutes = runCatching {
            (usageStatsProvider.getTodayUsageMillisViaEvents(packageName) / 60_000L).toInt()
        }.getOrDefault(0)
        val untilMidnight = Duration.between(now, today.plusDays(1).atStartOfDay(zone)).toMinutes().toInt()

        val streak = if (app?.dailyLimitMinutes == null) 0 else streakDays(app.packageName, app.dailyLimitMinutes, app.createdAt, today, zone)

        // A carteira resolve o preço do passe (rede → cache → padrão): não espera por ela.
        val monthSaved = withTimeoutOrNull(WALLET_TIMEOUT_MS) { moneySummaryRepository.observeMonthWallet().first().saved }
        val currency = moneyPreferences.settings.first().currency

        return BlockStatData(
            input = BlockStatInput(
                attemptsToday = attempts,
                usedMinutes = usedMinutes,
                limitMinutes = app?.dailyLimitMinutes,
                minutesUntilMidnight = untilMidnight,
                streakDays = streak,
                monthSaved = monthSaved,
            ),
            currency = currency,
        )
    }

    /** Dias seguidos sem passe pago para o app, até ontem, com a mesma régua do Extrato. */
    private suspend fun streakDays(
        packageName: String,
        limitMinutes: Int,
        createdAt: Long,
        today: LocalDate,
        zone: ZoneId,
    ): Int {
        val todayEpoch = today.toEpochDay()
        val yesterday = todayEpoch - 1
        val first = todayEpoch - STREAK_LOOKBACK_DAYS
        val firstMs = LocalDate.ofEpochDay(first).atStartOfDay(zone).toInstant().toEpochMilli()

        val usage = dailyUsageDao.observeRange(first, yesterday).first()
            .filter { it.packageName == packageName }
            .associate { AppDay(it.packageName, it.epochDay) to it.usedMillis }
        val blocks = eventDao.observeBlockStampsSince(firstMs).first()
            .filter { it.packageName == packageName }
            .map { AppDay(it.packageName, epochDayAt(it.timestamp, zone)) }
            .toSet()
        val unlocks = eventDao.observeUnlockReceiptsSince(firstMs).first()
            .filter { it.packageName == packageName }
            .map { UnlockDay(AppDay(it.packageName, epochDayAt(it.timestamp, zone)), it.amount, it.method) }

        val app = CalendarApp(packageName, limitMinutes, epochDayAt(createdAt, zone))
        val tones = StatementCalendar.appTones(app, first..yesterday, todayEpoch, usage, blocks, unlocks)
        return streakEndingYesterday(tones)
    }

    private companion object {
        const val WALLET_TIMEOUT_MS = 1_500L
        const val STREAK_LOOKBACK_DAYS = 60L
    }
}
