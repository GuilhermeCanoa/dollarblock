package com.dollarblock.data.usage

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import java.time.Instant
import java.time.ZoneId

/**
 * "Hoje" reativo: emite o epochDay local atual e emite de novo quando a meia-noite passa
 * (ou o fuso muda). Use no lugar de calcular `LocalDate.now()` uma única vez ao montar um
 * Flow — o processo do app vive por dias, e um dia capturado na criação congela a tela
 * no dia em que ela foi aberta (bug do "contador não zera").
 *
 * Reavalia o relógio a cada [maxTickMs] no máximo, em vez de um único `delay` até a
 * meia-noite: o `delay` corre em relógio monotônico, que para durante o sono profundo do
 * aparelho, então um delay longo acordaria horas depois da virada.
 */
fun localEpochDayFlow(
    nowMs: () -> Long = System::currentTimeMillis,
    zone: () -> ZoneId = ZoneId::systemDefault,
    maxTickMs: Long = 60_000L,
): Flow<Long> = flow {
    while (true) {
        val now = nowMs()
        val z = zone()
        emit(epochDayAt(now, z))
        delay(millisUntilNextLocalMidnight(now, z).coerceIn(1L, maxTickMs))
    }
}.distinctUntilChanged()

/** epochDay local de [epochMillis] no fuso [zone]. */
fun epochDayAt(epochMillis: Long, zone: ZoneId): Long =
    Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate().toEpochDay()

/** Millis de [epochMillis] até a próxima meia-noite local em [zone] (sempre > 0). */
fun millisUntilNextLocalMidnight(epochMillis: Long, zone: ZoneId): Long {
    val nextMidnight = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()
        .plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    return nextMidnight - epochMillis
}
