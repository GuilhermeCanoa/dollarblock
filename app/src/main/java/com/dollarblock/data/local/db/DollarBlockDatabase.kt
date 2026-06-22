package com.dollarblock.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.dollarblock.data.local.db.dao.EventDao
import com.dollarblock.data.local.db.entity.BlockEventEntity
import com.dollarblock.data.local.db.entity.UnlockEventEntity

@Database(
    entities = [
        BlockEventEntity::class,
        UnlockEventEntity::class,
        MonitoredAppEntity::class,
        DailyUsageEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class DollarBlockDatabase : RoomDatabase() {
    abstract fun eventDao(): EventDao
    abstract fun monitoredAppDao(): MonitoredAppDao
    abstract fun dailyUsageDao(): DailyUsageDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE monitored_apps ADD COLUMN usageBaselineMillis INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
