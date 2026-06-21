package com.dollarblock.data.local.db

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyUsageDao {

    @Query("SELECT * FROM daily_usage WHERE epochDay = :epochDay")
    fun observeForDay(epochDay: Long): Flow<List<DailyUsageEntity>>

    @Query("SELECT * FROM daily_usage WHERE packageName = :packageName AND epochDay = :epochDay LIMIT 1")
    suspend fun getForDay(packageName: String, epochDay: Long): DailyUsageEntity?

    /**
     * Insere ou atualiza o uso de um app em um dia. Como `id` é autogerado, fazemos
     * upsert manual via INSERT OR REPLACE usando o id existente (se houver) para
     * respeitar o índice único (packageName, epochDay).
     */
    @Query(
        """
        INSERT INTO daily_usage (id, packageName, epochDay, usedMillis, updatedAt)
        VALUES (
            COALESCE((SELECT id FROM daily_usage WHERE packageName = :packageName AND epochDay = :epochDay), 0),
            :packageName, :epochDay, :usedMillis, :updatedAt
        )
        ON CONFLICT(packageName, epochDay) DO UPDATE SET
            usedMillis = :usedMillis,
            updatedAt = :updatedAt
        """,
    )
    suspend fun upsertUsage(packageName: String, epochDay: Long, usedMillis: Long, updatedAt: Long)
}
