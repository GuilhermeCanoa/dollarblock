package com.dollarblock.feature.blocking.scene

import com.dollarblock.core.designsystem.mascot.ArmPose
import com.dollarblock.core.designsystem.mascot.MascotChoreography
import com.dollarblock.core.designsystem.mascot.MascotPose
import com.dollarblock.core.designsystem.mascot.MascotProp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/*
 * Coreografias das três cenas da tela de bloqueio (E25), em funções puras do tempo (s, a
 * partir do primeiro frame): dá para testar que a entrada termina exatamente na pose final,
 * que os gestos do repouso fecham sem emenda e que a figura fica dentro da tela. Tempos e
 * poses: docs/specs/E25-tela-bloqueio-dinamica.md, "Coreografia das cenas". As poses do
 * escudo são as mesmas do E23 (MascotChoreography, intocado); aqui só mudam os atos.
 */

/** Gestos curtos do repouso (1–2 s), sorteados a cada 6–12 s. Cada cena usa alguns. */
enum class IdleGesture { TAP_FOOT, GLINT, LOOK_BUTTON, LOOK_DOWN, QUIRK, TUG, SPIN }

/** O que toda cena expõe ao relógio que a anima (BlockSceneClock). */
interface SceneChoreography {
    /** Duração da entrada; daí em diante, [still] (e os gestos do repouso). */
    val entranceSeconds: Float

    /** Instantes da entrada com haptic (impacto do carimbo, braço que para). */
    val hapticAt: List<Float>

    /** Gestos que esta cena sorteia no repouso. */
    val gestures: List<IdleGesture>

    fun entrance(t: Float): MascotPose

    /** Pose final — também a única com "Remover animações" ligado. */
    fun still(): MascotPose

    fun idle(gesture: IdleGesture, t: Float): MascotPose

    fun gestureSeconds(gesture: IdleGesture): Float

    /** Opacidade dos botões na entrada: sempre 1 em ≤ 1 s (R4). */
    fun controlsAlpha(t: Float): Float
}

object BlockSceneChoreography {

    /** Quanto o fade dos botões leva. */
    const val CONTROLS_FADE = 0.25f

    /** Pausa entre dois gestos do repouso (s): feita com `delay`, sem frames. */
    fun gapSeconds(random: Random): Float = 6f + random.nextFloat() * 6f

    fun forScene(scene: BlockScene): SceneChoreography = when (scene) {
        BlockScene.A_MASCOT -> Hero
        BlockScene.B_BEHIND -> Held
        BlockScene.C_STAMP -> Stamp
    }

    // ── A — o mascote é a tela ─────────────────────────────────────────────────────────────

    /**
     * Palco da cena A, em unidades da arte: os pés no centro (x = 0) e no chão (y = 0). A
     * caixa vai de [LEFT] a [RIGHT] e de [TOP] a [BOTTOM] (a mesma do mock).
     */
    object Hero : SceneChoreography {
        const val LEFT = -60f
        const val RIGHT = 60f
        const val TOP = -112f
        const val BOTTOM = 6f

        private const val START_X = 150f
        private const val WALK_END = 0.7f
        override val entranceSeconds = 1.0f
        override val hapticAt = listOf(1.0f)
        override val gestures = listOf(IdleGesture.TAP_FOOT, IdleGesture.GLINT, IdleGesture.LOOK_BUTTON)

        /** Braço estendido entregando o recibinho ao usuário. */
        val Handover = ArmPose(96f, -40f)

        private val Final = MascotPose(
            x = 0f, y = 0f, facing = 1f, tilt = -4f, brow = 0.15f, quirk = 0.7f, look = 0.4f,
            frontArm = Handover, backArm = ArmPose.OnHip, prop = MascotProp.RECEIPT,
        )

        override fun still() = Final

        override fun entrance(t: Float): MascotPose {
            if (t >= entranceSeconds) return Final
            if (t < WALK_END) {
                // Entra andando pela direita, de frente para onde vai, recibinho na mão.
                val x = { s: Float -> START_X * (1f - walkEase(s / WALK_END)) }
                return walking(x, t, START_X).copy(facing = -1f, prop = MascotProp.RECEIPT)
            }
            // Para, vira para o usuário e estende o recibinho; ergue a sobrancelha da frente.
            val f = smooth((t - WALK_END) / (entranceSeconds - WALK_END))
            return Final.copy(
                tilt = Final.tilt * f,
                brow = Final.brow * f,
                quirk = Final.quirk * f,
                look = Final.look * f,
                frontArm = ArmPose.Relaxed.lerp(Handover, f),
                backArm = ArmPose.Relaxed.lerp(ArmPose.OnHip, f),
            )
        }

        override fun controlsAlpha(t: Float) = fade(t, 0.6f, CONTROLS_FADE)

        /** Título, subtítulo, valor e chip aparecem um depois do outro (80 ms). */
        fun textAlpha(t: Float, index: Int) = fade(t, index * 0.08f, 0.3f)

        override fun gestureSeconds(gesture: IdleGesture) = when (gesture) {
            IdleGesture.TAP_FOOT -> 1.4f
            IdleGesture.GLINT -> 1.2f
            else -> 2.0f
        }

        override fun idle(gesture: IdleGesture, t: Float): MascotPose {
            val d = gestureSeconds(gesture)
            val base = Final.copy(y = breath(t, d))
            return when (gesture) {
                IdleGesture.TAP_FOOT -> tapFoot(base, t)
                IdleGesture.GLINT -> base.copy(glint = glintSweep(t, 0.3f))
                else -> {
                    // Olha para o botão, embaixo, e volta.
                    val k = MascotChoreography.keys(t, 0.2f to 0f, 0.6f to 1f, 1.4f to 1f, 1.8f to 0f)
                    base.copy(look = Final.look * (1f - k), lookDown = 0.9f * k)
                }
            }
        }
    }

    // ── B — atrás do recibo ────────────────────────────────────────────────────────────────

    /**
     * Palco da cena B: caixa de [LEFT] a [RIGHT] (a largura do recibo) e de [TOP] a
     * [BOTTOM]; o recibo começa em [RECEIPT_TOP], na altura das mãos.
     */
    object Held : SceneChoreography {
        const val LEFT = -42.76f
        const val RIGHT = 42.76f
        const val TOP = -96f
        const val BOTTOM = 4f
        const val RECEIPT_TOP = -44f

        /** Mãos na borda de cima do papel. */
        val Grip = ArmPose(24f, 6f)

        private const val RISE_END = 0.6f
        const val STAMP_START = 0.9f
        val impact = STAMP_START + STAMP_ALPHA + springImpactSeconds()
        override val entranceSeconds = STAMP_START + STAMP_ALPHA + SPRING_SETTLE
        override val hapticAt = listOf(impact)
        override val gestures = listOf(IdleGesture.LOOK_DOWN, IdleGesture.QUIRK, IdleGesture.TUG)

        private val Final = MascotPose(x = 0f, y = 0f, brow = -0.4f, frontArm = Grip, backArm = Grip)

        override fun still() = Final

        override fun entrance(t: Float): MascotPose {
            if (t >= entranceSeconds) return Final
            return Final.copy(
                brow = MascotChoreography.keys(t, RISE_END to 0f, 0.9f to 0.5f, impact to 0.5f, impact + 0.15f to -0.4f),
                tilt = MascotChoreography.keys(t, RISE_END to 0f, 0.75f to -6f, 0.9f to 0f),
            )
        }

        /** Recibo + mascote sobem de baixo da tela: fração da altura ainda abaixo (1 → 0). */
        fun rise(t: Float) = 1f - fastOutSlowIn((t / RISE_END).coerceIn(0f, 1f))

        /** Oscilação do papel subindo (−2° → 1° → 0, como mola). */
        fun paperRotation(t: Float) =
            if (t >= entranceSeconds) 0f else MascotChoreography.keys(t, 0f to -2f, 0.38f to 1f, 0.75f to 0f)

        /** Tremor do recibo no impacto do carimbo, em dp (2 dp, ~150 ms). */
        fun tremorDp(t: Float) = shake(t, impact, 0.15f, 2f)

        fun stampScale(t: Float) = stampDrop(t, STAMP_START, 2.4f)
        fun stampAlpha(t: Float) = stampFade(t, STAMP_START)

        override fun controlsAlpha(t: Float) = fade(t, 0.5f, CONTROLS_FADE)

        override fun gestureSeconds(gesture: IdleGesture) = when (gesture) {
            IdleGesture.TUG -> 1.4f
            else -> 1.6f
        }

        override fun idle(gesture: IdleGesture, t: Float): MascotPose {
            val d = gestureSeconds(gesture)
            // Respira inclinando de leve a cabeça (as mãos não saem do papel).
            val base = Final.copy(tilt = 1.2f * sin(2f * PI.toFloat() * t / d))
            return when (gesture) {
                IdleGesture.LOOK_DOWN ->
                    base.copy(lookDown = MascotChoreography.keys(t, 0.2f to 0f, 0.5f to 1f, 1.1f to 1f, 1.4f to 0f))
                IdleGesture.QUIRK ->
                    base.copy(quirk = MascotChoreography.keys(t, 0.2f to 0f, 0.5f to 1f, 1.1f to 1f, 1.4f to 0f))
                else -> base
            }
        }

        /** Puxão no recibo (gesto [IdleGesture.TUG]), em dp para cima. */
        fun tugDp(gesture: IdleGesture?, t: Float) =
            if (gesture == IdleGesture.TUG) 2f * MascotChoreography.keys(t, 0.2f to 0f, 0.45f to 1f, 0.9f to 1f, 1.2f to 0f) else 0f
    }

    // ── C — o carimbo ──────────────────────────────────────────────────────────────────────

    /** Palco do mascote da cena C (só ele; o recibo fica ao lado): pés em (0, 0). */
    object Stamp : SceneChoreography {
        const val LEFT = -50f
        const val RIGHT = 30f
        const val TOP = -125f
        const val BOTTOM = 5f

        /** Carimbo erguido, satisfeito (a pose final). */
        val Raised = ArmPose(122f, 24f)

        /** Carimbo lá em cima, pronto para bater. */
        val WindUp = ArmPose(150f, 18f)

        /** O braço que desce batendo. */
        val Slam = ArmPose(64f, 8f)

        private const val START_X = 140f
        private const val WALK_START = 0.3f
        private const val WALK_END = 0.75f
        const val SLAM_START = 0.9f
        const val IMPACT = 0.98f
        private const val BLOW_START = 1.1f
        override val entranceSeconds = 1.8f
        override val hapticAt = listOf(IMPACT)
        override val gestures = listOf(IdleGesture.SPIN, IdleGesture.TAP_FOOT)

        private val Final = MascotPose(
            x = 0f, y = 0f, facing = -1f, tilt = 6f, brow = 0.5f, quirk = 0.3f, look = 0.4f,
            glint = 0.35f, frontArm = Raised, backArm = ArmPose.OnHip, prop = MascotProp.STAMP,
        )

        override fun still() = Final

        override fun entrance(t: Float): MascotPose {
            if (t >= entranceSeconds) return Final
            val x = { s: Float ->
                START_X * (1f - walkEase(((s - WALK_START) / (WALK_END - WALK_START)).coerceIn(0f, 1f)))
            }
            val walk = walking(x, t, START_X)
            val arm = when {
                t < 0.6f -> ArmPose.Relaxed
                t < SLAM_START -> ArmPose.Relaxed.lerp(WindUp, smooth((t - 0.6f) / (SLAM_START - 0.6f)))
                // Bate: desce rápido, sem suavizar a chegada (é o impacto).
                t < IMPACT -> WindUp.lerp(Slam, ((t - SLAM_START) / (IMPACT - SLAM_START)).let { it * it })
                t < BLOW_START -> Slam
                else -> Slam.lerp(Raised, smooth((t - BLOW_START) / 0.4f))
            }
            val puff = ((t - 1.2f) / 0.5f).let { if (it <= 0f || it >= 1f) 0f else it }
            return Final.copy(
                x = walk.x,
                walk = walk.walk,
                stride = walk.stride,
                // Assopra o carimbo: o escudo inclina na direção dele e volta.
                tilt = kf(t, 0f to 0f, 1.15f to 0f, 1.3f to -8f, 1.55f to -8f, 1.8f to 6f),
                brow = kf(t, 0.3f to 0f, IMPACT to -0.3f, 1.5f to -0.3f, 1.75f to 0.5f),
                quirk = kf(t, 1.5f to 0f, 1.8f to 0.3f),
                look = kf(t, 0.75f to 0f, 1.0f to 0.6f, 1.5f to 0.6f, 1.8f to 0.4f),
                frontArm = arm,
                propOpen = puff,
                glint = if (t < 1.5f) -1f else 0.35f * smooth((t - 1.5f) / 0.3f),
            )
        }

        /** Recibo "impresso" de cima para baixo: fração visível (0 → 1, linear). */
        fun reveal(t: Float) = (t / 0.7f).coerceIn(0f, 1f)

        fun titleAlpha(t: Float) = fade(t, 0.05f, 0.5f)

        /** Tremor de tela na batida: 3 dp por 120 ms. */
        fun shakeDp(t: Float) = shake(t, IMPACT, 0.12f, 3f)

        /** O "BLOQUEADO" grande aparece na batida e assenta com mola (1,6 → 1). */
        fun stampScale(t: Float) = if (t < IMPACT) STAMP_C_FROM else spring(STAMP_C_FROM, 1f, t - IMPACT)
        fun stampAlpha(t: Float) = if (t < IMPACT) 0f else 1f

        override fun controlsAlpha(t: Float) = fade(t, 0.7f, CONTROLS_FADE)

        override fun gestureSeconds(gesture: IdleGesture) = 1.4f

        override fun idle(gesture: IdleGesture, t: Float): MascotPose {
            val base = Final.copy(y = breath(t, gestureSeconds(gesture)))
            return when (gesture) {
                // Gira o carimbo na mão, uma volta inteira.
                IdleGesture.SPIN -> base.copy(propPhase = 360f * smooth((t - 0.2f) / 1.0f).let { if (t >= 1.2f) 0f else it })
                else -> tapFoot(base, t)
            }
        }

        private const val STAMP_C_FROM = 1.6f
    }

    // ── Peças comuns ───────────────────────────────────────────────────────────────────────

    /** Carimbo "como hoje": aparece em 120 ms e cai com mola (escala [from] → 1). */
    const val STAMP_ALPHA = 0.12f

    /** Depois disto a mola é dada como assentada (resto < 0,2%). */
    const val SPRING_SETTLE = 0.4f

    private const val DAMPING = 0.45f

    /** Spring.StiffnessMedium do Compose (o mesmo da tela de hoje). */
    private const val STIFFNESS = 1500f

    fun stampDrop(t: Float, start: Float, from: Float): Float =
        if (t < start + STAMP_ALPHA) from else spring(from, 1f, t - start - STAMP_ALPHA)

    fun stampFade(t: Float, start: Float): Float = fade(t, start, STAMP_ALPHA)

    /** Mola sub-amortecida de [from] a [to], [dt] s depois de solta; exata em [to] ao assentar. */
    fun spring(from: Float, to: Float, dt: Float): Float {
        if (dt <= 0f) return from
        if (dt >= SPRING_SETTLE - 1e-4f) return to
        val w = sqrt(STIFFNESS)
        val wd = w * sqrt(1f - DAMPING * DAMPING)
        val decay = exp(-DAMPING * w * dt)
        return to + (from - to) * decay * (cos(wd * dt) + DAMPING * w / wd * sin(wd * dt))
    }

    /** Quando a mola cruza o alvo pela 1ª vez: o impacto do carimbo no papel. */
    fun springImpactSeconds(): Float {
        val w = sqrt(STIFFNESS)
        val wd = w * sqrt(1f - DAMPING * DAMPING)
        val k = DAMPING * w / wd
        return (PI.toFloat() - atan(1f / k)) / wd
    }

    private fun shake(t: Float, at: Float, length: Float, amplitude: Float): Float {
        val u = t - at
        if (u <= 0f || u >= length) return 0f
        return amplitude * sin(u * 2f * PI.toFloat() * 30f) * (1f - u / length)
    }

    /** Respiração: o corpo sobe e desce 0,6 u, um ciclo por gesto (fecha no zero). */
    private fun breath(t: Float, duration: Float) = -0.6f * sin(2f * PI.toFloat() * t / duration)

    /** Bate o pé, impaciente: duas batidas do pé da frente. */
    private fun tapFoot(base: MascotPose, t: Float): MascotPose {
        val tap = MascotChoreography.keys(t, 0.2f to 0f, 0.35f to 1f, 0.5f to 0f) +
            MascotChoreography.keys(t, 0.65f to 0f, 0.8f to 1f, 0.95f to 0f)
        return base.copy(walk = 0.45f * tap, stride = 0f, brow = base.brow - 0.15f * MascotChoreography.keys(t, 0.1f to 0f, 0.3f to 1f, 1.0f to 1f, 1.3f to 0f))
    }

    /** Brilho atravessando a lente a partir de [start]; fora da varredura, sem brilho. */
    private fun glintSweep(t: Float, start: Float) = ((t - start) / 0.6f).let { if (it < 0f || it > 1f) -1f else it }

    private const val STEP = 15f

    /** Passada sincronizada com o deslocamento (como o `walking` do E23). */
    private fun walking(x: (Float) -> Float, t: Float, origin: Float): MascotPose {
        val now = x(t)
        val dt = 0.02f
        val t0 = (t - dt).coerceAtLeast(0f)
        val speed = abs(x(t + dt) - x(t0)) / (t + dt - t0)
        return MascotPose(
            x = now,
            y = 0f,
            walk = (speed / 160f).coerceIn(0f, 1f),
            stride = abs(now - origin) / STEP * PI.toFloat(),
        )
    }

    private fun kf(t: Float, vararg frames: Pair<Float, Float>) = MascotChoreography.keys(t, *frames)

    internal fun fade(t: Float, start: Float, length: Float) = ((t - start) / length).coerceIn(0f, 1f)

    private fun smooth(x: Float): Float {
        val c = x.coerceIn(0f, 1f)
        return c * c * (3f - 2f * c)
    }

    /** Arranque e parada curtos, velocidade constante no meio. */
    private fun walkEase(f: Float): Float {
        val c = f.coerceIn(0f, 1f)
        val r = 0.25f
        return when {
            c < r -> c * c / (2f * r * (1f - r))
            c > 1f - r -> 1f - (1f - c) * (1f - c) / (2f * r * (1f - r))
            else -> (c - r / 2f) / (1f - r)
        }
    }

    /** FastOutSlowIn do Material (cubic-bezier 0.4, 0, 0.2, 1), resolvido por bisseção. */
    internal fun fastOutSlowIn(x: Float): Float {
        if (x <= 0f) return 0f
        if (x >= 1f) return 1f
        fun bez(p1: Float, p2: Float, s: Float): Float {
            val inv = 1f - s
            return 3f * inv * inv * s * p1 + 3f * inv * s * s * p2 + s * s * s
        }
        var lo = 0f
        var hi = 1f
        repeat(30) {
            val mid = (lo + hi) / 2f
            if (bez(0.4f, 0.2f, mid) < x) lo = mid else hi = mid
        }
        return bez(0f, 1f, (lo + hi) / 2f)
    }
}
