package com.dollarblock.data.usage

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class LocalDayFlowTest {

    private val zone = ZoneId.of("America/Sao_Paulo")
    private val nov3 = LocalDate.of(2026, 11, 3).toEpochDay()

    private fun millisOf(dt: LocalDateTime) = dt.atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `emite o novo dia quando a meia-noite local passa`() = runTest {
        // 23:59:30 — 30 s antes da virada; o relógio "real" avança junto com o tempo virtual.
        val start = millisOf(LocalDateTime.of(2026, 11, 3, 23, 59, 30))
        val t0 = currentTime

        val days = localEpochDayFlow(
            nowMs = { start + (currentTime - t0) },
            zone = { zone },
        ).take(2).toList()

        assertEquals(listOf(nov3, nov3 + 1), days)
    }

    @Test
    fun `nao re-emite o mesmo dia a cada tick`() = runTest {
        val start = millisOf(LocalDateTime.of(2026, 11, 3, 8, 0))
        val t0 = currentTime

        val days = localEpochDayFlow(
            nowMs = { start + (currentTime - t0) },
            zone = { zone },
        ).take(2).toList()

        // Mesmo começando às 08:00 e tickando a cada 60 s, só emite de novo no dia seguinte.
        assertEquals(listOf(nov3, nov3 + 1), days)
    }

    @Test
    fun `millis ate a meia-noite`() {
        val now = millisOf(LocalDateTime.of(2026, 11, 3, 23, 0))
        assertEquals(3_600_000L, millisUntilNextLocalMidnight(now, zone))
    }
}
