package com.dollarblock.feature.blocking.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class BlockSceneTest {

    @Test
    fun `never repeats the previous scene`() {
        val random = Random(42)
        BlockScene.entries.forEach { last ->
            repeat(500) { assertNotEquals(last, pickBlockScene(last, random)) }
        }
    }

    @Test
    fun `a chain of picks shows all three scenes and never the same twice in a row`() {
        val random = Random(7)
        var last: BlockScene? = null
        val seen = mutableSetOf<BlockScene>()
        repeat(6) {
            val next = pickBlockScene(last, random)
            assertNotEquals(last, next)
            seen += next
            last = next
        }
        assertEquals(BlockScene.entries.toSet(), seen)
    }

    @Test
    fun `distribution is roughly uniform over 3000 picks`() {
        val random = Random(2026)
        var last: BlockScene? = null
        val counts = BlockScene.entries.associateWith { 0 }.toMutableMap()
        repeat(3_000) {
            val next = pickBlockScene(last, random)
            counts[next] = counts.getValue(next) + 1
            last = next
        }
        counts.values.forEach { assertTrue("contagens $counts", abs(it - 1_000) < 100) }
    }

    @Test
    fun `null last picks any of the three uniformly`() {
        val random = Random(1)
        val counts = BlockScene.entries.associateWith { 0 }.toMutableMap()
        repeat(3_000) {
            val next = pickBlockScene(null, random)
            counts[next] = counts.getValue(next) + 1
        }
        counts.values.forEach { assertTrue("contagens $counts", abs(it - 1_000) < 100) }
    }

    @Test
    fun `re-assert of the same app inside the window keeps the scene and the entrance start`() {
        val first = resolveBlockScene(null, "com.instagram.android", 10_000L, Random(3))
        val again = resolveBlockScene(first, "com.instagram.android", 10_500L, Random(3))
        assertSame(first, again)
    }

    @Test
    fun `a new opening after the window or for another app picks a different scene`() {
        val first = resolveBlockScene(null, "com.instagram.android", 10_000L, Random(3))
        val later = resolveBlockScene(first, "com.instagram.android", 10_000L + BLOCK_SCENE_REASSERT_WINDOW_MS, Random(3))
        assertNotEquals(first.scene, later.scene)
        assertEquals(10_000L + BLOCK_SCENE_REASSERT_WINDOW_MS, later.startedAtMs)
        val other = resolveBlockScene(first, "com.tiktok", 10_100L, Random(3))
        assertNotEquals(first.scene, other.scene)
    }

    @Test
    fun `forced scene wins and restarts the entrance`() {
        val first = resolveBlockScene(null, "pkg", 10_000L, Random(3))
        val forced = resolveBlockScene(first, "pkg", 10_100L, Random(3), forced = first.scene)
        assertEquals(first.scene, forced.scene)
        assertEquals(10_100L, forced.startedAtMs)
    }

    @Test
    fun `debug extra parses A B C and ignores anything else`() {
        assertEquals(BlockScene.A_MASCOT, BlockScene.fromExtra("A"))
        assertEquals(BlockScene.B_BEHIND, BlockScene.fromExtra("b"))
        assertEquals(BlockScene.C_STAMP, BlockScene.fromExtra(" C "))
        assertNull(BlockScene.fromExtra(null))
        assertNull(BlockScene.fromExtra("D"))
    }
}
