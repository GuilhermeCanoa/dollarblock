package com.dollarblock.data.local.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.notificationDataStore by preferencesDataStore(name = "dollarblock_notifications")

/** Liga/desliga o aviso de "faltam N min de limite" (ver [com.dollarblock.service.accessibility.LimitWarningNotifier]). */
@Singleton
class NotificationPreferences @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val dataStore = context.notificationDataStore
    private val enabledKey = booleanPreferencesKey("limit_warnings_enabled")

    val enabled: Flow<Boolean> = dataStore.data.map { it[enabledKey] ?: true }

    suspend fun isEnabled(): Boolean = enabled.first()

    suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { it[enabledKey] = enabled }
    }

    suspend fun reset() {
        dataStore.edit { it.clear() }
    }
}
