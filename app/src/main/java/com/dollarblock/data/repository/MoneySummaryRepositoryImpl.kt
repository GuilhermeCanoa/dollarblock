package com.dollarblock.data.repository

import com.dollarblock.data.local.db.dao.EventDao
import com.dollarblock.data.local.db.dao.EventStamp
import com.dollarblock.domain.model.AppDay
import com.dollarblock.domain.model.MoneyReport
import com.dollarblock.domain.repository.MoneySummaryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

class MoneySummaryRepositoryImpl @Inject constructor(
    private val eventDao: EventDao,
    private val pricingRepository: PricingRepository,
) : MoneySummaryRepository {

    override fun observeTotalSpent(): Flow<Double> =
        eventDao.observeUnlockAmounts().map { MoneyReport.totalSpent(it) }

    override fun observeTotalSaved(): Flow<Double> {
        // Preço resolvido uma vez por coleta (rede → cache → default), o mesmo da tela de
        // bloqueio. Dias antigos usam o preço atual — o histórico de preço não é armazenado.
        val dayPassPrice = flow {
            emit(pricingRepository.getDayPassPrice().replace(',', '.').toDoubleOrNull() ?: 0.0)
        }
        return combine(
            eventDao.observeBlockStamps(),
            eventDao.observeUnlockStamps(),
            dayPassPrice,
        ) { blocks, unlocks, price ->
            MoneyReport.totalSaved(blocks.map { it.toAppDay() }, unlocks.map { it.toAppDay() }, price)
        }
    }

    private fun EventStamp.toAppDay() = AppDay(
        packageName = packageName,
        epochDay = Instant.ofEpochMilli(timestamp)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .toEpochDay(),
    )
}
