package com.dollarblock.feature.celebration

import kotlin.random.Random

/**
 * Regras do parabéns por recusar a fatura (E27). Puro e testado em CelebrationPolicyTest.
 */
object CelebrationPolicy {

    /** No máximo um parabéns a cada 2 h: recusar de novo antes disso vai direto ao launcher. */
    const val INTERVAL_MS = 2 * 60 * 60 * 1000L

    /**
     * Se a recusa agora leva ao app com o parabéns. Sem registro, sim. Um relógio que voltou
     * (último parabéns "no futuro") também libera, para não travar o parabéns por horas.
     */
    fun isDue(lastShownAt: Long?, now: Long): Boolean {
        if (lastShownAt == null) return true
        val elapsed = now - lastShownAt
        return elapsed < 0 || elapsed >= INTERVAL_MS
    }

    /** Sorteia uma variação de texto entre [count], sem repetir [last] quando houver outra. */
    fun pickVariant(count: Int, last: Int?, random: Random): Int {
        require(count > 0)
        if (count == 1) return 0
        val options = (0 until count).filter { it != last }
        return options[random.nextInt(options.size)]
    }
}
