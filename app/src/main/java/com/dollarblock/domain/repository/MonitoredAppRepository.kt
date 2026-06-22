package com.dollarblock.domain.repository

import com.dollarblock.domain.model.MonitoredAppUsage
import kotlinx.coroutines.flow.Flow

interface MonitoredAppRepository {

    /** Apps monitorados combinados com o uso de hoje, atualizados em tempo real (Room Flow). */
    fun observeMonitoredAppsUsage(): Flow<List<MonitoredAppUsage>>

    /** Ativa/desativa o monitoramento de um app. Cria o registro em Room se não existir. */
    suspend fun setMonitored(packageName: String, appName: String, isMonitored: Boolean)

    /** Define (ou remove, com null) o limite diário de uso em minutos para um app já monitorado. */
    suspend fun setDailyLimit(packageName: String, dailyLimitMinutes: Int?)

    /**
     * Força uma sincronização imediata do uso de hoje (fora do ciclo do Worker).
     * Quando [foregroundPackage]/[foregroundSinceMillis] são informados (app monitorado
     * atualmente aberto e o epoch millis de quando entrou em foreground), o tempo da
     * sessão em andamento desse app é incluído — sem isso, `totalTimeInForeground` só
     * reflete sessões já fechadas e o uso fica "congelado" enquanto o app permanece aberto.
     */
    suspend fun syncTodayUsage(foregroundPackage: String? = null, foregroundSinceMillis: Long? = null)
}
