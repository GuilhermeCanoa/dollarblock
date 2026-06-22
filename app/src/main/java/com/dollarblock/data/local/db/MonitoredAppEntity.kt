package com.dollarblock.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * App selecionado pelo usuário para monitoramento de tempo de uso.
 * `dailyLimitMinutes` é definido pelo diálogo de limite na tela Apps
 * (`DailyLimitDialog`); fica nulo enquanto nenhum limite foi configurado.
 */
@Entity(tableName = "monitored_apps")
data class MonitoredAppEntity(
    @PrimaryKey val packageName: String,
    val appName: String,
    val isMonitored: Boolean,
    val dailyLimitMinutes: Int?,
    val createdAt: Long,
    val usageBaselineMillis: Long = 0L,
)
