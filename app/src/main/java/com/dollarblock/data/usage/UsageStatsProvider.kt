package com.dollarblock.data.usage

import android.app.AppOpsManager
import android.app.usage.UsageEvents
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
     *
     * IMPORTANTE: `UsageStats.totalTimeInForeground` só contabiliza sessões já
     * *fechadas* (quando o app sai do foreground) — o app atualmente em foreground
     * ainda não teve sua sessão em andamento contabilizada aqui. Use a sobrecarga
     * [getTodayUsageByPackage] com `foregroundPackage`/`foregroundSinceMillis` para
     * incluir esse tempo (o `DollarBlockAccessibilityService` conhece o momento exato
     * em que o app monitorado entrou em foreground).
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

    /**
     * Versão de [getTodayUsageByPackage] que soma, ao agregado já fechado, o tempo da
     * sessão em andamento de [foregroundPackage] (se informado), contado a partir de
     * [foregroundSinceMillis] (epoch millis de quando esse app entrou em foreground).
     * Use esta versão quando houver um app monitorado atualmente aberto, para que o
     * uso sincronizado em Room (e refletido na tela Apps) não fique "congelado" até
     * o usuário sair do app.
     */
    suspend fun getTodayUsageByPackage(
        foregroundPackage: String?,
        foregroundSinceMillis: Long?,
    ): Map<String, Long> = withContext(Dispatchers.IO) {
        if (!hasUsageAccess()) return@withContext emptyMap()

        val zone = ZoneId.systemDefault()
        val startOfDay = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
        val now = System.currentTimeMillis()

        val closed = usageStatsManager.queryAndAggregateUsageStats(startOfDay, now)
            .mapValues { (_, stats) -> stats.totalTimeInForeground }
            .filterValues { it > 0L }

        if (foregroundPackage == null || foregroundSinceMillis == null || foregroundSinceMillis !in startOfDay..now) {
            return@withContext closed
        }

        val ongoingMillis = now - foregroundSinceMillis
        closed + (foregroundPackage to (closed[foregroundPackage] ?: 0L) + ongoingMillis)
    }

    /**
     * Calcula o tempo de uso de [packageName] hoje via eventos individuais
     * (ACTIVITY_RESUMED / ACTIVITY_PAUSED), somando cada sessão fechada.
     * Se [ongoingSessionSince] for informado, inclui também o tempo da sessão em andamento.
     * Retorna milissegundos totais de uso desde a meia-noite local.
     */
    suspend fun getTodayUsageMillisViaEvents(
        packageName: String,
        ongoingSessionSince: Long? = null,
    ): Long = withContext(Dispatchers.IO) {
        if (!hasUsageAccess()) return@withContext 0L

        val zone = ZoneId.systemDefault()
        val startOfDay = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
        val now = System.currentTimeMillis()

        val events = usageStatsManager.queryEvents(startOfDay, now)
        var totalMillis = 0L
        var resumeTime = -1L
        val event = UsageEvents.Event()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.packageName != packageName) continue
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> resumeTime = event.timeStamp
                UsageEvents.Event.ACTIVITY_PAUSED,
                UsageEvents.Event.ACTIVITY_STOPPED -> {
                    if (resumeTime >= 0L) {
                        totalMillis += event.timeStamp - resumeTime
                        resumeTime = -1L
                    }
                }
            }
        }

        // Sessão em andamento (fornecida pelo serviço de acessibilidade)
        val sessionStart = ongoingSessionSince ?: if (resumeTime >= 0L) resumeTime else -1L
        if (sessionStart in startOfDay..now) {
            totalMillis += now - sessionStart
        }

        totalMillis
    }

    /**
     * Versão multi-app de [getTodayUsageMillisViaEvents]: percorre os eventos uma única vez
     * e acumula o uso de todos os apps em [packages]. [ongoingPackage]/[ongoingSessionSince]
     * adicionam a sessão em andamento do app atualmente em foreground.
     */
    suspend fun getTodayUsageMillisViaEvents(
        packages: Set<String>,
        ongoingPackage: String? = null,
        ongoingSessionSince: Long? = null,
    ): Map<String, Long> = withContext(Dispatchers.IO) {
        if (!hasUsageAccess() || packages.isEmpty()) return@withContext emptyMap()

        val zone = ZoneId.systemDefault()
        val startOfDay = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
        val now = System.currentTimeMillis()

        val totals = mutableMapOf<String, Long>()
        val resumes = mutableMapOf<String, Long>()
        val events = usageStatsManager.queryEvents(startOfDay, now)
        val event = UsageEvents.Event()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName
            if (pkg !in packages) continue
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> resumes[pkg] = event.timeStamp
                UsageEvents.Event.ACTIVITY_PAUSED,
                UsageEvents.Event.ACTIVITY_STOPPED -> {
                    val start = resumes.remove(pkg) ?: continue
                    totals[pkg] = (totals[pkg] ?: 0L) + (event.timeStamp - start)
                }
            }
        }

        // Sessão em andamento do app em foreground
        if (ongoingPackage != null && ongoingSessionSince != null && ongoingPackage in packages) {
            val sessionStart = if (ongoingSessionSince in startOfDay..now) ongoingSessionSince
                              else resumes[ongoingPackage] ?: -1L
            if (sessionStart >= startOfDay) {
                totals[ongoingPackage] = (totals[ongoingPackage] ?: 0L) + (now - sessionStart)
            }
        }

        totals
    }

    /**
     * Tempo de uso em foreground de [packageName] desde [sinceMs] (epoch millis) até agora.
     * Usa eventos UsageStats — só conta tempo real em foreground, não é afetado por overlays.
     * Inclui sessão em andamento se o app ainda estiver aberto (ACTIVITY_RESUMED sem PAUSED).
     */
    suspend fun getUsageMillisSince(packageName: String, sinceMs: Long): Long = withContext(Dispatchers.IO) {
        if (!hasUsageAccess()) return@withContext 0L
        val now = System.currentTimeMillis()
        val from = sinceMs.coerceAtLeast(0L)

        val events = usageStatsManager.queryEvents(from, now)
        var total = 0L
        var resumeTime = -1L
        val ev = UsageEvents.Event()

        while (events.hasNextEvent()) {
            events.getNextEvent(ev)
            if (ev.packageName != packageName) continue
            when (ev.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> resumeTime = ev.timeStamp
                UsageEvents.Event.ACTIVITY_PAUSED,
                UsageEvents.Event.ACTIVITY_STOPPED -> {
                    if (resumeTime >= 0L) {
                        total += ev.timeStamp - resumeTime
                        resumeTime = -1L
                    }
                }
            }
        }
        if (resumeTime >= 0L) total += now - resumeTime
        total
    }

    /** Uso total de cada app nos últimos 7 dias (millis de foreground). */
    suspend fun getWeeklyUsageByPackage(): Map<String, Long> = withContext(Dispatchers.IO) {
        if (!hasUsageAccess()) return@withContext emptyMap()
        val now = System.currentTimeMillis()
        val sevenDaysAgo = now - 7L * 24 * 60 * 60 * 1000
        usageStatsManager.queryAndAggregateUsageStats(sevenDaysAgo, now)
            .mapValues { (_, stats) -> stats.totalTimeInForeground }
            .filterValues { it > 0L }
    }

    /** epochDay local consistente com o usado nas entidades Room. */
    fun currentEpochDay(): Long = LocalDate.now(ZoneId.systemDefault()).toEpochDay()

    /**
     * Tempo de uso de hoje (em minutos) de um único app, desde a meia-noite local.
     * Usado pelo `DollarBlockAccessibilityService` para decidir bloqueio em tempo real,
     * sem precisar agregar o uso de todos os apps instalados a cada troca de janela.
     *
     * IMPORTANTE: `UsageStats.totalTimeInForeground` só contabiliza sessões já *fechadas*
     * (quando o app sai do foreground) — enquanto o app permanece aberto, esse valor fica
     * "congelado" no que já era verdade quando ele abriu. Para refletir o tempo em tempo
     * real, use [getTodayUsageMinutesIncludingOngoingSession].
     */
    suspend fun getTodayUsageMinutes(packageName: String): Int = withContext(Dispatchers.IO) {
        if (!hasUsageAccess()) return@withContext 0

        val zone = ZoneId.systemDefault()
        val startOfDay = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
        val now = System.currentTimeMillis()

        val usedMillis = usageStatsManager.queryAndAggregateUsageStats(startOfDay, now)[packageName]
            ?.totalTimeInForeground ?: 0L
        (usedMillis / 60_000L).toInt()
    }

    /**
     * Tempo de uso de hoje (em minutos) de [packageName], somando o agregado já fechado
     * (`totalTimeInForeground`) com a sessão atual em andamento, caso [foregroundSinceMillis]
     * seja informado (epoch millis de quando o app entrou em foreground agora). Isso evita a
     * defasagem de [getTodayUsageMinutes] enquanto o app permanece aberto sem trocar de janela.
     */
    suspend fun getTodayUsageMinutesIncludingOngoingSession(
        packageName: String,
        foregroundSinceMillis: Long?,
    ): Int = withContext(Dispatchers.IO) {
        if (!hasUsageAccess()) return@withContext 0

        val zone = ZoneId.systemDefault()
        val startOfDay = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
        val now = System.currentTimeMillis()

        val closedMillis = usageStatsManager.queryAndAggregateUsageStats(startOfDay, now)[packageName]
            ?.totalTimeInForeground ?: 0L
        val ongoingMillis = if (foregroundSinceMillis != null && foregroundSinceMillis in startOfDay..now) {
            now - foregroundSinceMillis
        } else {
            0L
        }
        ((closedMillis + ongoingMillis) / 60_000L).toInt()
    }
}
