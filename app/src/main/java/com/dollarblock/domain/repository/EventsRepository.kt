package com.dollarblock.domain.repository

import com.dollarblock.domain.model.RecentEvent
import kotlinx.coroutines.flow.Flow

/** Registro e leitura de eventos de bloqueio e desbloqueio (histórico). */
interface EventsRepository {

    suspend fun recordBlock(packageName: String, appLabel: String)

    suspend fun recordUnlock(
        packageName: String,
        appLabel: String,
        amount: String,
        currency: String,
        method: String,
    )

    /** Eventos mais recentes (bloqueios + desbloqueios), ordenados do mais novo. */
    fun recentEvents(limit: Int): Flow<List<RecentEvent>>
}
