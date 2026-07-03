package com.dollarblock.service.accessibility

/**
 * Decide quando disparar o aviso passivo-agressivo de "faltam 5 min de limite"
 * (função pura, testável isoladamente do serviço de acessibilidade).
 */
object LimitWarningPolicy {

    const val WARNING_THRESHOLD_MS = 5 * 60_000L

    /**
     * true quando o uso cruzou para dentro da janela final de 5 min antes do limite
     * neste frame (usedMillis antes < limiar <= usedMillis agora), evitando disparos
     * repetidos a cada poll enquanto o usuário permanece nessa janela.
     */
    fun shouldWarn(previousUsedMillis: Long, currentUsedMillis: Long, limitMillis: Long): Boolean {
        if (limitMillis <= 0) return false
        // Para limites menores que a janela de aviso, o "5 min restantes" nunca é
        // literal — a janela some inteira dentro do limite, então avisa já no
        // primeiro uso (previousUsedMillis == 0), e só nessa vez.
        val warnAt = limitMillis - WARNING_THRESHOLD_MS
        if (warnAt <= 0) return previousUsedMillis <= 0 && currentUsedMillis in 1 until limitMillis
        return previousUsedMillis < warnAt && currentUsedMillis >= warnAt && currentUsedMillis < limitMillis
    }
}
