package com.dollarblock.core.designsystem.mascot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class MascotChoreographyTest {

    private val width = 450f
    private val ground = 160f
    private val acts = HomeActKind.entries.flatMap { kind -> listOf(HomeAct(kind, -1), HomeAct(kind, 1)) }

    /** Fora da vista: nenhum pedaço da figura (corpo, braços, lupa) dentro da cena. */
    private fun offScreen(pose: MascotPose) =
        pose.x < -MascotChoreography.FIGURE_HALF_WIDTH || pose.x > width + MascotChoreography.FIGURE_HALF_WIDTH

    @Test
    fun `every home act starts and ends off screen`() {
        acts.forEach { act ->
            val duration = MascotChoreography.homeDuration(act, width)
            assertTrue("$act start", offScreen(MascotChoreography.homePose(act, 0f, width, ground)))
            assertTrue("$act end", offScreen(MascotChoreography.homePose(act, duration, width, ground)))
        }
    }

    @Test
    fun `every home act comes into view at some point`() {
        acts.forEach { act ->
            val duration = MascotChoreography.homeDuration(act, width)
            val seen = (0..100).any { i ->
                val x = MascotChoreography.homePose(act, duration * i / 100f, width, ground).x
                x in -30f..(width + 30f)
            }
            assertTrue("$act never shows up", seen)
        }
    }

    @Test
    fun `home acts move smoothly frame to frame`() {
        acts.forEach { act ->
            val duration = MascotChoreography.homeDuration(act, width)
            var t = 0f
            var last = MascotChoreography.homePose(act, 0f, width, ground)
            while (t < duration) {
                t += 1f / 60f
                val pose = MascotChoreography.homePose(act, t, width, ground)
                assertTrue("$act jumps at $t", abs(pose.x - last.x) < 8f)
                assertTrue("$act lean jumps at $t", abs(pose.lean - last.lean) < 3f)
                assertEquals(ground, pose.y, 0.001f)
                last = pose
            }
        }
    }

    @Test
    fun `cross walks from the entry side to the other side`() {
        val fromLeft = HomeAct(HomeActKind.CROSS, -1)
        val duration = MascotChoreography.homeDuration(fromLeft, width)
        assertTrue(MascotChoreography.homePose(fromLeft, 0f, width, ground).x < 0f)
        assertTrue(MascotChoreography.homePose(fromLeft, duration, width, ground).x > width)
        assertEquals(1f, MascotChoreography.homePose(fromLeft, 1f, width, ground).facing)
    }

    @Test
    fun `next act never repeats the previous kind`() {
        val random = Random(42)
        var previous: HomeAct? = null
        repeat(200) {
            val next = MascotChoreography.nextHomeAct(previous, random)
            if (previous != null) assertNotEquals(previous!!.kind, next.kind)
            previous = next
        }
    }

    @Test
    fun `gaps keep the mascot occasional`() {
        val random = Random(7)
        repeat(100) {
            assertTrue(MascotChoreography.homeGapSeconds(random) in 12f..25f)
        }
    }

    @Test
    fun `reaction loops close without a seam`() {
        listOf(true to MascotChoreography.RAISED_PERIOD, false to MascotChoreography.CUT_PERIOD).forEach { (increased, period) ->
            val a = MascotChoreography.reaction(increased, 0.001f, 100f, 120f)
            val b = MascotChoreography.reaction(increased, period * 3 + 0.001f, 100f, 120f)
            assertEquals(a.tilt, b.tilt, 0.5f)
            assertEquals(a.lean, b.lean, 0.5f)
            assertEquals(a.frontArm.shoulder, b.frontArm.shoulder, 1f)
            assertEquals(a.question, b.question, 0.01f)
        }
    }

    @Test
    fun `raised limit shows doubt and cut limit shows approval`() {
        val raised = (0..64).map { MascotChoreography.reaction(true, it / 10f, 100f, 120f) }
        assertTrue(raised.any { it.question > 0.9f && it.quirk > 0.9f })
        assertTrue(raised.any { it.frontArm == ArmPose.OnHip && it.brow < 0f })

        val cut = (0..52).map { MascotChoreography.reaction(false, it / 10f, 100f, 120f) }
        assertTrue(cut.any { it.frontArm.shoulder > 130f && it.backArm.shoulder > 130f })
        assertTrue(cut.none { it.question > 0f })
    }

    @Test
    fun `still poses carry the reaction on their own`() {
        val raised = MascotChoreography.reactionStill(true, 100f, 120f)
        assertEquals(1f, raised.question)
        assertEquals(ArmPose.ScratchHead, raised.frontArm)
        val cut = MascotChoreography.reactionStill(false, 100f, 120f)
        assertEquals(ArmPose.Cheer, cut.frontArm)
        assertEquals(ArmPose.Cheer, cut.backArm)
    }
}
