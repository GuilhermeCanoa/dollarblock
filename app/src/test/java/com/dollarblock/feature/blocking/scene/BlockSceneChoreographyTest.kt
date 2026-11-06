package com.dollarblock.feature.blocking.scene

import com.dollarblock.core.designsystem.mascot.MascotPose
import com.dollarblock.core.designsystem.mascot.shieldMascotHand
import com.dollarblock.feature.blocking.scene.BlockSceneChoreography.Held
import com.dollarblock.feature.blocking.scene.BlockSceneChoreography.Hero
import com.dollarblock.feature.blocking.scene.BlockSceneChoreography.Stamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class BlockSceneChoreographyTest {

    private val scenes = BlockScene.entries.map { BlockSceneChoreography.forScene(it) }

    /** Poses iguais a menos de [eps] em todos os campos numéricos (a mola e o lerp deixam resto). */
    private fun assertClose(message: String, a: MascotPose, b: MascotPose, eps: Float = 0.05f) {
        fun close(x: Float, y: Float) = abs(x - y) <= eps
        val ok = close(a.x, b.x) && close(a.y, b.y) && a.facing == b.facing && close(a.lean, b.lean) &&
            close(a.tilt, b.tilt) && close(a.brow, b.brow) && close(a.quirk, b.quirk) &&
            close(a.look, b.look) && close(a.lookDown, b.lookDown) &&
            close(a.frontArm.shoulder, b.frontArm.shoulder) && close(a.frontArm.elbow, b.frontArm.elbow) &&
            close(a.backArm.shoulder, b.backArm.shoulder) && close(a.backArm.elbow, b.backArm.elbow) &&
            close(a.walk, b.walk) && a.prop == b.prop && close(a.propOpen, b.propOpen) &&
            close(a.propPhase, b.propPhase) && close(a.glint, b.glint)
        assertTrue("$message\n  a=$a\n  b=$b", ok)
    }

    @Test
    fun `entrance ends exactly on the still pose`() {
        scenes.forEach { s ->
            assertEquals(s.still(), s.entrance(s.entranceSeconds))
            assertEquals(s.still(), s.entrance(s.entranceSeconds + 5f))
        }
    }

    @Test
    fun `entrance arrives at the still pose without a jump`() {
        scenes.forEach { s ->
            assertClose("${s::class.simpleName} fim da entrada", s.entrance(s.entranceSeconds - 0.001f), s.still())
        }
    }

    @Test
    fun `entrance has no jumps between frames`() {
        scenes.forEach { s ->
            var prev = s.entrance(0f)
            var t = 1f / 60f
            while (t <= s.entranceSeconds) {
                val now = s.entrance(t)
                // A virada de frente (cena A) é o único salto permitido.
                if (now.facing == prev.facing) {
                    assertTrue("${s::class.simpleName} t=$t tilt", abs(now.tilt - prev.tilt) < 4f)
                    assertTrue("${s::class.simpleName} t=$t brow", abs(now.brow - prev.brow) < 0.4f)
                }
                prev = now
                t += 1f / 60f
            }
        }
    }

    @Test
    fun `idle gestures start and end on the still pose`() {
        scenes.forEach { s ->
            s.gestures.forEach { g ->
                assertClose("${s::class.simpleName} $g início", s.idle(g, 0f), s.still())
                assertClose("${s::class.simpleName} $g fim", s.idle(g, s.gestureSeconds(g)), s.still())
            }
        }
    }

    @Test
    fun `idle gestures last between 1 and 2 seconds and gaps between 6 and 12`() {
        scenes.forEach { s -> s.gestures.forEach { assertTrue(s.gestureSeconds(it) in 1f..2f) } }
        val random = kotlin.random.Random(9)
        repeat(200) { assertTrue(BlockSceneChoreography.gapSeconds(random) in 6f..12f) }
    }

    @Test
    fun `controls are fully visible within one second`() {
        scenes.forEach { s -> assertEquals(1f, s.controlsAlpha(1.0f), 0f) }
    }

    @Test
    fun `haptics happen inside the entrance`() {
        scenes.forEach { s -> s.hapticAt.forEach { assertTrue(it > 0f && it <= s.entranceSeconds) } }
    }

    @Test
    fun `stamps start big and invisible and land at 1`() {
        assertEquals(0f, Held.stampAlpha(0f), 0f)
        assertEquals(2.4f, Held.stampScale(Held.STAMP_START), 0f)
        assertEquals(1f, Held.stampScale(Held.entranceSeconds), 0f)
        assertEquals(1f, Held.stampAlpha(Held.entranceSeconds), 0f)
        assertEquals(0f, Stamp.stampAlpha(Stamp.IMPACT - 0.01f), 0f)
        assertEquals(1.6f, Stamp.stampScale(Stamp.IMPACT - 0.01f), 0f)
        assertEquals(1f, Stamp.stampScale(Stamp.entranceSeconds), 0f)
        // O impacto (haptic) é quando a mola cruza 1 pela primeira vez.
        assertEquals(1f, BlockSceneChoreography.spring(2.4f, 1f, BlockSceneChoreography.springImpactSeconds()), 0.01f)
    }

    @Test
    fun `scene B rises from below and settles`() {
        assertEquals(1f, Held.rise(0f), 0f)
        assertEquals(0f, Held.rise(0.6f), 0f)
        assertEquals(0f, Held.paperRotation(Held.entranceSeconds), 0f)
        assertEquals(0f, Held.tremorDp(Held.entranceSeconds), 0f)
    }

    @Test
    fun `scene C prints the receipt and shakes only at the slam`() {
        assertEquals(0f, Stamp.reveal(0f), 0f)
        assertEquals(1f, Stamp.reveal(0.7f), 0f)
        assertEquals(0f, Stamp.shakeDp(Stamp.IMPACT - 0.01f), 0f)
        assertEquals(0f, Stamp.shakeDp(Stamp.IMPACT + 0.2f), 0f)
    }

    @Test
    fun `scene B hands sit on the top edge of the receipt`() {
        val hand = shieldMascotHand(Held.still(), front = true)
        assertEquals(Held.RECEIPT_TOP, hand.y, 1.5f)
        assertTrue(abs(hand.x) < Held.RIGHT)
    }

    @Test
    fun `poses stay inside the stage once the mascot has arrived`() {
        fun inside(pose: MascotPose, left: Float, right: Float, label: String) {
            listOf(true, false).forEach { front ->
                val h = shieldMascotHand(pose, front)
                assertTrue("$label mão x=${h.x}", h.x in left..right)
            }
            assertTrue("$label corpo x=${pose.x}", pose.x - 23f >= left && pose.x + 23f <= right)
        }
        val stages = mapOf(
            Hero to (Hero.LEFT to Hero.RIGHT),
            Held to (Held.LEFT to Held.RIGHT),
            // A cena C deixa o mascote passar um pouco da caixa (o mock o encosta na borda).
            Stamp to (Stamp.LEFT to Stamp.RIGHT + 12f),
        )
        stages.forEach { (s, range) ->
            val label = s::class.simpleName.orEmpty()
            val arrived = when (s) {
                Hero -> 0.7f
                Stamp -> 0.75f
                else -> 0f
            }
            var t = arrived
            while (t <= s.entranceSeconds) {
                inside(s.entrance(t), range.first, range.second, "$label t=$t")
                t += 0.05f
            }
            s.gestures.forEach { g ->
                var u = 0f
                while (u <= s.gestureSeconds(g)) {
                    inside(s.idle(g, u), range.first, range.second, "$label $g u=$u")
                    u += 0.05f
                }
            }
        }
    }

    @Test
    fun `scene A walks in from off screen on the right`() {
        assertTrue(Hero.entrance(0f).x - 23f > Hero.RIGHT)
        assertTrue(Stamp.entrance(0f).x - 23f > Stamp.RIGHT)
    }
}
