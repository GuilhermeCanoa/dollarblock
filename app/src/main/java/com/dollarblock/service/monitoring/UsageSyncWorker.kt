package com.dollarblock.service.monitoring

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.dollarblock.domain.repository.MonitoredAppRepository
import com.dollarblock.service.nudge.NudgeEngine
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Sincroniza periodicamente o tempo de uso (UsageStatsManager) dos apps monitorados
 * para o Room (DailyUsageEntity). Agendado como trabalho periódico (mínimo 15min,
 * limite do Android para PeriodicWorkRequest) por [UsageSyncScheduler].
 *
 * Não substitui o AccessibilityService: este Worker mede "quanto tempo passou",
 * o AccessibilityService detecta "o que está em primeiro plano agora" para bloqueio
 * instantâneo (ver ARCHITECTURE.md, ADR-001).
 */
@HiltWorker
class UsageSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: MonitoredAppRepository,
    private val nudgeEngine: NudgeEngine,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            repository.syncTodayUsage()
            // Fechamento do dia (D18–D20): a partir das 21h, uma vez por dia, com o uso recém-sincronizado.
            runCatching { nudgeEngine.onDailyCloseCheck() }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "usage_sync_work"
    }
}
