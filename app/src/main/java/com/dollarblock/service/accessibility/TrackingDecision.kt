package com.dollarblock.service.accessibility

/**
 * Decisão **pura** (sem Android) de o que o loop de tracking deve fazer num frame:
 * avisar que o limite está próximo e/ou bloquear.
 *
 * Extraída de [DollarBlockAccessibilityService] porque a orquestração do serviço não é
 * testável em unit test — e era justamente onde morava o bug do "aviso-fantasma":
 * o app rastreado (ex.: Chrome) continuava disparando o aviso de 5 min mesmo depois de
 * o usuário ter saído dele, porque o aviso não verificava se o app ainda estava em
 * foreground (só o bloqueio verificava).
 *
 * Regra central: **nada dispara se o app rastreado não é mais o app em foreground.**
 */
object TrackingDecision {

    data class Frame(
        /** Pacote que este loop de tracking está acompanhando. */
        val trackedPackage: String,
        /** Pacote atualmente em foreground (o último window-state observado). Null = desconhecido. */
        val foregroundPackage: String?,
        /** Uso do app rastreado no frame anterior (para detectar o cruzamento do limiar). */
        val previousUsedMillis: Long,
        /** Uso do app rastreado agora. */
        val currentUsedMillis: Long,
        val limitMillis: Long,
        /** true se há passe do dia ativo (pago) para o app rastreado. */
        val unlockActive: Boolean,
    )

    data class Decision(
        val warn: Boolean,
        val block: Boolean,
    ) {
        companion object {
            val NONE = Decision(warn = false, block = false)
        }
    }

    /**
     * true quando o app rastreado ainda é o que está na frente. Quando é false, o loop
     * está observando um app que o usuário já deixou — não deve avisar nem bloquear
     * (e o serviço deveria ter parado o tracking; ver o fix em onAccessibilityEvent).
     */
    fun isStillForeground(frame: Frame): Boolean =
        frame.foregroundPackage == frame.trackedPackage

    /**
     * Decide aviso e bloqueio para o frame. Ambos exigem que o app rastreado ainda esteja
     * em foreground — essa é a correção do aviso-fantasma. O bloqueio ainda exige limite
     * atingido e ausência de passe do dia; o aviso exige o cruzamento do limiar de 5 min
     * ([LimitWarningPolicy.shouldWarn]) e que o limite ainda não tenha estourado.
     */
    fun decide(frame: Frame): Decision {
        if (!isStillForeground(frame)) return Decision.NONE

        val limitReached = frame.currentUsedMillis >= frame.limitMillis
        val block = limitReached && !frame.unlockActive

        // Não avisa se já vamos bloquear neste frame (o bloqueio é a mensagem mais forte).
        val warn = !limitReached &&
            LimitWarningPolicy.shouldWarn(
                previousUsedMillis = frame.previousUsedMillis,
                currentUsedMillis = frame.currentUsedMillis,
                limitMillis = frame.limitMillis,
            )

        return Decision(warn = warn, block = block)
    }
}
