package com.dollarblock.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.dollarblock.domain.model.BlockReason

@Entity(tableName = "block_events")
data class BlockEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val appLabel: String,
    val timestamp: Long,
    val reason: BlockReason = BlockReason.DAILY_LIMIT,
)
