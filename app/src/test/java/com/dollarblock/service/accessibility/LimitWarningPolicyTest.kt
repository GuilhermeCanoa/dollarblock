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
}
