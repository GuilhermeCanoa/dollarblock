package com.dollarblock.service.accessibility

import org.junit.Assert.assertEquals
import org.junit.Test

class LimitWarningPolicyTest {

    private val limit = 30 * 60_000L // 30 min

    @Test
    fun `dispara ao cruzar a janela de 5 min restantes`() {
        val warnAt = limit - LimitWarningPolicy.WARNING_THRESHOLD_MS // 25 min

        assertEquals(
            true,
            LimitWarningPolicy.shouldWarn(previousUsedMillis = warnAt - 1000, currentUsedMillis = warnAt, limitMillis = limit),
        )
    }

    @Test
    fun `nao dispara antes da janela`() {
        val warnAt = limit - LimitWarningPolicy.WARNING_THRESHOLD_MS

        assertEquals(
            false,
            LimitWarningPolicy.shouldWarn(previousUsedMillis = warnAt - 5000, currentUsedMillis = warnAt - 2000, limitMillis = limit),
        )
    }

    @Test
    fun `nao dispara de novo enquanto permanece na janela`() {
        val warnAt = limit - LimitWarningPolicy.WARNING_THRESHOLD_MS

        assertEquals(
            false,
            LimitWarningPolicy.shouldWarn(previousUsedMillis = warnAt + 1000, currentUsedMillis = warnAt + 2000, limitMillis = limit),
        )
    }

    @Test
    fun `nao dispara apos estourar o limite`() {
        assertEquals(
            false,
            LimitWarningPolicy.shouldWarn(previousUsedMillis = limit - 1000, currentUsedMillis = limit + 1000, limitMillis = limit),
        )
    }

    @Test
    fun `limite menor que a janela avisa uma unica vez no primeiro uso`() {
        val shortLimit = 2 * 60_000L // 2 min < janela de 5 min: a janela inteira cabe no limite

        assertEquals(
            true,
            LimitWarningPolicy.shouldWarn(previousUsedMillis = 0, currentUsedMillis = 1000, limitMillis = shortLimit),
        )
        assertEquals(
            false,
            LimitWarningPolicy.shouldWarn(previousUsedMillis = 1000, currentUsedMillis = 2000, limitMillis = shortLimit),
        )
    }

    @Test
    fun `limite invalido nunca dispara`() {
        assertEquals(false, LimitWarningPolicy.shouldWarn(0, 1000, 0))
    }

    @Test
    fun `dispara quando cruza exatamente no ponto de aviso`() {
        val warnAt = limit - LimitWarningPolicy.WARNING_THRESHOLD_MS // 25 min
        // previous logo abaixo, current exatamente em warnAt → deve avisar.
        assertEquals(
            true,
            LimitWarningPolicy.shouldWarn(previousUsedMillis = warnAt - 1, currentUsedMillis = warnAt, limitMillis = limit),
        )
    }

    @Test
    fun `nao dispara quando o uso ja atingiu o limite exato neste frame`() {
        val warnAt = limit - LimitWarningPolicy.WARNING_THRESHOLD_MS
        // Pulou direto de antes da janela para exatamente o limite: bloqueio, não aviso.
        assertEquals(
            false,
            LimitWarningPolicy.shouldWarn(previousUsedMillis = warnAt - 1000, currentUsedMillis = limit, limitMillis = limit),
        )
    }

    @Test
    fun `nao dispara quando o uso ja comecou acima do limite`() {
        assertEquals(
            false,
            LimitWarningPolicy.shouldWarn(previousUsedMillis = limit + 1000, currentUsedMillis = limit + 5000, limitMillis = limit),
        )
    }

    @Test
    fun `avisa uma unica vez em polls sucessivos dentro da janela`() {
        val warnAt = limit - LimitWarningPolicy.WARNING_THRESHOLD_MS
        // Simula o loop de tracking: cada frame usa o "current" anterior como "previous".
        val frames = listOf(warnAt - 3000, warnAt - 1000, warnAt + 1000, warnAt + 3000, limit - 500)
        var warnCount = 0
        var previous = frames.first()
        for (current in frames.drop(1)) {
            if (LimitWarningPolicy.shouldWarn(previous, current, limit)) warnCount++
            previous = current
        }
        assertEquals(1, warnCount)
    }

    @Test
    fun `minutesRemaining arredonda para cima e nunca fica abaixo de 1`() {
        // faltam 4 min e 30 s → arredonda para 5.
        assertEquals(5, LimitWarningPolicy.minutesRemaining(currentUsedMillis = limit - (4 * 60_000L + 30_000L), limitMillis = limit))
        // faltam 10 s → mínimo 1.
        assertEquals(1, LimitWarningPolicy.minutesRemaining(currentUsedMillis = limit - 10_000L, limitMillis = limit))
        // já estourou → mínimo 1, nunca negativo.
        assertEquals(1, LimitWarningPolicy.minutesRemaining(currentUsedMillis = limit + 60_000L, limitMillis = limit))
    }
}
