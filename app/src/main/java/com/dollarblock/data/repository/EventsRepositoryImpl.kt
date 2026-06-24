package com.dollarblock.data.repository

import com.dollarblock.data.local.db.dao.EventDao
import com.dollarblock.data.local.db.entity.BlockEventEntity
import com.dollarblock.data.local.db.entity.UnlockEventEntity
import com.dollarblock.domain.model.RecentEvent
import com.dollarblock.domain.repository.EventsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

class EventsRepositoryImpl @Inject constructor(
    private val eventDao: EventDao,
) : EventsRepository {

    override suspend fun recordBlock(packageName: String, appLabel: String) {
        eventDao.insertBlock(
            BlockEventEntity(
                packageName = packageName,
                appLabel = appLabel,
                timestamp = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun recordUnlock(
        packageName: String,
        appLabel: String,
        amount: String,
        currency: String,
        method: String,
    ) {
        eventDao.insertUnlock(
            UnlockEventEntity(
                packageName = packageName,
                appLabel = appLabel,
                timestamp = System.currentTimeMillis(),
                amount = amount,
                currency = currency,
                method = method,
            ),
        )
    }

    override fun blockAttemptsToday(): Flow<Int> {
        val startOfDay = LocalDate.now()
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        return eventDao.countBlocksSince(startOfDay)
    }

    override fun recentEvents(limit: Int): Flow<List<RecentEvent>> =
        combine(
            eventDao.recentBlocks(limit),
            eventDao.recentUnlocks(limit),
        ) { blocks, unlocks ->
            val mapped = blocks.map { RecentEvent.Blocked(it.appLabel, it.timestamp) } +
                unlocks.map {
                    RecentEvent.Unlocked(
                        appLabel = it.appLabel,
                        timestamp = it.timestamp,
                        amount = it.amount,
                        currency = it.currency,
                        method = it.method,
                    )
                }
            mapped.sortedByDescending { it.timestamp }.take(limit)
        }
}
