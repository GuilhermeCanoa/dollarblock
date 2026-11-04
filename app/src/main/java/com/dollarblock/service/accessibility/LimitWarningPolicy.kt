package com.dollarblock.service.accessibility

import kotlin.math.ceil

/**
 * Decide quando disparar o aviso passivo-agressivo de "faltam N min de limite"
 * (função pura, testável isoladamente do serviço de acessibilidade).
 */
object LimitWarningPolicy {

    const val WARNING_THRESHOLD_MS = 5 * 60_000L

    /**
     * true quando o uso está dentro da janela final de 5 min antes do limite e o aviso
     * ainda não saiu hoje para este app/limite.
     *
     * O "já avisou hoje" vem de fora (persistido por dia), e não de comparar com o uso do
     * frame anterior: essa comparação vivia em memória e não virava à meia-noite — limites
     * de até 5 min deixavam de avisar a partir do 2º dia, e um restart do serviço dentro
     * da janela repetia o aviso.
     *
     * Limites menores que a janela: o "5 min restantes" nunca é literal — a janela cobre o
     * limite inteiro, então avisa já no primeiro uso do dia.
     */
    fun shouldWarn(currentUsedMillis: Long, limitMillis: Long, alreadyWarnedToday: Boolean): Boolean {
        if (limitMillis <= 0 || alreadyWarnedToday) return false
        val warnAt = (limitMillis - WARNING_THRESHOLD_MS).coerceAtLeast(1L)
        return currentUsedMillis >= warnAt && currentUsedMillis < limitMillis
    }

    /**
     * Minutos restantes reais no instante do aviso (arredondado para cima, mínimo 1),
     * para a notificação nunca dizer "5 min" quando na verdade faltam menos.
     */
    fun minutesRemaining(currentUsedMillis: Long, limitMillis: Long): Int {
        val remainingMs = (limitMillis - currentUsedMillis).coerceAtLeast(0L)
        return ceil(remainingMs / 60_000.0).toInt().coerceAtLeast(1)
    }
}
