package com.dollarblock

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.dollarblock.core.locale.LocaleManager
import com.dollarblock.data.local.prefs.LanguagePreferences
import com.dollarblock.service.monitoring.UsageSyncScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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

    @Inject
    lateinit var languagePreferences: LanguagePreferences

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        // Aplica o idioma persistido antes de qualquer Activity ser criada, para que os
        // recursos certos (values / values-pt) sejam resolvidos já no cold start. O
        // `setApplicationLocales` é idempotente — se o locale já estiver correto, é no-op.
        // Leitura única e rápida do DataStore (chave pequena); seguro em onCreate.
        val language = runBlocking { languagePreferences.language.first() }
        LocaleManager.apply(language)

        usageSyncScheduler.schedule()
    }
}
