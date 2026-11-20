package com.dollarblock.data.local.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private val Context.celebrationDataStore by preferencesDataStore(name = "dollarblock_celebration")

/**
 * Quando foi o último parabéns por recusar a fatura (E27) e qual frase saiu, para limitar a
 * frequência ([com.dollarblock.feature.celebration.CelebrationPolicy]) e não repetir o texto.
 */
@Singleton
class CelebrationPreferences @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val dataStore = context.celebrationDataStore
    private val lastShownAtKey = longPreferencesKey("last_shown_at")
    private val lastVariantKey = intPreferencesKey("last_variant")

    suspend fun lastShownAt(): Long? = dataStore.data.first()[lastShownAtKey]

    suspend fun lastVariant(): Int? = dataStore.data.first()[lastVariantKey]

    suspend fun markShown(atMillis: Long, variant: Int) {
        dataStore.edit {
            it[lastShownAtKey] = atMillis
            it[lastVariantKey] = variant
        }
    }
}
