package com.dollarblock.core.designsystem.mascot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class MascotChoreographyTest {

    private val width = 450f
    private val ground = 160f
    private val stage = HomeStage(width, ground, numberStart = 150f, numberEnd = 330f)
    private val acts = HomeActKind.entries.flatMap { kind -> listOf(HomeAct(kind, -1), HomeAct(kind, 1)) }

    /** Fora da vista: nenhum pedaço da figura (corpo, braços, objetos) dentro da cena. */
    private fun offScreen(pose: MascotPose) =
        pose.x < -MascotChoreography.FIGURE_HALF_WIDTH || pose.x > width + MascotChoreography.FIGURE_HALF_WIDTH

    private fun frames(act: HomeAct, step: Float = 1f / 60f): List<MascotPose> {
        val duration = MascotChoreography.homeDuration(act, stage)
        return generateSequence(0f) { it + step }.takeWhile { it <= duration }
            .map { MascotChoreography.homePose(act, it, stage) }.toList()
    }

    @Test
    fun `the first act is always the peek`() {
        repeat(50) { seed ->
            assertEquals(HomeActKind.PEEK, MascotChoreography.nextHomeAct(emptyList(), Random(seed)).kind)
        }
    }

    @Test
    fun `every home act starts and ends off screen`() {
        acts.forEach { act ->
            val duration = MascotChoreography.homeDuration(act, stage)
            assertTrue("$act start", offScreen(MascotChoreography.homePose(act, 0f, stage)))
            assertTrue("$act end", offScreen(MascotChoreography.homePose(act, duration, stage)))
        }
    }

    @Test
    fun `every home act comes into view at some point`() {
        acts.forEach { act ->
            assertTrue("$act never shows up", frames(act, 0.1f).any { it.x in -30f..(width + 30f) })
        }
    }

    @Test
    fun `home acts move smoothly frame to frame`() {
        acts.forEach { act ->
            frames(act).zipWithNext().forEachIndexed { i, (last, pose) ->
                assertTrue("$act jumps at frame $i", abs(pose.x - last.x) < 8f)
                assertTrue("$act lean jumps at frame $i", abs(pose.lean - last.lean) < 3f)
                assertTrue("$act tilt jumps at frame $i", abs(pose.tilt - last.tilt) < 3f)
                assertTrue("$act prop jumps at frame $i", abs(pose.propOpen - last.propOpen) < 0.1f)
                assertEquals(ground, pose.y, 0.001f)
            }
        }
    }

    @Test
    fun `cross walks from the entry side to the other side`() {
        val fromLeft = HomeAct(HomeActKind.CROSS, -1)
        val duration = MascotChoreography.homeDuration(fromLeft, stage)
        assertTrue(MascotChoreography.homePose(fromLeft, 0f, stage).x < 0f)
        assertTrue(MascotChoreography.homePose(fromLeft, duration, stage).x > width)
        assertEquals(1f, MascotChoreography.homePose(fromLeft, 1f, stage).facing)
    }

    @Test
    fun `bump stops right at the edge of the number`() {
        listOf(-1 to stage.numberStart, 1 to stage.numberEnd).forEach { (side, edge) ->
            val poses = frames(HomeAct(HomeActKind.BUMP, side))
            val closest = poses.minOf { abs(it.x - edge) }
            assertEquals("side $side", MascotChoreography.BUMP_REACH, closest, 0.5f)
            // Nunca entra por trás do número.
            assertTrue(poses.none { if (side < 0) it.x > edge else it.x < edge })
            // O susto: "!" no ar e o corpo jogado para trás.
            assertTrue(poses.any { it.exclaim > 0.9f && it.lean < -5f })
            assertTrue(poses.all { it.prop == MascotProp.PHONE })
        }
    }

    @Test
    fun `bump never leaves the screen when the number is huge`() {
        val wide = HomeStage(width, ground, numberStart = 4f, numberEnd = width - 4f)
        listOf(-1, 1).forEach { side ->
            val act = HomeAct(HomeActKind.BUMP, side)
            val duration = MascotChoreography.homeDuration(act, wide)
            val seen = (0..100).any { MascotChoreography.homePose(act, duration * it / 100f, wide).x in 0f..width }
            assertTrue(seen)
        }
    }

    @Test
    fun `scenes act out what they promise`() {
        fun all(kind: HomeActKind) = frames(HomeAct(kind, -1), 0.05f)

        assertTrue("bump walks looking at the phone", all(HomeActKind.BUMP).any { it.lookDown > 0.99f && it.walk > 0.5f })
        assertTrue("wave raises the hand", all(HomeActKind.WAVE).any { it.frontArm.shoulder > 100f })
        assertTrue("coffee sips", all(HomeActKind.COFFEE).any { it.frontArm.shoulder > 45f && it.prop == MascotProp.MUG })
        assertTrue("sweep sweeps", all(HomeActKind.SWEEP).any { it.propOpen > 0.99f && it.prop == MascotProp.BROOM })
    }

    @Test
    fun `visits stop beside the number, not behind it`() {
        listOf(HomeActKind.WAVE, HomeActKind.COFFEE, HomeActKind.SWEEP).forEach { kind ->
            listOf(-1, 1).forEach { side ->
                val act = HomeAct(kind, side)
                val duration = MascotChoreography.homeDuration(act, stage)
                val middle = MascotChoreography.homePose(act, duration / 2f, stage)
                assertTrue("$act at ${middle.x}", middle.x < width * 0.25f || middle.x > width * 0.75f)
            }
        }
    }

    @Test
    fun `next act skips the recent ones and visits all of them`() {
        val random = Random(42)
        val recent = ArrayDeque<HomeActKind>()
        val seen = mutableSetOf<HomeActKind>()
        repeat(300) { i ->
            val next = MascotChoreography.nextHomeAct(recent, random)
            if (i == 0) assertEquals(HomeActKind.PEEK, next.kind)
            assertFalse("$next repeated $recent", next.kind in recent)
            seen += next.kind
            recent.addLast(next.kind)
            if (recent.size > MascotChoreography.RECENT_MEMORY) recent.removeFirst()
        }
        assertEquals(HomeActKind.entries.toSet(), seen)
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
