package com.dollarblock.data.local.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface MonitoredAppDao {

    @Query("SELECT * FROM monitored_apps ORDER BY appName ASC")
    fun observeAll(): Flow<List<MonitoredAppEntity>>

    @Query("SELECT * FROM monitored_apps WHERE isMonitored = 1")
    fun observeMonitored(): Flow<List<MonitoredAppEntity>>

    @Query("SELECT packageName FROM monitored_apps WHERE isMonitored = 1")
    suspend fun getMonitoredPackages(): List<String>

    @Query("SELECT * FROM monitored_apps WHERE packageName = :packageName")
    suspend fun getByPackage(packageName: String): MonitoredAppEntity?

    @Upsert
    suspend fun upsert(entity: MonitoredAppEntity)

    @Query("UPDATE monitored_apps SET isMonitored = :isMonitored WHERE packageName = :packageName")
    suspend fun setMonitored(packageName: String, isMonitored: Boolean)
}
