package com.dollarblock.feature.statistics

import com.dollarblock.domain.model.AppDay
import com.dollarblock.domain.model.PaymentMethod
import com.dollarblock.domain.model.UnlockDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StatementCalendarTest {

    private val today = 100L
    private val insta = CalendarApp("insta", dailyLimitMinutes = 60, firstMonitoredDay = 95L)
    private val tiktok = CalendarApp("tiktok", dailyLimitMinutes = 30, firstMonitoredDay = 95L)
    private fun min(m: Int) = m * 60_000L

    private fun tone(
        app: CalendarApp = insta,
        day: Long = 98L,
        used: Long? = null,
        blocked: Boolean = false,
        unlock: String? = null,
    ) = StatementCalendar.appTone(app, day, today, used, blocked, unlock)

    @Test
    fun `uso abaixo do limite e verde`() = assertEquals(DayTone.WITHIN, tone(used = min(59)))

    @Test
    fun `uso exatamente no limite ainda e verde`() = assertEquals(DayTone.WITHIN, tone(used = min(60)))

    @Test
    fun `dia monitorado sem linha de uso conta como zero`() = assertEquals(DayTone.WITHIN, tone(used = null))

    @Test
    fun `bloqueio sem recibo e ambar`() = assertEquals(DayTone.HELD, tone(used = min(60), blocked = true))

    @Test
    fun `recibo pago e vermelho mesmo com bloqueio`() =
        assertEquals(DayTone.PAID, tone(used = min(90), blocked = true, unlock = PaymentMethod.PLAY_BILLING))

    @Test
    fun `passe livre e cortesia sao gratis`() {
        assertEquals(DayTone.FREE, tone(used = min(90), unlock = PaymentMethod.FREE_PASS))
        assertEquals(DayTone.FREE, tone(used = min(90), unlock = PaymentMethod.COURTESY))
    }

    @Test
    fun `antes do monitoramento passar do limite e OVER`() =
        assertEquals(DayTone.OVER, tone(day = 90L, used = min(120)))

    @Test
    fun `antes do monitoramento sem dado e vazio`() = assertEquals(DayTone.EMPTY, tone(day = 90L, used = null))

    @Test
    fun `app sem limite e dia futuro sao vazios`() {
        assertEquals(DayTone.EMPTY, tone(app = insta.copy(dailyLimitMinutes = null), used = min(500)))
        assertEquals(DayTone.EMPTY, tone(day = 101L, used = min(1)))
    }

    @Test
    fun `cor do dia e a do pior app`() {
        val days = StatementCalendar.days(
            days = 98L..98L,
            today = today,
            apps = listOf(insta, tiktok),
            usage = mapOf(AppDay("insta", 98L) to min(10), AppDay("tiktok", 98L) to min(30)),
            blocks = setOf(AppDay("tiktok", 98L)),
            unlocks = emptyList(),
        )
        assertEquals(DayTone.HELD, days.single().tone)
        assertEquals(min(40), days.single().usedMillis)
    }

    @Test
    fun `dia so com backfill fica marcado como antes do DollarBlock`() {
        val days = StatementCalendar.days(
            days = 93L..95L,
            today = today,
            apps = listOf(insta),
            usage = mapOf(AppDay("insta", 93L) to min(10), AppDay("insta", 95L) to min(10)),
            blocks = emptySet(),
            unlocks = emptyList(),
        )
        assertTrue(days[0].beforeDollarBlock)
        assertEquals(DayTone.EMPTY, days[1].tone)
        assertFalse(days[1].beforeDollarBlock)
        assertFalse(days[2].beforeDollarBlock)
    }

    @Test
    fun `recibo pago prevalece sobre gratis no mesmo dia`() {
        val days = StatementCalendar.days(
            days = 98L..98L,
            today = today,
            apps = listOf(insta),
            usage = emptyMap(),
            blocks = emptySet(),
            unlocks = listOf(
                UnlockDay(AppDay("insta", 98L), "0.00", PaymentMethod.FREE_PASS),
                UnlockDay(AppDay("insta", 98L), "1.00", PaymentMethod.PLAY_BILLING),
            ),
        )
        assertEquals(DayTone.PAID, days.single().tone)
    }

    private fun day(d: Long, tone: DayTone) = CalendarDay(d, tone, beforeDollarBlock = false, usedMillis = 0)

    @Test
    fun `sequencia conta dias dentro ate o primeiro fora`() {
        val history = listOf(
            day(96, DayTone.PAID),
            day(97, DayTone.WITHIN),
            day(98, DayTone.HELD),
            day(99, DayTone.WITHIN),
            day(100, DayTone.WITHIN),
        )
        assertEquals(4, StatementCalendar.streak(history, today))
    }

    @Test
    fun `hoje vazio nao quebra a sequencia`() {
        val history = listOf(day(98, DayTone.WITHIN), day(99, DayTone.WITHIN), day(100, DayTone.EMPTY))
        assertEquals(2, StatementCalendar.streak(history, today))
    }

    @Test
    fun `hoje fora zera a sequencia`() {
        val history = listOf(day(99, DayTone.WITHIN), day(100, DayTone.FREE))
        assertEquals(0, StatementCalendar.streak(history, today))
    }

    @Test
    fun `contagem dentro ignora dias vazios`() {
        val days = listOf(day(1, DayTone.WITHIN), day(2, DayTone.EMPTY), day(3, DayTone.OVER), day(4, DayTone.HELD))
        assertEquals(2 to 3, StatementCalendar.withinCount(days))
    }
}
