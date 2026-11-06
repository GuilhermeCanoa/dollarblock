package com.dollarblock.feature.statistics

import com.dollarblock.domain.model.AppDay
import com.dollarblock.domain.model.PaymentMethod
import com.dollarblock.domain.model.UnlockDay

/**
 * Desfecho de um app num dia, do melhor ao pior. A cor do dia no calendário do Extrato (E24)
 * é a do pior app daquele dia ([severity]).
 */
enum class DayTone(val severity: Int) {
    /** Sem dado, sem limite ou dia futuro: não entra na conta. */
    EMPTY(0),

    /** Ficou abaixo do limite. */
    WITHIN(1),

    /** Bateu o limite, o app trancou e o usuário aguentou (não pagou). */
    HELD(2),

    /** Passe livre do 1º dia ou cortesia: passou do limite sem cobrança. */
    FREE(3),

    /** Antes do DollarBlock (histórico do Android): passou do limite — teria pagado. */
    OVER(4),

    /** Pagou o passe do dia. */
    PAID(5),
    ;

    /** Conta como "dia dentro do limite" no herói. */
    val isWithin: Boolean get() = this == WITHIN || this == HELD
}

/** App monitorado reduzido ao que o calendário precisa. */
data class CalendarApp(
    val packageName: String,
    val dailyLimitMinutes: Int?,
    /** Dia local em que o app passou a ser monitorado; antes dele, o dado vem do backfill. */
    val firstMonitoredDay: Long,
)

/** Desfecho de um dia inteiro (todos os apps). */
data class CalendarDay(
    val epochDay: Long,
    val tone: DayTone,
    /** Todos os apps com dado nesse dia ainda não eram monitorados: "antes do DollarBlock". */
    val beforeDollarBlock: Boolean,
    val usedMillis: Long,
)

object StatementCalendar {

    /**
     * Desfecho de [app] em [day]. A precedência segue o que de fato aconteceu: um recibo
     * (pago ou grátis) decide; senão um bloqueio sem recibo é "aguentou"; senão vale a
     * comparação do uso com o limite atual (o limite não tem histórico — ver spec E24).
     */
    fun appTone(
        app: CalendarApp,
        day: Long,
        today: Long,
        usedMillis: Long?,
        blocked: Boolean,
        unlockMethod: String?,
    ): DayTone {
        val limit = app.dailyLimitMinutes ?: return DayTone.EMPTY
        if (day > today) return DayTone.EMPTY
        if (unlockMethod != null) return if (PaymentMethod.isFree(unlockMethod)) DayTone.FREE else DayTone.PAID
        if (blocked) return DayTone.HELD
        val beforeMonitoring = day < app.firstMonitoredDay
        // Antes do monitoramento só há dado se o backfill achou uso; sem linha, não há o que dizer.
        val used = usedMillis ?: if (beforeMonitoring) return DayTone.EMPTY else 0L
        return if (used <= limit * 60_000L) DayTone.WITHIN else DayTone.OVER
    }

    /**
     * Um [CalendarDay] por dia de [days], combinando o uso diário, os bloqueios e os
     * recibos de desbloqueio de todos os [apps].
     */
    fun days(
        days: LongRange,
        today: Long,
        apps: List<CalendarApp>,
        usage: Map<AppDay, Long>,
        blocks: Set<AppDay>,
        unlocks: List<UnlockDay>,
    ): List<CalendarDay> {
        val unlockByAppDay = unlockMethods(unlocks)
        return days.map { day ->
            var worst = DayTone.EMPTY
            var anyMonitored = false
            var anyData = false
            var used = 0L
            apps.forEach { app ->
                val key = AppDay(app.packageName, day)
                used += usage[key] ?: 0L
                val tone = appTone(app, day, today, usage[key], key in blocks, unlockByAppDay[key])
                if (tone == DayTone.EMPTY) return@forEach
                anyData = true
                if (day >= app.firstMonitoredDay) anyMonitored = true
                if (tone.severity > worst.severity) worst = tone
            }
            CalendarDay(day, worst, beforeDollarBlock = anyData && !anyMonitored, usedMillis = used)
        }
    }

    /** Desfechos de um único app ao longo de [days] (tira de bolinhas da lista de apps). */
    fun appTones(
        app: CalendarApp,
        days: LongRange,
        today: Long,
        usage: Map<AppDay, Long>,
        blocks: Set<AppDay>,
        unlocks: List<UnlockDay>,
    ): List<DayTone> {
        val unlockByAppDay = unlockMethods(unlocks)
        return days.map { day ->
            val key = AppDay(app.packageName, day)
            appTone(app, day, today, usage[key], key in blocks, unlockByAppDay[key])
        }
    }

    /**
     * Dias seguidos dentro do limite terminando hoje. Hoje ainda vazio (nada medido) não
     * quebra a sequência — o dia não acabou; qualquer outro dia vazio ou fora a encerra.
     */
    fun streak(history: List<CalendarDay>, today: Long): Int {
        var count = 0
        for (day in history.sortedByDescending { it.epochDay }) {
            if (day.epochDay > today) continue
            if (day.epochDay == today && day.tone == DayTone.EMPTY) continue
            if (!day.tone.isWithin) break
            count++
        }
        return count
    }

    /** "X de Y": dias dentro do limite sobre dias com dado. */
    fun withinCount(days: List<CalendarDay>): Pair<Int, Int> {
        val counted = days.filter { it.tone != DayTone.EMPTY }
        return counted.count { it.tone.isWithin } to counted.size
    }

    /**
     * Um recibo por app/dia: se houver mais de um, o pago prevalece sobre o grátis (é o que
     * pesou no bolso).
     */
    private fun unlockMethods(unlocks: List<UnlockDay>): Map<AppDay, String> =
        unlocks.groupBy { it.appDay }.mapValues { (_, list) ->
            (list.firstOrNull { !PaymentMethod.isFree(it.method) } ?: list.first()).method
        }
}
