package com.dollarblock.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.dollarblock.data.local.db.dao.EventDao
import com.dollarblock.data.local.db.entity.BlockEventEntity
import com.dollarblock.data.local.db.entity.UnlockEventEntity

@Database(
    entities = [BlockEventEntity::class, UnlockEventEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class DollarBlockDatabase : RoomDatabase() {
    abstract fun eventDao(): EventDao
}
