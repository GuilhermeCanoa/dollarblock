package com.dollarblock.feature.blocking

/**
 * Estado puro da tela de bloqueio no que diz respeito à cobrança do passe do dia.
 *
 * Existe separado da [BlockActivity] porque a regra da saída de cortesia (E17) é a que
 * garante que o usuário nunca fique trancado fora do app por culpa nossa — regra crítica
 * demais para viver espalhada em mutações de `MutableStateFlow` dentro da Activity, onde
 * só um emulador conseguiria testá-la. Aqui ela é uma função pura, coberta por testes JVM.
 *
 * Invariante central: [courtesyAvailable] só liga em **erro** (loja indisponível, falha do
 * Billing, cobrança recusada), nunca em **desistência** do usuário. Se cancelar concedesse
 * cortesia, abrir a folha de pagamento e cancelar seria um bypass universal do bloqueio.
 */
data class PaymentUiState(
    /** Loja conectada e produto carregado — dá para cobrar. */
    val ready: Boolean = false,
    /** Cobrança em andamento; a tela mostra o progresso e esconde os botões. */
    val inProgress: Boolean = false,
    /** Cobrança falhou por erro nosso/da loja: a tela oferece o passe de cortesia. */
    val courtesyAvailable: Boolean = false,
) {
    /** O botão "tentar pagar de novo" só aparece se ainda há como cobrar. */
    val canRetry: Boolean get() = courtesyAvailable && ready

    companion object {
        /** Tempo máximo de espera pela loja antes de liberar por cortesia. */
        const val BILLING_READY_TIMEOUT_MS = 8_000L
    }
}

/** Transições possíveis do fluxo de cobrança. */
sealed interface PaymentEvent {
    /** A loja informou se está pronta para cobrar (Billing ready / isReadyToPay). */
    data class ReadinessChanged(val ready: Boolean) : PaymentEvent

    /** O usuário tocou em pagar (ou em tentar de novo). */
    data object PayClicked : PaymentEvent

    /** Cobrança falhou por erro — loja fora, Billing recusou, charge não sucedeu. */
    data object Error : PaymentEvent

    /** O usuário fechou a folha de pagamento sem pagar. Não é erro. */
    data object Cancelled : PaymentEvent

    /** Passou o timeout e a loja nunca ficou pronta. */
    data object ReadyTimeout : PaymentEvent

    /** Compra confirmada pela loja. Encerra o progresso sem oferecer cortesia. */
    data object Completed : PaymentEvent
}

/**
 * Aplica um [PaymentEvent] ao estado. Função pura: toda a regra do E17 está aqui.
 */
fun PaymentUiState.reduce(event: PaymentEvent): PaymentUiState = when (event) {
    is PaymentEvent.ReadinessChanged -> copy(ready = event.ready)

    // Nova tentativa limpa a falha; se falhar de novo, o Error a religa.
    PaymentEvent.PayClicked -> copy(inProgress = true, courtesyAvailable = false)

    PaymentEvent.Error -> copy(inProgress = false, courtesyAvailable = true)

    // Desistência não é erro: volta ao bloqueio normal, sem cortesia.
    PaymentEvent.Cancelled -> copy(inProgress = false)

    PaymentEvent.Completed -> copy(inProgress = false, courtesyAvailable = false)

    // Só vale se a loja nunca ficou pronta e não há cobrança em andamento; caso
    // contrário o timeout chegou tarde demais e deve ser ignorado.
    PaymentEvent.ReadyTimeout ->
        if (!ready && !inProgress) copy(courtesyAvailable = true) else this
}
