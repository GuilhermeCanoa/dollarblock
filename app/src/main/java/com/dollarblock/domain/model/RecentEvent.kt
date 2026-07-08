package com.dollarblock.domain.model

/** Evento exibido no histórico/Home (bloqueio ou desbloqueio pago). */
sealed interface RecentEvent {
    val appLabel: String
    val timestamp: Long

    data class Blocked(
        override val appLabel: String,
        override val timestamp: Long,
    ) : RecentEvent

    data class Unlocked(
        override val appLabel: String,
        override val timestamp: Long,
        val amount: String,
        val currency: String,
        val method: String,
    ) : RecentEvent
}

/** Métodos de pagamento registrados em um desbloqueio. */
object PaymentMethod {
    const val PLAY_BILLING = "play_billing"
    const val GOOGLE_PAY = "google_pay"
    const val SIMULATED = "simulado"
}
