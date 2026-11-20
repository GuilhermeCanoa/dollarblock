package com.dollarblock.feature.blocking.stats

import com.dollarblock.feature.statistics.DayTone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class BlockStatTest {

    private fun input(
        attempts: Int = 2,
        used: Int = 50,
        limit: Int? = 45,
        untilMidnight: Int = 300,
        streak: Int = 0,
        saved: Double? = 0.0,
    ) = BlockStatInput(attempts, used, limit, untilMidnight, streak, saved)

    @Test
    fun `streak beats every other kind`() {
        val kind = pickBlockStat(input(attempts = 9, used = 200, streak = 3), last = null, random = Random(1))
        assertEquals(BlockStatKind.STREAK, kind)
    }

    @Test
    fun `third attempt shows attempts`() {
        assertEquals(BlockStatKind.ATTEMPTS, pickBlockStat(input(attempts = 3), null, Random(1)))
    }

    @Test
    fun `first attempt of the day is called out`() {
        assertEquals(BlockStatKind.FIRST_TODAY, pickBlockStat(input(attempts = 1), null, Random(1)))
    }

    @Test
    fun `big overage needs 15 min and half the limit`() {
        // 30 min de limite, 20 acima: >=15 e >=50% → excedente.
        assertEquals(BlockStatKind.OVERAGE, pickBlockStat(input(used = 50, limit = 30), null, Random(1)))
        // 120 min de limite, 20 acima: não chega a 50%.
        assertTrue(BlockStatKind.OVERAGE !in BlockStatRules.prioritized(input(used = 140, limit = 120)))
        // 10 min de limite, 10 acima: 100%, mas < 15 min.
        assertTrue(BlockStatKind.OVERAGE !in BlockStatRules.prioritized(input(used = 20, limit = 10)))
    }

    @Test
    fun `never repeats the previous kind when there is another option`() {
        val busy = input(attempts = 5, streak = 4)
        assertEquals(BlockStatKind.ATTEMPTS, pickBlockStat(busy, last = BlockStatKind.STREAK, random = Random(1)))
        repeat(50) { seed ->
            val kind = pickBlockStat(input(), last = BlockStatKind.COUNTDOWN, random = Random(seed))
            assertNotEquals(BlockStatKind.COUNTDOWN, kind)
        }
    }

    @Test
    fun `fallback skips usage without a limit and money without savings`() {
        val pool = BlockStatRules.fallback(input(limit = null, saved = 0.0))
        assertEquals(listOf(BlockStatKind.COUNTDOWN), pool)
        // Única opção: repete em vez de quebrar.
        assertEquals(BlockStatKind.COUNTDOWN, pickBlockStat(input(limit = null, saved = 0.0), BlockStatKind.COUNTDOWN, Random(1)))
    }

    @Test
    fun `month saved joins the pool only with money saved`() {
        assertTrue(BlockStatKind.MONTH_SAVED in BlockStatRules.fallback(input(saved = 3.0)))
        assertTrue(BlockStatKind.MONTH_SAVED !in BlockStatRules.fallback(input(saved = null)))
    }

    @Test
    fun `only month saved opens home`() {
        BlockStatKind.entries.forEach {
            val expected = if (it == BlockStatKind.MONTH_SAVED) BlockStatTarget.HOME else BlockStatTarget.STATEMENT_TODAY
            assertEquals(expected, it.target)
        }
    }

    @Test
    fun `streak counts within and held days back from yesterday`() {
        val tones = listOf(DayTone.PAID, DayTone.WITHIN, DayTone.HELD, DayTone.WITHIN)
        assertEquals(3, streakEndingYesterday(tones))
        assertEquals(0, streakEndingYesterday(listOf(DayTone.WITHIN, DayTone.FREE)))
        assertEquals(0, streakEndingYesterday(listOf(DayTone.WITHIN, DayTone.EMPTY)))
        assertEquals(0, streakEndingYesterday(emptyList()))
    }

    @Test
    fun `reassertion keeps the kind and a new opening rotates`() {
        val first = BlockStatRotation.next("pkg.a", input(), nowMs = 10_000, random = Random(3))
        assertEquals(first, BlockStatRotation.next("pkg.a", input(), nowMs = 10_500, random = Random(4)))
        val later = BlockStatRotation.next("pkg.a", input(), nowMs = 60_000, random = Random(5))
        assertNotEquals(first, later)
    }
}
