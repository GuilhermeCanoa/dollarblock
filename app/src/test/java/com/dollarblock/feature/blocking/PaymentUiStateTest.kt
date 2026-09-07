package com.dollarblock.feature.blocking

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regras da saída de cortesia (E17). O ponto crítico coberto aqui: o usuário nunca fica
 * trancado fora do app por erro nosso, e cancelar a compra nunca vira um bypass grátis.
 */
class PaymentUiStateTest {

    private val idle = PaymentUiState()
    private val readyToPay = PaymentUiState(ready = true)

    private fun PaymentUiState.on(vararg events: PaymentEvent): PaymentUiState =
        events.fold(this) { state, event -> state.reduce(event) }

    // ——— A regra que não pode quebrar: cancelar não dá cortesia ———

    @Test
    fun `desistir da compra nao libera cortesia`() {
        val state = readyToPay.on(PaymentEvent.PayClicked, PaymentEvent.Cancelled)

        assertFalse("cancelar viraria bypass universal do bloqueio", state.courtesyAvailable)
        assertFalse(state.inProgress)
    }

    @Test
    fun `cancelar repetidas vezes nunca acumula cortesia`() {
        var state = readyToPay
        repeat(5) {
            state = state.on(PaymentEvent.PayClicked, PaymentEvent.Cancelled)
        }

        assertFalse(state.courtesyAvailable)
    }

    // ——— Erros liberam a cortesia ———

    @Test
    fun `erro na cobranca libera cortesia e encerra o progresso`() {
        val state = readyToPay.on(PaymentEvent.PayClicked, PaymentEvent.Error)

        assertTrue(state.courtesyAvailable)
        assertFalse(state.inProgress)
    }

    @Test
    fun `loja que nunca fica pronta libera cortesia no timeout`() {
        val state = idle.on(PaymentEvent.ReadyTimeout)

        assertTrue("sem loja nao ha como cobrar — nao pode trancar o usuario", state.courtesyAvailable)
    }

    @Test
    fun `timeout nao libera cortesia se a loja ficou pronta antes`() {
        val state = idle.on(
            PaymentEvent.ReadinessChanged(ready = true),
            PaymentEvent.ReadyTimeout,
        )

        assertFalse(state.courtesyAvailable)
    }

    @Test
    fun `timeout que chega durante uma cobranca em andamento e ignorado`() {
        val state = readyToPay.on(PaymentEvent.PayClicked, PaymentEvent.ReadyTimeout)

        assertFalse(state.courtesyAvailable)
        assertTrue(state.inProgress)
    }

    // ——— Retentativa ———

    @Test
    fun `tentar de novo limpa a cortesia enquanto a cobranca roda`() {
        val failed = readyToPay.on(PaymentEvent.PayClicked, PaymentEvent.Error)

        val retrying = failed.on(PaymentEvent.PayClicked)

        assertFalse(retrying.courtesyAvailable)
        assertTrue(retrying.inProgress)
    }

    @Test
    fun `falhar de novo depois da retentativa religa a cortesia`() {
        val state = readyToPay.on(
            PaymentEvent.PayClicked,
            PaymentEvent.Error,
            PaymentEvent.PayClicked,
            PaymentEvent.Error,
        )

        assertTrue(state.courtesyAvailable)
    }

    @Test
    fun `botao de tentar de novo so aparece quando ainda da para cobrar`() {
        val failedWithStore = readyToPay.on(PaymentEvent.PayClicked, PaymentEvent.Error)
        val failedWithoutStore = idle.on(PaymentEvent.ReadyTimeout)

        assertTrue(failedWithStore.canRetry)
        assertFalse("sem loja pronta nao ha o que retentar", failedWithoutStore.canRetry)
    }

    // ——— Sucesso ———

    @Test
    fun `compra concluida encerra o progresso sem oferecer cortesia`() {
        val state = readyToPay.on(PaymentEvent.PayClicked, PaymentEvent.Completed)

        assertFalse(state.inProgress)
        assertFalse(state.courtesyAvailable)
    }

    @Test
    fun `sucesso depois de uma falha limpa a cortesia`() {
        val state = readyToPay.on(
            PaymentEvent.PayClicked,
            PaymentEvent.Error,
            PaymentEvent.PayClicked,
            PaymentEvent.Completed,
        )

        assertFalse(state.courtesyAvailable)
    }

    // ——— Estado inicial ———

    @Test
    fun `estado inicial nao oferece cortesia nem pagamento`() {
        assertFalse(idle.courtesyAvailable)
        assertFalse(idle.inProgress)
        assertFalse(idle.ready)
        assertFalse(idle.canRetry)
    }

    @Test
    fun `loja pode ficar pronta depois e habilita a cobranca`() {
        val state = idle.on(PaymentEvent.ReadinessChanged(ready = true))

        assertTrue(state.ready)
        assertFalse(state.courtesyAvailable)
    }

    @Test
    fun `perder a conexao com a loja nao concede cortesia sozinho`() {
        // Desconexão momentânea do BillingClient: a tela volta a "indisponível", mas a
        // cortesia só entra por erro de cobrança ou pelo timeout.
        val state = readyToPay.on(PaymentEvent.ReadinessChanged(ready = false))

        assertFalse(state.ready)
        assertFalse(state.courtesyAvailable)
    }
}
