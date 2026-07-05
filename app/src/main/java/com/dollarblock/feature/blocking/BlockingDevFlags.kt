package com.dollarblock.feature.blocking

/**
 * Flags de desenvolvimento do fluxo de bloqueio/pagamento. Todas só têm efeito em
 * builds debug (a UI combina cada flag com `BuildConfig.DEBUG`).
 */
object BlockingDevFlags {
    /**
     * Liga o botão "Simular pagamento" na tela de bloqueio, para desenvolver sem
     * cobranças reais. Desligado por padrão — o caminho de simulação continua no
     * código (`onPaymentSuccess(PaymentMethod.SIMULATED)`), só some da UI.
     */
    const val SIMULATED_PAYMENTS = false
}
