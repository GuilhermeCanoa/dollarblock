package com.dollarblock.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * App selecionado pelo usuário para monitoramento de tempo de uso.
 * O limite diário (`dailyLimitMinutes`) ainda não possui UI de edição; fica nulo
 * até a tela/diálogo de definição de limite ser implementada (próxima etapa).
 */
@Entity(tableName = "monitored_apps")
data class MonitoredAppEntity(
    @PrimaryKey val packageName: String,
    val appName: String,
    val isMonitored: Boolean,
    val dailyLimitMinutes: Int?,
    val createdAt: Long,
)
