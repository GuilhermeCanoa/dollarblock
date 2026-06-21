package com.dollarblock.service.monitoring

import android.content.Context
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.ExistingPeriodicWorkPolicy
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Agenda a sincronização periódica de uso ([UsageSyncWorker]). Chamado uma vez
 * na inicialização do app (DollarBlockApp.onCreate).
 */
@Singleton
class UsageSyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun schedule() {
        val request = PeriodicWorkRequestBuilder<UsageSyncWorker>(
            MIN_INTERVAL_MINUTES, TimeUnit.MINUTES,
        ).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UsageSyncWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    private companion object {
        // 15 minutos é o intervalo mínimo permitido pelo Android para PeriodicWorkRequest.
        const val MIN_INTERVAL_MINUTES = 15L
    }
}
