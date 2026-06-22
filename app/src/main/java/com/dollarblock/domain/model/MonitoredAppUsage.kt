package com.dollarblock.domain.model

/**
 * App monitorado combinado com seu tempo de uso de hoje.
 * `dailyLimitMinutes` é nulo até o usuário definir um valor pelo diálogo de
 * limite na tela Apps; nesse caso a UI omite a barra de progresso "uso vs limite".
 */
data class MonitoredAppUsage(
    val packageName: String,
    val appName: String,
    val isMonitored: Boolean,
    val dailyLimitMinutes: Int?,
    val usedMinutesToday: Int,
)
