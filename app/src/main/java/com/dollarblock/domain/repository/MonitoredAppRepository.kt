package com.dollarblock.domain.repository

import com.dollarblock.domain.model.MonitoredAppUsage
import kotlinx.coroutines.flow.Flow

interface MonitoredAppRepository {

    /** Apps monitorados combinados com o uso de hoje, atualizados em tempo real (Room Flow). */
    fun observeMonitoredAppsUsage(): Flow<List<MonitoredAppUsage>>

    /** Ativa/desativa o monitoramento de um app. Cria o registro em Room se não existir. */
    suspend fun setMonitored(packageName: String, appName: String, isMonitored: Boolean)

    /** Força uma sincronização imediata do uso de hoje (fora do ciclo do Worker). */
    suspend fun syncTodayUsage()
}
