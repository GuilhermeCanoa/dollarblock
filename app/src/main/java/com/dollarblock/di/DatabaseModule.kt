package com.dollarblock.di

import android.content.Context
import androidx.room.Room
import com.dollarblock.data.local.db.DailyUsageDao
import com.dollarblock.data.local.db.DollarBlockDatabase
import com.dollarblock.data.local.db.MonitoredAppDao
import com.dollarblock.data.local.db.dao.EventDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): DollarBlockDatabase =
        Room.databaseBuilder(
            context,
            DollarBlockDatabase::class.java,
            "dollarblock.db",
        ).build()

    @Provides
    fun provideEventDao(database: DollarBlockDatabase): EventDao = database.eventDao()

    @Provides
    fun provideMonitoredAppDao(database: DollarBlockDatabase): MonitoredAppDao =
        database.monitoredAppDao()

    @Provides
    fun provideDailyUsageDao(database: DollarBlockDatabase): DailyUsageDao =
        database.dailyUsageDao()
}
