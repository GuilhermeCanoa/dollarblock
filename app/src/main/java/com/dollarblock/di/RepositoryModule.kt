package com.dollarblock.di

import com.dollarblock.data.repository.EventsRepositoryImpl
import com.dollarblock.data.repository.MonitoredAppRepositoryImpl
import com.dollarblock.domain.repository.EventsRepository
import com.dollarblock.domain.repository.MonitoredAppRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindEventsRepository(impl: EventsRepositoryImpl): EventsRepository

    @Binds
    @Singleton
    abstract fun bindMonitoredAppRepository(impl: MonitoredAppRepositoryImpl): MonitoredAppRepository
}
