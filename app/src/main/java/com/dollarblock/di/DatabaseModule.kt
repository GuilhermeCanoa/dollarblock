package com.dollarblock.di

import android.content.Context
import androidx.room.Room
import com.dollarblock.data.local.db.DollarBlockDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Provê apenas o [DollarBlockDatabase] (recurso compartilhado por todas as features).
 * Cada feature provê o seu próprio DAO e vincula o seu repositório em um módulo próprio
 * (ex.: [EventsModule], [MonitoredAppModule]) — assim dois devs raramente tocam o mesmo
 * arquivo de DI. Ver docs/MERGE_HOTSPOTS.md seção 6.
 */
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
}
