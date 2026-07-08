package com.dollarblock.feature.blocking.payment

/**
 * Provedores de cobrança do passe do dia.
 *
 * [PLAY_BILLING] é o provider ativo (E16): a Payments Policy do Google Play exige
 * Google Play Billing para desbloqueio de funcionalidade dentro do app, então a
 * cobrança via Stripe não pode ir para a Play Store sem enrollment em programa de
 * billing alternativo (ver docs/specs/E16-compliance-play-store-pagamentos.md).
 *
 * [STRIPE_GOOGLE_PAY] é o fluxo Google Pay + Stripe + backend AWS (E9), mantido
 * compilável para eventual reuso (ex.: distribuição fora da Play Store ou enrollment
 * futuro no User Choice Billing), mas desabilitado por padrão.
 */
enum class PaymentProvider {
    PLAY_BILLING,
    STRIPE_GOOGLE_PAY,
}

object PaymentConfig {
    /** Provider usado pela tela de bloqueio. Trocar aqui reativa o caminho Stripe inteiro. */
    val PROVIDER = PaymentProvider.PLAY_BILLING

    /**
     * ID do produto consumível no Play Console (um "passe do dia" por compra).
     * Precisa existir e estar ativo no Play Console antes da submissão — ver
     * docs/PLAYSTORE_PRIVACY_SUBMISSION.md §6.
     */
    const val PLAY_PRODUCT_DAY_PASS = "day_pass"
}
