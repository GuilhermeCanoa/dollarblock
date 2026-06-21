package com.dollarblock.domain.model

/**
 * App monitorado combinado com seu tempo de uso de hoje.
 * `dailyLimitMinutes` é nulo até a tela/diálogo de definição de limite existir —
 * nesse caso a UI deve omitir a barra de progresso "uso vs limite".
 */
data class MonitoredAppUsage(
    val packageName: String,
    val appName: String,
    val isMonitored: Boolean,
    val dailyLimitMinutes: Int?,
    val usedMinutesToday: Int,
)
