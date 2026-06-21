package com.dollarblock

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.dollarblock.service.monitoring.UsageSyncScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Ponto de entrada da aplicação. Habilita a injeção de dependências via Hilt
 * em todo o app DollarBlock, configura o WorkManager para suportar Workers com
 * Hilt e agenda a sincronização periódica de uso.
 */
@HiltAndroidApp
class DollarBlockApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var usageSyncScheduler: UsageSyncScheduler

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        usageSyncScheduler.schedule()
    }
}
