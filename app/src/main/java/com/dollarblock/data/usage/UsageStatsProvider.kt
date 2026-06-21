package com.dollarblock.data.usage

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Process
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lê o tempo de uso por app via [UsageStatsManager] e verifica/solicita a permissão
 * especial de Usage Access (concedida pelo usuário em Configurações do sistema).
 */
@Singleton
class UsageStatsProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val usageStatsManager: UsageStatsManager
        get() = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    /** Verifica se o usuário já concedeu a permissão de Usage Access. */
    fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** Intent para abrir a tela de Configurações onde o usuário concede Usage Access. */
    fun usageAccessSettingsIntent(): Intent =
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

    /**
     * Retorna o tempo de uso (em millis) de cada app, agregado desde a meia-noite
     * local (epochDay do dispositivo) até agora.
     */
    suspend fun getTodayUsageByPackage(): Map<String, Long> = withContext(Dispatchers.IO) {
        if (!hasUsageAccess()) return@withContext emptyMap()

        val zone = ZoneId.systemDefault()
        val startOfDay = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
        val now = System.currentTimeMillis()

        usageStatsManager.queryAndAggregateUsageStats(startOfDay, now)
            .mapValues { (_, stats) -> stats.totalTimeInForeground }
            .filterValues { it > 0L }
    }

    /** epochDay local consistente com o usado nas entidades Room. */
    fun currentEpochDay(): Long = LocalDate.now(ZoneId.systemDefault()).toEpochDay()
}
