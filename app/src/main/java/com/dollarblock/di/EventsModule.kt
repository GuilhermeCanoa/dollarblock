package com.dollarblock.di

import com.dollarblock.data.local.db.DollarBlockDatabase
import com.dollarblock.data.local.db.dao.EventDao
import com.dollarblock.data.repository.EventsRepositoryImpl
import com.dollarblock.domain.repository.EventsRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * DI da feature de eventos (histórico de bloqueio/desbloqueio): DAO + repositório.
 * Dono: ver .github/CODEOWNERS.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class EventsModule {

    @Binds
    @Singleton
    abstract fun bindEventsRepository(impl: EventsRepositoryImpl): EventsRepository

    companion object {
        @Provides
        fun provideEventDao(database: DollarBlockDatabase): EventDao = database.eventDao()
    }
}
