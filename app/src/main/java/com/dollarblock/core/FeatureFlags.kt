package com.dollarblock.core

/**
 * Feature flags de build — chaves simples para ligar/desligar comportamento
 * sem remover código. Trocar o valor e recompilar.
 */
object FeatureFlags {

    /** Splash rápida (logo + nome) ao abrir o app. */
    const val SPLASH_ENABLED = true

    /** Duração da splash antes do fade-out. */
    const val SPLASH_DURATION_MS = 1200L
}
