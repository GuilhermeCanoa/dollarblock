package com.dollarblock.di

import com.dollarblock.data.local.db.DailyUsageDao
import com.dollarblock.data.local.db.DollarBlockDatabase
import com.dollarblock.data.local.db.MonitoredAppDao
import com.dollarblock.data.repository.MonitoredAppRepositoryImpl
import com.dollarblock.domain.repository.MonitoredAppRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * DI da feature de apps monitorados: DAOs (monitorados + uso diário) + repositório.
 * Dono: ver .github/CODEOWNERS.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class MonitoredAppModule {

    @Binds
    @Singleton
    abstract fun bindMonitoredAppRepository(
        impl: MonitoredAppRepositoryImpl,
    ): MonitoredAppRepository

    companion object {
        @Provides
        fun provideMonitoredAppDao(database: DollarBlockDatabase): MonitoredAppDao =
            database.monitoredAppDao()

        @Provides
        fun provideDailyUsageDao(database: DollarBlockDatabase): DailyUsageDao =
            database.dailyUsageDao()
    }
}
