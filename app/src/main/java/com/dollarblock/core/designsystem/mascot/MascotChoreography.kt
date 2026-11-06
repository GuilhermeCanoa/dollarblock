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
        /** Celular erguido na frente do rosto, do lado da frente (sem cobrir o `$`). */
        val HoldPhone = ArmPose(50f, -80f)
        /** Mão erguida ao lado da cabeça, para o tchau. */
        val Wave = ArmPose(118f, 40f)
        val HoldMug = ArmPose(25f, -70f)
        val SipMug = ArmPose(48f, -128f)
        val Sweep = ArmPose(30f, -20f)
    }
}

enum class MascotProp { NONE, MAGNIFIER, PHONE, MUG, BROOM }

/**
 * Tudo o que muda no escudo de um quadro para o outro, em unidades da arte (o escudo tem
 * 46 de largura e ~90 de altura, dos pés ao topo). [x]/[y] são os pés; "frente" é o lado
 * para onde ele olha ([facing] = 1 → direita).
 *
 * - [lean]: inclina o corpo todo a partir dos pés (positivo = para a frente).
 * - [tilt]: inclina só o escudo (a "cabeça"), em torno do centro dele.
 * - [brow]: -1 franzido … 0 neutro … 1 erguido; [quirk] ergue só a sobrancelha da frente.
 * - [look]: -1..1 desloca óculos, sobrancelhas e cifrão (o rosto vira para o lado);
 *   [lookDown] (0..1) baixa óculos e sobrancelhas (olhando para o que tem nas mãos).
 * - [walk] (0..1) é a amplitude da passada e [stride] a fase dela, em radianos.
 * - [question]/[exclaim] (0..1) são a opacidade do "?" e do "!" sobre a cabeça; [glint]
 *   (0..1) a varredura do brilho na lente, fora desse intervalo sem brilho.
 * - [prop] é o objeto na mão da frente; [propOpen] (0..1) o quanto ele está em uso
 *   (vassoura varrendo) e [propPhase] (s) o relógio dos detalhes que se mexem sozinhos
 *   (vapor, poeira, a tela do celular rolando).
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
    val lookDown: Float = 0f,
    val frontArm: ArmPose = ArmPose.Relaxed,
    val backArm: ArmPose = ArmPose.Relaxed,
    val walk: Float = 0f,
    val stride: Float = 0f,
    val prop: MascotProp = MascotProp.NONE,
    val propOpen: Float = 0f,
    val propPhase: Float = 0f,
    val question: Float = 0f,
    val exclaim: Float = 0f,
    val glint: Float = -1f,
)

/** Os atos da Home. [PEEK] é sempre o primeiro. */
enum class HomeActKind { PEEK, CROSS, INSPECT, WAVE, BUMP, COFFEE, SWEEP }

/** [side] é a borda de onde ele vem: -1 esquerda, 1 direita. */
data class HomeAct(val kind: HomeActKind, val side: Int)

/**
 * O palco da Home, em unidades da arte: largura, o chão (linha de base do número, medida do
 * topo) e onde a tinta do número começa e termina — inclusive o símbolo da moeda. Sem
 * medida, supõe o número no terço do meio.
 */
data class HomeStage(
    val width: Float,
    val ground: Float,
    val numberStart: Float = width * 0.3f,
    val numberEnd: Float = width * 0.7f,
)

/**
 * Coreografia do escudo, em funções puras do tempo: dá para testar que todo ato começa e
 * termina fora da vista e que os loops do diálogo fecham sem emenda.
 */
object MascotChoreography {

    /** Meia largura do escudo com braços e objetos: a partir daí, fora da tela de verdade. */
    const val FIGURE_HALF_WIDTH = 72f

    /** Quantos atos recentes não voltam no sorteio: a Home não fica repetindo a mesma cena. */
    const val RECENT_MEMORY = 3

    /** Distância entre o centro do escudo e a borda dele que bate no número. */
    const val BUMP_REACH = 21f

    /** Velocidade de caminhada (unidades/s): lenta, deadpan. */
    private const val WALK_SPEED = 58f

    /** Comprimento de um passo (unidades de deslocamento por meia volta da passada). */
    private const val STEP = 15f

    // ── Home ──────────────────────────────────────────────────────────────────────────────

    /**
     * Próximo ato: o primeiro é sempre a espiada do canto; depois, sorteado entre os que não
     * apareceram nos últimos [RECENT_MEMORY].
     */
    fun nextHomeAct(recent: List<HomeActKind>, random: Random): HomeAct {
        if (recent.isEmpty()) return HomeAct(HomeActKind.PEEK, if (random.nextBoolean()) 1 else -1)
        val skip = recent.takeLast(RECENT_MEMORY).toSet()
        val kinds = HomeActKind.entries.filter { it !in skip }
        return HomeAct(kinds[random.nextInt(kinds.size)], if (random.nextBoolean()) 1 else -1)
    }

    /** Pausa entre dois atos (s): "às vezes", não o tempo todo. */
    fun homeGapSeconds(random: Random): Float = 12f + random.nextFloat() * 13f

    fun homeDuration(act: HomeAct, stage: HomeStage): Float = when (act.kind) {
        HomeActKind.PEEK -> 6.2f
        HomeActKind.CROSS -> crossTimeline(act, stage.width).end
        HomeActKind.INSPECT -> 8.4f
        HomeActKind.BUMP -> bumpTimeline(act.side, stage).end
        else -> visitFor(act, stage).end
    }

    /** Pose do ato [act] no instante [t] (s). Os pés pisam no chão do [stage]. */
    fun homePose(act: HomeAct, t: Float, stage: HomeStage): MascotPose {
        val w = stage.width
        val ground = stage.ground
        return when (act.kind) {
            HomeActKind.PEEK -> peek(act.side, t, w, ground)
            HomeActKind.CROSS -> cross(act, t, w, ground)
            HomeActKind.INSPECT -> inspect(act.side, t, w, ground)
            HomeActKind.WAVE -> wave(act.side, t, visitFor(act, stage), ground)
            HomeActKind.BUMP -> bump(act.side, t, bumpTimeline(act.side, stage), ground)
            HomeActKind.COFFEE -> coffee(act.side, t, visitFor(act, stage), ground)
            HomeActKind.SWEEP -> sweep(act.side, t, visitFor(act, stage), ground)
        }
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

    // ── Visitas: entra pela borda, para ao lado do número, faz a cena e vai embora ──────────

    /**
     * Entra por [side] até [spot], fica [stay] s e sai — de volta pelo mesmo lado (dando
     * meia-volta) ou, com [across], pelo outro.
     */
    private class Visit(
        val side: Int,
        val hidden: Float,
        val spot: Float,
        val exit: Float,
        val arrive: Float,
        val leave: Float,
        val end: Float,
        val across: Boolean,
    ) {
        fun x(s: Float): Float = when {
            s < arrive -> hidden + (spot - hidden) * walkEase(s / arrive)
            s < leave -> spot
            else -> spot + (exit - spot) * walkEase(((s - leave) / (end - leave)).coerceAtMost(1f))
        }

        /** Olha para dentro da cena; na volta pelo mesmo lado, vira de costas para ela. */
        fun facing(s: Float): Float = if (!across && s >= leave) side.toFloat() else -side.toFloat()
    }

    private fun visit(side: Int, stage: HomeStage, fromEdge: Float, stay: Float, across: Boolean = false): Visit {
        val w = stage.width
        val off = FIGURE_HALF_WIDTH + 10f
        val edge = if (side < 0) 0f else w
        val hidden = edge + side * off
        val spot = edge - side * fromEdge
        val exit = if (across) (w - edge) - side * off else hidden
        val arrive = abs(spot - hidden) / WALK_SPEED
        val leave = arrive + stay
        return Visit(side, hidden, spot, exit, arrive, leave, leave + abs(exit - spot) / WALK_SPEED, across)
    }

    /** Ponto de parada e duração de cada visita — ao lado do número, nunca atrás dele. */
    private fun visitFor(act: HomeAct, stage: HomeStage): Visit {
        val w = stage.width
        return when (act.kind) {
            HomeActKind.WAVE -> visit(act.side, stage, w * 0.2f, WAVE_STAY)
            HomeActKind.COFFEE -> visit(act.side, stage, w * 0.16f, COFFEE_STAY)
            HomeActKind.SWEEP -> visit(act.side, stage, w * 0.13f, SWEEP_STAY, across = true)
            else -> error("${act.kind} não é uma visita")
        }
    }

    private const val WAVE_STAY = 4.2f
    private const val COFFEE_STAY = 6.0f
    private const val SWEEP_STAY = 5.0f

    /** Base comum: andando entre os pontos da visita, olhando para onde vai. */
    private fun visiting(v: Visit, t: Float, ground: Float): MascotPose =
        walking(v::x, t, v.hidden).copy(y = ground, facing = v.facing(t))

    /** Para, vira o rosto para quem está olhando a tela e dá um tchauzinho sem pressa. */
    private fun wave(side: Int, t: Float, v: Visit, ground: Float): MascotPose {
        val u = t - v.arrive
        val raise = keys(u, 0.3f to 0f, 0.8f to 1f, 3.3f to 1f, 3.8f to 0f)
        val waving = keys(u, 0.8f to 0f, 1.0f to 1f, 3.0f to 1f, 3.3f to 0f)
        val swing = sin((u - 0.8f) * 2f * PI.toFloat() * 1.1f) * waving
        val hand = ArmPose(ArmPose.Wave.shoulder, ArmPose.Wave.elbow + 22f * swing)
        return visiting(v, t, ground).copy(
            tilt = 6f * raise,
            brow = 0.35f * raise,
            look = -0.2f * raise,
            frontArm = ArmPose.Relaxed.lerp(hand, raise),
            glint = (u - 1.6f) / 0.5f,
        )
    }

    /** Para, toma o café devagar (dois goles), o vapor subindo da caneca; vai embora. */
    private fun coffee(side: Int, t: Float, v: Visit, ground: Float): MascotPose {
        val u = t - v.arrive
        val sip = keys(u, 1.0f to 0f, 1.5f to 1f, 2.1f to 1f, 2.6f to 0f) +
            keys(u, 3.8f to 0f, 4.3f to 1f, 4.9f to 1f, 5.4f to 0f)
        return visiting(v, t, ground).copy(
            tilt = -5f * sip,
            brow = 0.25f * keys(u, 2.4f to 0f, 2.8f to 1f, 3.6f to 1f, 4.0f to 0f),
            look = 0.4f * keys(u, 0f to 0f, 0.5f to 1f, 5.5f to 1f, 6.0f to 0f),
            frontArm = ArmPose.HoldMug.lerp(ArmPose.SipMug, sip),
            prop = MascotProp.MUG,
            propPhase = t,
            glint = (u - 2.8f) / 0.5f,
        )
    }

    /** Entra varrendo, para varrendo o chão na linha do número e segue para o outro lado. */
    private fun sweep(side: Int, t: Float, v: Visit, ground: Float): MascotPose {
        val u = t - v.arrive
        val sweeping = keys(u, 0.2f to 0f, 0.5f to 1f, 4.5f to 1f, 4.8f to 0f)
        val stroke = sin(u * 2f * PI.toFloat() * 0.9f) * sweeping
        return visiting(v, t, ground).copy(
            lean = 6f * sweeping,
            tilt = 4f * sweeping,
            look = 0.5f,
            lookDown = 0.8f,
            frontArm = ArmPose(ArmPose.Sweep.shoulder + 12f * stroke, ArmPose.Sweep.elbow),
            prop = MascotProp.BROOM,
            propOpen = sweeping,
            propPhase = t,
        )
    }

    // ── Trombada: andando de olho no celular, bate na borda do número ───────────────────────

    private class BumpTimeline(val start: Float, val contact: Float, val back: Float, val arrive: Float, val leave: Float, val end: Float)

    private const val RECOIL = 7f

    private fun bumpTimeline(side: Int, stage: HomeStage): BumpTimeline {
        val w = stage.width
        val dir = -side
        val start = if (dir > 0) -FIGURE_HALF_WIDTH - 10f else w + FIGURE_HALF_WIDTH + 10f
        val contact = (if (dir > 0) stage.numberStart - BUMP_REACH else stage.numberEnd + BUMP_REACH)
            .coerceIn(25f, w - 25f)
        val back = contact - dir * RECOIL
        val arrive = abs(contact - start) / WALK_SPEED + 0.25f
        val leave = arrive + 3.0f
        return BumpTimeline(start, contact, back, arrive, leave, leave + abs(start - back) / WALK_SPEED)
    }

    private fun bump(side: Int, t: Float, tl: BumpTimeline, ground: Float): MascotPose {
        val dir = -side
        val x = { s: Float ->
            when {
                // Anda em velocidade constante até a borda: para de uma vez (é a trombada).
                s < tl.arrive -> tl.start + (tl.contact - tl.start) * easeInOnly(s / tl.arrive)
                s < tl.leave -> tl.contact + (tl.back - tl.contact) * keys(s - tl.arrive, 0f to 0f, 0.3f to 1f)
                else -> tl.back + (tl.start - tl.back) * walkEase(((s - tl.leave) / (tl.end - tl.leave)).coerceAtMost(1f))
            }
        }
        val u = t - tl.arrive
        val hit = if (u < 0f) 0f else keys(u, 0f to 0f, 0.12f to 1f, 0.6f to 1f, 1.1f to 0f)
        val onPhone = if (u < 0f) 1f else keys(u, 0f to 1f, 0.3f to 0f, 2.6f to 0f, 3.0f to 1f)
        val base = walking(x, t, tl.start)
        return base.copy(
            y = ground,
            // Na ré do susto as pernas não andam.
            walk = if (u in 0f..tl.leave - tl.arrive) 0f else base.walk,
            facing = if (t < tl.leave) dir.toFloat() else -dir.toFloat(),
            lean = -9f * hit,
            tilt = -7f * hit,
            brow = -0.7f * keys(u, 0.6f to 0f, 1.0f to 1f, 2.4f to 1f, 2.8f to 0f),
            look = if (t < tl.leave) 0.6f else 0.5f,
            lookDown = onPhone,
            frontArm = ArmPose.HoldPhone.lerp(ArmPose.Relaxed, keys(u, 0.3f to 0f, 0.9f to 1f, 2.4f to 1f, 3.0f to 0f)),
            prop = MascotProp.PHONE,
            propPhase = t,
            exclaim = keys(u, 0.04f to 0f, 0.22f to 1f, 1.6f to 1f, 2.0f to 0f),
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

    /** Só o arranque suave; chega no fim ainda andando (para a trombada parar seco). */
    private fun easeInOnly(f: Float): Float {
        val c = f.coerceIn(0f, 1f)
        val r = 0.15f
        return if (c < r) c * c / (r * (2f - r)) else (2f * c - r) / (2f - r)
    }
}
