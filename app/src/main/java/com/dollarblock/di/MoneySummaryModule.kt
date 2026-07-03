package com.dollarblock.di

import com.dollarblock.data.repository.MoneySummaryRepositoryImpl
import com.dollarblock.domain.repository.MoneySummaryRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * DI dos totais monetários da Home (gasto/economizado). Hoje a fonte é o Room local;
 * trocar por uma API = trocar este binding. Dono: ver .github/CODEOWNERS.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class MoneySummaryModule {

    @Binds
    @Singleton
    abstract fun bindMoneySummaryRepository(impl: MoneySummaryRepositoryImpl): MoneySummaryRepository
}
