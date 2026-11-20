package com.dollarblock.feature.blocking.stats

import com.dollarblock.feature.statistics.DayTone
import kotlin.random.Random

/*
 * A estatística da tela de bloqueio (E27): uma linha de dado real acima dos botões. Tudo aqui
 * é puro — a escolha, a rotação e a sequência — e testado em BlockStatTest.
 */

/** Os tipos de estatística que a tela de bloqueio sabe mostrar. */
enum class BlockStatKind {
    /** Sequência de dias sem passe pago para este app. */
    STREAK,

    /** Nº de tentativas de abrir este app hoje. */
    ATTEMPTS,

    /** Quanto passou do limite hoje. */
    OVERAGE,

    /** Primeira tentativa do dia. */
    FIRST_TODAY,

    /** Uso de hoje e o limite contratado. */
    USAGE_VS_LIMIT,

    /** Quanto falta para a meia-noite (o app volta de graça). */
    COUNTDOWN,

    /** Valor guardado no mês recusando faturas. */
    MONTH_SAVED,
    ;

    /** Para onde o toque na linha leva dentro do app. */
    val target: BlockStatTarget
        get() = if (this == MONTH_SAVED) BlockStatTarget.HOME else BlockStatTarget.STATEMENT_TODAY
}

enum class BlockStatTarget { HOME, STATEMENT_TODAY }

/** Tudo o que a escolha precisa saber sobre este bloqueio. */
data class BlockStatInput(
    val attemptsToday: Int,
    val usedMinutes: Int,
    val limitMinutes: Int?,
    val minutesUntilMidnight: Int,
    val streakDays: Int,
    /** Guardado no mês (na moeda do usuário); null quando a carteira não carregou a tempo. */
    val monthSaved: Double?,
)

object BlockStatRules {
    const val MIN_ATTEMPTS = 3
    const val MIN_STREAK_DAYS = 2
    const val MIN_OVERAGE_MINUTES = 15

    /** Excedente "grande": ≥15 min e ≥50% do limite. */
    fun overageMinutes(input: BlockStatInput): Int {
        val limit = input.limitMinutes ?: return 0
        return (input.usedMinutes - limit).coerceAtLeast(0)
    }

    private fun bigOverage(input: BlockStatInput): Boolean {
        val limit = input.limitMinutes ?: return false
        val over = overageMinutes(input)
        return over >= MIN_OVERAGE_MINUTES && over * 2 >= limit
    }

    /** Tipos relevantes nesta situação, do mais forte ao mais fraco. */
    fun prioritized(input: BlockStatInput): List<BlockStatKind> = buildList {
        if (input.streakDays >= MIN_STREAK_DAYS) add(BlockStatKind.STREAK)
        if (input.attemptsToday >= MIN_ATTEMPTS) add(BlockStatKind.ATTEMPTS)
        if (bigOverage(input)) add(BlockStatKind.OVERAGE)
        if (input.attemptsToday == 1) add(BlockStatKind.FIRST_TODAY)
    }

    /** Tipos que sempre têm algo a dizer, sorteados quando nenhum prioritário serve. */
    fun fallback(input: BlockStatInput): List<BlockStatKind> = buildList {
        if (input.limitMinutes != null) add(BlockStatKind.USAGE_VS_LIMIT)
        add(BlockStatKind.COUNTDOWN)
        if ((input.monthSaved ?: 0.0) > 0.0) add(BlockStatKind.MONTH_SAVED)
    }
}

/**
 * A estatística desta abertura: o tipo prioritário mais forte que não seja [last]; sem
 * nenhum, um sorteio entre os de [BlockStatRules.fallback] que não sejam [last]. Só repete
 * [last] quando não há outra opção.
 */
fun pickBlockStat(input: BlockStatInput, last: BlockStatKind?, random: Random): BlockStatKind {
    BlockStatRules.prioritized(input).firstOrNull { it != last }?.let { return it }
    val pool = BlockStatRules.fallback(input)
    val fresh = pool.filter { it != last }
    val options = fresh.ifEmpty { pool }
    return options[random.nextInt(options.size)]
}

/**
 * Dias seguidos sem passe pago, terminando **ontem** (hoje ainda está em jogo). [tones] são os
 * desfechos do app dia a dia, do mais antigo ao de ontem. Dentro do limite e "aguentou"
 * contam; qualquer outro desfecho (pagou, cortesia, sem dado) encerra a série.
 */
fun streakEndingYesterday(tones: List<DayTone>): Int {
    var count = 0
    for (tone in tones.asReversed()) {
        if (!tone.isWithin) break
        count++
    }
    return count
}

/** Uma escolha para [packageName], com o instante (ms, relógio monotônico) em que foi feita. */
data class BlockStatPick(val kind: BlockStatKind, val packageName: String, val pickedAtMs: Long)

/**
 * Janela em que uma nova abertura para o mesmo app é a re-afirmação da anterior (o serviço
 * relança a tela ~500 ms depois — ADR-001): mantém o tipo para a linha não trocar na cara.
 */
const val BLOCK_STAT_REASSERT_WINDOW_MS = 2_000L

/**
 * O último tipo mostrado, só em memória (mesmo raciocínio de `BlockSceneRotation`): a tela
 * roda no processo do serviço, e repetir uma vez depois de o processo morrer não faz mal.
 */
object BlockStatRotation {
    private var last: BlockStatPick? = null

    /** A re-afirmação em andamento para [packageName], se houver. */
    @Synchronized
    fun reassertedKind(packageName: String, nowMs: Long): BlockStatKind? {
        val prev = last ?: return null
        return prev.kind.takeIf {
            prev.packageName == packageName && nowMs - prev.pickedAtMs in 0 until BLOCK_STAT_REASSERT_WINDOW_MS
        }
    }

    @Synchronized
    fun next(packageName: String, input: BlockStatInput, nowMs: Long, random: Random): BlockStatKind {
        reassertedKind(packageName, nowMs)?.let { return it }
        return pickBlockStat(input, last?.kind, random).also { last = BlockStatPick(it, packageName, nowMs) }
    }
}
