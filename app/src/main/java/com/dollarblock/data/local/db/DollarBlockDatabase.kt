package com.dollarblock.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [MonitoredAppEntity::class, DailyUsageEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class DollarBlockDatabase : RoomDatabase() {
    abstract fun monitoredAppDao(): MonitoredAppDao
    abstract fun dailyUsageDao(): DailyUsageDao
}
