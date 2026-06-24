package com.dollarblock.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.dollarblock.data.local.db.entity.BlockEventEntity
import com.dollarblock.data.local.db.entity.UnlockEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {

    @Insert
    suspend fun insertBlock(event: BlockEventEntity)

    @Insert
    suspend fun insertUnlock(event: UnlockEventEntity)

    @Query("SELECT * FROM block_events ORDER BY timestamp DESC LIMIT :limit")
    fun recentBlocks(limit: Int): Flow<List<BlockEventEntity>>

    @Query("SELECT COUNT(*) FROM block_events WHERE timestamp >= :startMs AND timestamp <= :endMs")
    fun countBlocksInRange(startMs: Long, endMs: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM block_events WHERE timestamp >= :startMs")
    fun countBlocksSince(startMs: Long): Flow<Int>

    @Query("SELECT * FROM unlock_events ORDER BY timestamp DESC LIMIT :limit")
    fun recentUnlocks(limit: Int): Flow<List<UnlockEventEntity>>
}
