package com.dollarblock.data.local.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private val Context.pricingDataStore by preferencesDataStore(name = "dollarblock_pricing")

/**
 * Cache local do preço do passe do dia, alimentado por [PricingRepository]. Existe para que
 * a tela de bloqueio tenha um preço para mostrar mesmo sem rede (último valor visto do
 * backend), evitando uma chamada de rede síncrona no caminho crítico do bloqueio.
 */
@Singleton
class PricingPreferences @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val dataStore = context.pricingDataStore

    suspend fun getCachedPrice(key: String): String? =
        dataStore.data.first()[stringPreferencesKey(key)]

    suspend fun setCachedPrice(key: String, value: String) {
        dataStore.edit { it[stringPreferencesKey(key)] = value }
    }
}
