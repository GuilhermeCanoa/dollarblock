package com.dollarblock.feature.blocking.scene

import kotlin.random.Random

/**
 * As três cenas da tela de bloqueio (E25), sorteadas a cada bloqueio para a tela não ficar
 * repetitiva. Mockups: docs/design/block-screen-variants/.
 */
enum class BlockScene {
    /** A — o mascote é a tela: segura um recibo em miniatura; o recibo completo vai numa sheet. */
    A_MASCOT,

    /** B — atrás do recibo: cabeça e mãos aparecem por cima do papel. */
    B_BEHIND,

    /** C — o carimbo: recibo torto, carimbo grande, o mascote de pé com o carimbo na mão. */
    C_STAMP,
    ;

    companion object {
        /** Valor do extra de debug `extra_scene` (`A`|`B`|`C`, sem diferenciar maiúsculas). */
        fun fromExtra(value: String?): BlockScene? = when (value?.trim()?.uppercase()) {
            "A" -> A_MASCOT
            "B" -> B_BEHIND
            "C" -> C_STAMP
            else -> null
        }
    }
}

/** Sorteia uma cena com chance igual entre as que não são [last] (a da abertura anterior). */
fun pickBlockScene(last: BlockScene?, random: Random): BlockScene {
    val options = BlockScene.entries.filter { it != last }
    return options[random.nextInt(options.size)]
}

/** Uma cena escolhida para [packageName], com o instante (ms) em que a entrada dela começou. */
data class BlockScenePick(val scene: BlockScene, val packageName: String, val startedAtMs: Long)

/**
 * Janela em que uma nova abertura da tela para o mesmo app é a **mesma** abertura: o serviço
 * de acessibilidade relança a BlockActivity ~500 ms depois da primeira (ADR-001, com
 * `FLAG_ACTIVITY_CLEAR_TASK`, que recria a activity). Sem isto a cena trocaria no meio da
 * entrada.
 */
const val BLOCK_SCENE_REASSERT_WINDOW_MS = 2_000L

/**
 * A cena desta abertura. Se é a re-afirmação da anterior (mesmo app, dentro de
 * [BLOCK_SCENE_REASSERT_WINDOW_MS]), mantém a cena e o início da entrada — a nova activity
 * continua de onde a outra estava. Senão, sorteia sem repetir a anterior. [forced] (extra de
 * debug) ganha de tudo e começa a entrada do zero.
 */
fun resolveBlockScene(
    previous: BlockScenePick?,
    packageName: String,
    nowMs: Long,
    random: Random,
    forced: BlockScene? = null,
): BlockScenePick {
    if (forced != null) return BlockScenePick(forced, packageName, nowMs)
    if (previous != null &&
        previous.packageName == packageName &&
        nowMs - previous.startedAtMs in 0 until BLOCK_SCENE_REASSERT_WINDOW_MS
    ) {
        return previous
    }
    return BlockScenePick(pickBlockScene(previous?.scene, random), packageName, nowMs)
}

/**
 * A última cena mostrada, só em memória: a BlockActivity roda no mesmo processo do serviço de
 * acessibilidade, então isto basta para "não repetir a anterior". Ler DataStore atrasaria o
 * primeiro frame, que precisa ganhar a corrida contra o app bloqueado. Se o processo morrer,
 * repetir uma vez não faz mal.
 */
object BlockSceneRotation {
    private var last: BlockScenePick? = null

    @Synchronized
    fun next(packageName: String, nowMs: Long, random: Random, forced: BlockScene? = null): BlockScenePick =
        resolveBlockScene(last, packageName, nowMs, random, forced).also { last = it }
}
