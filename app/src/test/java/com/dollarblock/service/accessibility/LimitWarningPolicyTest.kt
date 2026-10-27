package com.dollarblock.service.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LimitWarningPolicyTest {

    private val limit = 30 * 60_000L // 30 min
    private val warnAt = limit - LimitWarningPolicy.WARNING_THRESHOLD_MS // 25 min

    private fun warn(current: Long, limitMs: Long = limit, warned: Boolean = false) =
        LimitWarningPolicy.shouldWarn(currentUsedMillis = current, limitMillis = limitMs, alreadyWarnedToday = warned)

    @Test
    fun `dispara ao entrar na janela de 5 min restantes`() {
        assertTrue(warn(warnAt))
        assertTrue(warn(warnAt + 60_000L))
    }

    @Test
    fun `nao dispara antes da janela`() {
        assertFalse(warn(warnAt - 1))
    }

    @Test
    fun `nao dispara de novo se ja avisou hoje`() {
        assertFalse(warn(warnAt + 2000, warned = true))
    }

    @Test
    fun `nao dispara com o limite atingido ou estourado`() {
        assertFalse(warn(limit))
        assertFalse(warn(limit + 5000))
    }

    @Test
    fun `limite menor que a janela avisa no primeiro uso do dia`() {
        val shortLimit = 2 * 60_000L
        assertFalse(warn(0, shortLimit))
        assertTrue(warn(1000, shortLimit))
        assertFalse(warn(2000, shortLimit, warned = true))
    }

    @Test
    fun `limite curto volta a avisar no dia seguinte`() {
        // Bug antigo: o uso de ontem ficava em memória como "anterior" e o aviso de limites
        // de até 5 min nunca mais saía. Agora o "já avisou" é por dia — dia novo, aviso novo.
        val shortLimit = 5 * 60_000L
        assertTrue(warn(30_000L, shortLimit, warned = false))
    }

    @Test
    fun `limite invalido nunca dispara`() {
        assertFalse(warn(1000, limitMs = 0))
    }

    @Test
    fun `avisa uma unica vez em polls sucessivos dentro da janela`() {
        // Simula o loop de tracking com o "já avisou hoje" persistido entre frames.
        val frames = listOf(warnAt - 3000, warnAt - 1000, warnAt + 1000, warnAt + 3000, limit - 500)
        var warned = false
        var warnCount = 0
        for (current in frames) {
            if (warn(current, warned = warned)) {
                warnCount++
                warned = true
            }
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
