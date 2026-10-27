package com.dollarblock.service.nudge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class NudgeTest {

    // --- Projeções anuais -----------------------------------------------------------

    @Test
    fun `30 min por dia viram 183 h, 7 dias, 26 livros e 2 maratonas por ano`() {
        assertEquals(183, NudgeMath.annualHours(30.0))
        assertEquals(7, NudgeMath.annualDays(30.0))
        assertEquals(26, NudgeMath.books(30.0))
        assertEquals(2, NudgeMath.marathons(30.0))
        assertEquals(26, NudgeMath.spanishPercent(30.0))
    }

    @Test
    fun `sem historico ou com media baixa nao ha projecao`() {
        assertTrue(NudgeMath.eligibleProjections(null, annualItemAvailable = true).isEmpty())
        assertTrue(NudgeMath.eligibleProjections(10.0, annualItemAvailable = true).isEmpty())
    }

    @Test
    fun `projecao so entra quando o numero e crivel`() {
        // 15 min/dia: 91 h/ano → 3 dias, 13 livros, 1 maratona, 13% do curso.
        val pool = NudgeMath.eligibleProjections(15.0, annualItemAvailable = false)
        assertTrue(Nudge.B6_DAYS_AWAKE in pool)
        assertTrue(Nudge.B8_MARATHONS in pool)
        assertFalse("sem item anual em unidades inteiras", Nudge.B10_ANNUAL_BILL in pool)
        // 5 h/dia: 1825 h/ano passa de um curso inteiro de espanhol → B11 fora.
        assertFalse(Nudge.B11_SPANISH in NudgeMath.eligibleProjections(300.0, annualItemAvailable = true))
    }

    @Test
    fun `media diaria exige ao menos 3 dias de registro`() {
        assertNull(NudgeMath.averageDailyMinutes(listOf(60_000L, 120_000L)))
        assertEquals(2.0, NudgeMath.averageDailyMinutes(listOf(60_000L, 120_000L, 180_000L))!!, 0.001)
    }

    @Test
    fun `valor anual multiplica a media pelo preco do minuto`() {
        assertEquals(365.0, NudgeMath.annualValue(avgDailyMinutes = 10.0, perMinuteRate = 0.1), 0.001)
    }

    // --- Débito automático (horário habitual) -----------------------------------------

    @Test
    fun `detecta habito quando 5 de 7 dias abrem perto do mesmo horario`() {
        val sevenAm = 7 * 60
        val days = listOf(sevenAm, sevenAm + 10, sevenAm - 20, sevenAm + 30, sevenAm + 5, 13 * 60, 22 * 60)
        assertEquals(sevenAm + 10, NudgeMath.habitMinuteOfDay(days)) // mediana
    }

    @Test
    fun `sem padrao nao ha habito`() {
        assertNull(NudgeMath.habitMinuteOfDay(listOf(7 * 60, 10 * 60, 13 * 60, 16 * 60, 19 * 60, 22 * 60)))
        assertNull("poucos dias", NudgeMath.habitMinuteOfDay(listOf(7 * 60, 7 * 60, 7 * 60)))
    }

    // --- Frequência --------------------------------------------------------------------

    @Test
    fun `teto diario de 2 cobrancas`() {
        assertFalse(NudgePolicy.capReached(1))
        assertTrue(NudgePolicy.capReached(2))
    }

    @Test
    fun `intervalo entre repeticoes`() {
        assertTrue("nunca disparou", NudgePolicy.cooledDown(null, today = 100, cooldownDays = 3))
        assertFalse(NudgePolicy.cooledDown(98, today = 100, cooldownDays = 3))
        assertTrue(NudgePolicy.cooledDown(97, today = 100, cooldownDays = 3))
    }

    @Test
    fun `sorteio nunca repete a ultima quando ha alternativa`() {
        val pool = listOf(Nudge.A1_WALK, Nudge.A2_JUST_CHECKING)
        repeat(50) { seed ->
            assertEquals(Nudge.A2_JUST_CHECKING, NudgePolicy.pick(pool, last = Nudge.A1_WALK, random = Random(seed)))
        }
        assertEquals(Nudge.A1_WALK, NudgePolicy.pick(listOf(Nudge.A1_WALK), last = Nudge.A1_WALK))
        assertNull(NudgePolicy.pick(emptyList(), last = null))
    }

    @Test
    fun `fechamento do dia escolhe pelo resultado`() {
        assertEquals(listOf(Nudge.D18_CLOSING, Nudge.D19_WONT_RETURN), NudgePolicy.dailyClosePool(true, true, hasTopApp = true))
        assertEquals(listOf(Nudge.D18_CLOSING), NudgePolicy.dailyClosePool(true, true, hasTopApp = false))
        assertEquals(listOf(Nudge.D20_CLEAN_DAY), NudgePolicy.dailyClosePool(false, hasLimits = true, hasTopApp = true))
        assertEquals(listOf(Nudge.D18_CLOSING), NudgePolicy.dailyClosePool(false, hasLimits = false, hasTopApp = true))
    }
}
