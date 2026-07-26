package com.dollarblock.service.accessibility

import com.dollarblock.service.accessibility.TrackingDecision.Frame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackingDecisionTest {

    private val chrome = "com.android.chrome"
    private val whatsapp = "com.whatsapp"
    private val limit = 30 * 60_000L
    private val warnAt = limit - LimitWarningPolicy.WARNING_THRESHOLD_MS // 25 min

    private fun frame(
        tracked: String = chrome,
        foreground: String? = chrome,
        previous: Long = warnAt - 1000,
        current: Long = warnAt,
        limitMs: Long = limit,
        unlock: Boolean = false,
    ) = Frame(tracked, foreground, previous, current, limitMs, unlock)

    // --- O BUG QUE O USUÁRIO SENTIU --------------------------------------

    @Test
    fun `nao avisa quando o app rastreado nao e mais o foreground`() {
        // Chrome cruzaria o limiar de 5 min, MAS o usuário já saiu para o WhatsApp.
        // Antes do fix, isso disparava a notificação-fantasma de "Chrome prestes a bloquear".
        val d = TrackingDecision.decide(frame(tracked = chrome, foreground = whatsapp))
        assertFalse("aviso-fantasma: não deve avisar app que saiu do foreground", d.warn)
        assertFalse(d.block)
    }

    @Test
    fun `nao bloqueia quando o app rastreado nao e mais o foreground`() {
        val d = TrackingDecision.decide(
            frame(tracked = chrome, foreground = whatsapp, previous = limit, current = limit + 5000),
        )
        assertFalse(d.block)
        assertFalse(d.warn)
    }

    @Test
    fun `foreground desconhecido (null) nao dispara nada`() {
        val d = TrackingDecision.decide(frame(foreground = null))
        assertEquals(TrackingDecision.Decision.NONE, d)
    }

    // --- Comportamento correto quando AINDA em foreground -----------------

    @Test
    fun `avisa ao cruzar o limiar com o app ainda em foreground`() {
        val d = TrackingDecision.decide(frame(tracked = chrome, foreground = chrome))
        assertTrue(d.warn)
        assertFalse(d.block)
    }

    @Test
    fun `bloqueia ao atingir o limite com o app em foreground e sem passe`() {
        val d = TrackingDecision.decide(
            frame(foreground = chrome, previous = limit - 1000, current = limit, unlock = false),
        )
        assertTrue(d.block)
    }

    @Test
    fun `nao bloqueia quando ha passe do dia ativo`() {
        val d = TrackingDecision.decide(
            frame(foreground = chrome, previous = limit - 1000, current = limit + 1000, unlock = true),
        )
        assertFalse(d.block)
    }

    @Test
    fun `no frame de bloqueio nao emite tambem o aviso`() {
        // Pulou direto de antes da janela para além do limite: bloqueia, não avisa.
        val d = TrackingDecision.decide(
            frame(foreground = chrome, previous = warnAt - 2000, current = limit + 1000),
        )
        assertTrue(d.block)
        assertFalse(d.warn)
    }

    @Test
    fun `isStillForeground reflete a comparacao de pacotes`() {
        assertTrue(TrackingDecision.isStillForeground(frame(tracked = chrome, foreground = chrome)))
        assertFalse(TrackingDecision.isStillForeground(frame(tracked = chrome, foreground = whatsapp)))
        assertFalse(TrackingDecision.isStillForeground(frame(tracked = chrome, foreground = null)))
    }
}
