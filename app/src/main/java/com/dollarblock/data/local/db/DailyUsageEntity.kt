package com.dollarblock.data.local.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Tempo de uso acumulado de um app em um dia específico (epochDay local do dispositivo).
 * Atualizado periodicamente pelo [com.dollarblock.service.monitoring.UsageSyncWorker]
 * a partir do UsageStatsManager.
 */
@Entity(
    tableName = "daily_usage",
    indices = [Index(value = ["packageName", "epochDay"], unique = true)],
)
data class DailyUsageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val epochDay: Long,
    val usedMillis: Long,
    val updatedAt: Long,
)
