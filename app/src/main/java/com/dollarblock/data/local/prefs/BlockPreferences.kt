package com.dollarblock.data.local.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dollarBlockDataStore by preferencesDataStore(name = "dollarblock_prefs")

/**
 * Persiste o conjunto de pacotes bloqueados em DataStore. Compartilhado entre a
 * UI (Home) e o [com.dollarblock.service.accessibility.DollarBlockAccessibilityService].
 */
@Singleton
class BlockPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val blockedKey = stringSetPreferencesKey("blocked_packages")

    val blockedPackages: Flow<Set<String>> =
        context.dollarBlockDataStore.data.map { prefs -> prefs[blockedKey] ?: emptySet() }

    suspend fun setBlocked(packageName: String, blocked: Boolean) {
        context.dollarBlockDataStore.edit { prefs ->
            val current = prefs[blockedKey]?.toMutableSet() ?: mutableSetOf()
            if (blocked) current.add(packageName) else current.remove(packageName)
            prefs[blockedKey] = current
        }
    }
}
