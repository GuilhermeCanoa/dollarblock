package com.dollarblock.core.designsystem.mascot

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

/**
 * Pose de um braço, em graus: [shoulder] a partir do braço pendurado (0 = reto para baixo,
 * positivo = abrindo para fora e subindo) e [elbow] dobrando o antebraço a partir daí.
 */
data class ArmPose(val shoulder: Float, val elbow: Float) {
    fun lerp(to: ArmPose, f: Float) = ArmPose(
        shoulder + (to.shoulder - shoulder) * f,
        elbow + (to.elbow - elbow) * f,
    )

    companion object {
        val Relaxed = ArmPose(8f, -6f)
        val OnHip = ArmPose(42f, -112f)
        val ScratchHead = ArmPose(150f, 62f)
        /** Braço erguido em "V", comemorando (os dois juntos abrem para os lados). */
        val Cheer = ArmPose(148f, 14f)
        val HoldForward = ArmPose(72f, 18f)
    }
}

enum class MascotProp { NONE, MAGNIFIER }

/**
 * Tudo o que muda no escudo de um quadro para o outro, em unidades da arte (o escudo tem
 * 46 de largura e ~90 de altura, dos pés ao topo). [x]/[y] são os pés; "frente" é o lado
 * para onde ele olha ([facing] = 1 → direita).
 *
 * - [lean]: inclina o corpo todo a partir dos pés (positivo = para a frente).
 * - [tilt]: inclina só o escudo (a "cabeça"), em torno do centro dele.
 * - [brow]: -1 franzido … 0 neutro … 1 erguido; [quirk] ergue só a sobrancelha da frente.
 * - [look]: -1..1 desloca óculos, sobrancelhas e cifrão (o rosto vira para o lado).
 * - [walk] (0..1) é a amplitude da passada e [stride] a fase dela, em radianos.
 * - [question] (0..1) é a opacidade do "?" sobre a cabeça; [glint] (0..1) a varredura do
 *   brilho na lente, fora desse intervalo sem brilho.
 */
data class MascotPose(
    val x: Float,
    val y: Float,
    val facing: Float = 1f,
    val lean: Float = 0f,
    val tilt: Float = 0f,
    val brow: Float = 0f,
    val quirk: Float = 0f,
    val look: Float = 0f,
    val frontArm: ArmPose = ArmPose.Relaxed,
    val backArm: ArmPose = ArmPose.Relaxed,
    val walk: Float = 0f,
    val stride: Float = 0f,
    val prop: MascotProp = MascotProp.NONE,
    val question: Float = 0f,
    val glint: Float = -1f,
)

/** Os atos da Home. [side] é a borda de onde ele vem: -1 esquerda, 1 direita. */
enum class HomeActKind { PEEK, CROSS, INSPECT }

data class HomeAct(val kind: HomeActKind, val side: Int)

/**
 * Coreografia do escudo, em funções puras do tempo: dá para testar que todo ato começa e
 * termina fora da vista e que os loops do diálogo fecham sem emenda.
 */
object MascotChoreography {

    /** Meia largura do escudo com braços e lupa: a partir daí, fora da tela de verdade. */
    const val FIGURE_HALF_WIDTH = 72f

    /** Velocidade de caminhada (unidades/s): lenta, deadpan. */
    private const val WALK_SPEED = 58f

    /** Comprimento de um passo (unidades de deslocamento por meia volta da passada). */
    private const val STEP = 15f

    // ── Home ──────────────────────────────────────────────────────────────────────────────

    /** Próximo ato: sorteado, nunca igual ao anterior (tipo e lado). */
    fun nextHomeAct(previous: HomeAct?, random: Random): HomeAct {
        val kinds = HomeActKind.entries.filter { it != previous?.kind }
        return HomeAct(kinds[random.nextInt(kinds.size)], if (random.nextBoolean()) 1 else -1)
    }

    /** Pausa entre dois atos (s): "às vezes", não o tempo todo. */
    fun homeGapSeconds(random: Random): Float = 12f + random.nextFloat() * 13f

    fun homeDuration(act: HomeAct, sceneWidth: Float): Float = when (act.kind) {
        HomeActKind.PEEK -> 6.2f
        HomeActKind.CROSS -> crossTimeline(act, sceneWidth).end
        HomeActKind.INSPECT -> 8.4f
    }

    /**
     * Pose do ato [act] no instante [t] (s), numa cena de [sceneWidth] unidades com o chão
     * em [ground] (os pés pisam ali — na Home, a linha de base do número).
     */
    fun homePose(act: HomeAct, t: Float, sceneWidth: Float, ground: Float): MascotPose = when (act.kind) {
        HomeActKind.PEEK -> peek(act.side, t, sceneWidth, ground)
        HomeActKind.CROSS -> cross(act, t, sceneWidth, ground)
        HomeActKind.INSPECT -> inspect(act.side, t, sceneWidth, ground)
    }

    /** Debruça da borda [side], olha o número, ergue a sobrancelha, inclina, volta. */
    private fun peek(side: Int, t: Float, w: Float, ground: Float): MascotPose {
        val edge = if (side < 0) 0f else w
        val hidden = edge + side * (FIGURE_HALF_WIDTH + 30f)
        val peeking = edge + side * 22f
        val inOut = keys(t, 0f to 0f, 1.0f to 1f, 5.0f to 1f, 6.2f to 0f)
        return MascotPose(
            x = hidden + (peeking - hidden) * inOut,
            y = ground,
            facing = -side.toFloat(),
            lean = 30f * keys(t, 0.2f to 0f, 1.1f to 1f, 4.9f to 1f, 5.9f to 0f),
            tilt = 9f * keys(t, 3.2f to 0f, 3.7f to 1f, 4.4f to 1f, 4.8f to 0f),
            brow = 0.2f,
            quirk = keys(t, 1.5f to 0f, 1.9f to 1f, 4.4f to 1f, 4.9f to 0f),
            look = keys(t, 0.9f to 0f, 1.4f to 1f, 4.6f to 1f, 5.0f to 0f),
            glint = (t - 2.5f) / 0.5f,
        )
    }

    private class CrossTimeline(val start: Float, val stop: Float, val finish: Float, val arrive: Float, val leave: Float, val end: Float)

    /** Entra por [HomeAct.side], para perto da borda (ao lado do número, não atrás) para conferi-lo, sai pelo outro lado. */
    private fun crossTimeline(act: HomeAct, w: Float): CrossTimeline {
        val dir = -act.side
        val start = if (dir > 0) -FIGURE_HALF_WIDTH - 10f else w + FIGURE_HALF_WIDTH + 10f
        val finish = if (dir > 0) w + FIGURE_HALF_WIDTH + 10f else -FIGURE_HALF_WIDTH - 10f
        val stop = if (dir > 0) w * 0.13f else w * 0.87f
        val arrive = abs(stop - start) / WALK_SPEED
        val leave = arrive + 3.4f
        return CrossTimeline(start, stop, finish, arrive, leave, leave + abs(finish - stop) / WALK_SPEED)
    }

    private fun cross(act: HomeAct, t: Float, w: Float, ground: Float): MascotPose {
        val tl = crossTimeline(act, w)
        val x = { s: Float ->
            when {
                s < tl.arrive -> tl.start + (tl.stop - tl.start) * walkEase(s / tl.arrive)
                s < tl.leave -> tl.stop
                else -> tl.stop + (tl.finish - tl.stop) * walkEase((s - tl.leave) / (tl.end - tl.leave))
            }
        }
        val a = tl.arrive
        val raise = keys(t, a + 0.3f to 0f, a + 0.8f to 1f, a + 2.6f to 1f, a + 3.1f to 0f)
        return walking(x, t, tl.start).copy(
            y = ground,
            facing = if (tl.finish > tl.start) 1f else -1f,
            lean = 10f * keys(t, a + 0.8f to 0f, a + 1.3f to 1f, a + 2.4f to 1f, a + 2.9f to 0f),
            quirk = keys(t, a + 1.1f to 0f, a + 1.5f to 1f, a + 2.5f to 1f, a + 3.0f to 0f),
            look = 0.6f * keys(t, a to 0f, a + 0.4f to 1f, a + 2.8f to 1f, a + 3.2f to 0f),
            frontArm = ArmPose.Relaxed.lerp(ArmPose.HoldForward, raise),
            prop = MascotProp.MAGNIFIER,
            glint = (t - a - 1.6f) / 0.5f,
        )
    }

    /** Entra um pouco, examina o número com a lupa, coça a cabeça em dúvida, dá meia-volta. */
    private fun inspect(side: Int, t: Float, w: Float, ground: Float): MascotPose {
        val edge = if (side < 0) 0f else w
        val hidden = edge + side * (FIGURE_HALF_WIDTH + 10f)
        val spot = edge - side * 70f
        val x = { s: Float ->
            when {
                s < 1.8f -> hidden + (spot - hidden) * walkEase(s / 1.8f)
                s < 6.6f -> spot
                else -> spot + (hidden - spot) * walkEase(((s - 6.6f) / 1.8f).coerceAtMost(1f))
            }
        }
        val turnedBack = t >= 6.5f
        val raise = keys(t, 1.9f to 0f, 2.4f to 1f, 3.9f to 1f, 4.4f to 0f)
        val scratch = keys(t, 4.3f to 0f, 4.8f to 1f, 5.9f to 1f, 6.4f to 0f)
        val scratching = ArmPose.ScratchHead.copy(elbow = ArmPose.ScratchHead.elbow + 9f * sin(t * 13f) * scratch)
        return walking(x, t, hidden).copy(
            y = ground,
            facing = if (turnedBack) side.toFloat() else -side.toFloat(),
            lean = 12f * keys(t, 2.4f to 0f, 2.9f to 1f, 3.8f to 1f, 4.3f to 0f),
            tilt = 10f * scratch,
            quirk = keys(t, 2.8f to 0f, 3.2f to 1f, 6.0f to 1f, 6.4f to 0f),
            look = if (turnedBack) 0f else keys(t, 1.8f to 0f, 2.2f to 1f, 6.0f to 1f, 6.4f to 0f),
            frontArm = ArmPose.Relaxed.lerp(ArmPose.HoldForward, raise),
            backArm = ArmPose.Relaxed.lerp(scratching, scratch),
            prop = MascotProp.MAGNIFIER,
            question = keys(t, 4.6f to 0f, 5.0f to 1f, 6.0f to 1f, 6.4f to 0f),
            glint = (t - 3.0f) / 0.5f,
        )
    }

    /** Passada sincronizada com o deslocamento: o pé não escorrega. */
    private fun walking(x: (Float) -> Float, t: Float, origin: Float): MascotPose {
        val now = x(t)
        val dt = 0.05f
        val speed = abs(x(t + dt) - x((t - dt).coerceAtLeast(0f))) / (t + dt - (t - dt).coerceAtLeast(0f))
        return MascotPose(
            x = now,
            y = 0f,
            walk = (speed / WALK_SPEED * 1.3f).coerceIn(0f, 1f),
            stride = abs(now - origin) / STEP * PI.toFloat(),
        )
    }

    // ── Aviso de troca de limite ──────────────────────────────────────────────────────────

    const val RAISED_PERIOD = 6.4f
    const val CUT_PERIOD = 5.2f

    /** Pose parada (animações desligadas) — se sustenta sozinha. */
    fun reactionStill(increased: Boolean, x: Float, ground: Float): MascotPose = if (increased) {
        MascotPose(
            x = x, y = ground, tilt = 10f, brow = 0.2f, quirk = 1f, look = 0.5f,
            frontArm = ArmPose.ScratchHead, backArm = ArmPose.OnHip, question = 1f,
        )
    } else {
        MascotPose(x = x, y = ground, brow = 0.6f, frontArm = ArmPose.Cheer, backArm = ArmPose.Cheer)
    }

    /**
     * Reação em loop. Limite **aumentado**: coça a cabeça com um "?" no ar, sobrancelha lá em
     * cima; depois as duas mãos na cintura e balança a cabeça devagar — inconformado.
     * Limite **reduzido**: comemora com os dois braços para cima, socando o ar, o corpo
     * balançando de um lado para o outro, e um brilho nos óculos.
     */
    fun reaction(increased: Boolean, time: Float, x: Float, ground: Float): MascotPose {
        val breath = sin(time * 2.2f) * 0.6f
        return if (increased) raised(time % RAISED_PERIOD, x, ground + breath) else cut(time % CUT_PERIOD, x, ground + breath)
    }

    private fun raised(t: Float, x: Float, ground: Float): MascotPose {
        val toScratch = keys(t, 0.3f to 0f, 1.0f to 1f, 2.8f to 1f, 3.4f to 0f)
        val scratching = ArmPose.ScratchHead.copy(elbow = ArmPose.ScratchHead.elbow + 9f * sin(t * 13f) * keys(t, 1.0f to 0f, 1.2f to 1f, 2.6f to 1f, 2.8f to 0f))
        val shake = keys(t, 3.4f to 0f, 3.7f to 1f, 5.0f to 1f, 5.4f to 0f)
        return MascotPose(
            x = x,
            y = ground,
            tilt = 11f * toScratch + 7f * sin((t - 3.4f) * 2f * PI.toFloat() * 1.4f) * shake,
            brow = -0.4f + 0.6f * toScratch,
            quirk = 0.3f + 0.7f * toScratch,
            look = 0.5f * toScratch,
            frontArm = ArmPose.OnHip.lerp(scratching, toScratch),
            backArm = ArmPose.OnHip,
            question = keys(t, 0.7f to 0f, 1.1f to 1f, 2.9f to 1f, 3.3f to 0f),
        )
    }

    private fun cut(t: Float, x: Float, ground: Float): MascotPose {
        val up = keys(t, 0.3f to 0f, 0.8f to 1f, 3.5f to 1f, 4.2f to 0f)
        val pumping = keys(t, 0.8f to 0f, 1.0f to 1f, 3.1f to 1f, 3.4f to 0f)
        val phase = (t - 0.8f) * 2f * PI.toFloat() * 1.3f
        // Socos no ar: o braço sobe esticando e volta dobrando um pouco; os dois em fase.
        val pump = sin(phase) * pumping
        val cheer = ArmPose(ArmPose.Cheer.shoulder + 10f * pump, ArmPose.Cheer.elbow + 16f * (1f - pump) * pumping)
        val arm = ArmPose.Relaxed.lerp(cheer, up)
        return MascotPose(
            x = x,
            y = ground - 2.5f * abs(sin(phase)) * pumping,
            tilt = 6f * sin(phase / 2f) * pumping,
            brow = 0.6f * up,
            frontArm = arm,
            backArm = arm,
            glint = (t - 2.7f) / 0.5f,
        )
    }

    // ── Interpolação ──────────────────────────────────────────────────────────────────────

    /** Valor no instante [t] entre chaves (tempo, valor), com entrada e saída suaves. */
    internal fun keys(t: Float, vararg frames: Pair<Float, Float>): Float {
        if (t <= frames.first().first) return frames.first().second
        for (i in 1 until frames.size) {
            val (t1, v1) = frames[i]
            if (t <= t1) {
                val (t0, v0) = frames[i - 1]
                return v0 + (v1 - v0) * smooth((t - t0) / (t1 - t0))
            }
        }
        return frames.last().second
    }

    private fun smooth(x: Float): Float {
        val c = x.coerceIn(0f, 1f)
        return c * c * (3f - 2f * c)
    }

    /** Arranque e parada curtos, velocidade constante no meio (andar, não deslizar). */
    private fun walkEase(f: Float): Float {
        val c = f.coerceIn(0f, 1f)
        val r = 0.15f
        return when {
            c < r -> c * c / (2f * r * (1f - r))
            c > 1f - r -> 1f - (1f - c) * (1f - c) / (2f * r * (1f - r))
            else -> (c - r / 2f) / (1f - r)
        }
    }
}
