package com.dollarblock.data.local.db

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyUsageDao {

    @Query("SELECT * FROM daily_usage WHERE epochDay = :epochDay")
    fun observeForDay(epochDay: Long): Flow<List<DailyUsageEntity>>

    @Query("SELECT * FROM daily_usage WHERE epochDay BETWEEN :startDay AND :endDay")
    fun observeRange(startDay: Long, endDay: Long): Flow<List<DailyUsageEntity>>

    @Query("SELECT * FROM daily_usage WHERE packageName = :packageName AND epochDay = :epochDay LIMIT 1")
    suspend fun getForDay(packageName: String, epochDay: Long): DailyUsageEntity?

    @Query("UPDATE daily_usage SET usedMillis = :usedMillis, updatedAt = :updatedAt WHERE packageName = :packageName AND epochDay = :epochDay")
    suspend fun updateUsage(packageName: String, epochDay: Long, usedMillis: Long, updatedAt: Long): Int

    @Query("INSERT OR IGNORE INTO daily_usage (packageName, epochDay, usedMillis, updatedAt) VALUES (:packageName, :epochDay, :usedMillis, :updatedAt)")
    suspend fun insertIfNotExists(packageName: String, epochDay: Long, usedMillis: Long, updatedAt: Long)

    suspend fun upsertUsage(packageName: String, epochDay: Long, usedMillis: Long, updatedAt: Long) {
        if (updateUsage(packageName, epochDay, usedMillis, updatedAt) == 0) {
            insertIfNotExists(packageName, epochDay, usedMillis, updatedAt)
        }
    }
}
