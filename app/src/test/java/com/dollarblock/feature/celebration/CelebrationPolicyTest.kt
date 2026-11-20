package com.dollarblock.feature.celebration

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class CelebrationPolicyTest {

    private val hour = 60 * 60 * 1000L

    @Test
    fun `first refusal ever celebrates`() {
        assertTrue(CelebrationPolicy.isDue(lastShownAt = null, now = 0))
    }

    @Test
    fun `within two hours goes straight to the launcher`() {
        assertFalse(CelebrationPolicy.isDue(lastShownAt = 0, now = hour))
        assertFalse(CelebrationPolicy.isDue(lastShownAt = 0, now = 2 * hour - 1))
    }

    @Test
    fun `after two hours celebrates again`() {
        assertTrue(CelebrationPolicy.isDue(lastShownAt = 0, now = 2 * hour))
    }

    @Test
    fun `clock moved back does not lock the celebration`() {
        assertTrue(CelebrationPolicy.isDue(lastShownAt = 10 * hour, now = hour))
    }

    @Test
    fun `variant never repeats the last one`() {
        repeat(100) { seed ->
            assertNotEquals(3, CelebrationPolicy.pickVariant(count = 8, last = 3, random = Random(seed)))
        }
    }

    @Test
    fun `variant covers every option`() {
        val seen = (0 until 300).map { CelebrationPolicy.pickVariant(8, last = null, random = Random(it)) }.toSet()
        assertTrue(seen == (0 until 8).toSet())
    }
}
