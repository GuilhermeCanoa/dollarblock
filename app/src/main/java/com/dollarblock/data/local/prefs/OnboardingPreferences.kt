package com.dollarblock.data.local.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.onboardingDataStore by preferencesDataStore(name = "dollarblock_onboarding")

/**
 * Persiste o estado do onboarding (E2). A flag [completed] decide o roteamento da
 * primeira execução: enquanto `false`, a [com.dollarblock.MainActivity] exibe o fluxo
 * de onboarding; quando `true`, vai direto para as abas.
 */
@Singleton
class OnboardingPreferences @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val dataStore = context.onboardingDataStore
    private val completedKey = booleanPreferencesKey("onboarding_completed")

    /** `true` quando o usuário já concluiu o onboarding pelo menos uma vez. */
    val completed: Flow<Boolean> = dataStore.data.map { it[completedKey] ?: false }

    /** Marca o onboarding como concluído (não volta a aparecer ao reabrir o app). */
    suspend fun setCompleted() {
        dataStore.edit { it[completedKey] = true }
    }
}
