package com.dollarblock.data.local.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
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

    /**
     * Se o aviso de 5 min já saiu para [packageName] no dia [epochDay] com este limite.
     * Persistido (e não em memória no serviço) para virar à meia-noite e sobreviver a
     * restarts do serviço de acessibilidade. Trocar o limite libera um novo aviso.
     */
    suspend fun wasWarned(packageName: String, epochDay: Long, limitMinutes: Int): Boolean =
        dataStore.data.first()[warnedKey(packageName)] == warnedMarker(epochDay, limitMinutes)

    suspend fun markWarned(packageName: String, epochDay: Long, limitMinutes: Int) {
        dataStore.edit { it[warnedKey(packageName)] = warnedMarker(epochDay, limitMinutes) }
    }

    /** Quantos avisos de limite já saíram no dia [epochDay], somando todos os apps. */
    suspend fun warningsSentOn(epochDay: Long): Int {
        val prefs = dataStore.data.first()
        return if (prefs[sentDayKey] == epochDay) prefs[sentCountKey] ?: 0 else 0
    }

    suspend fun recordWarningSent(epochDay: Long) {
        dataStore.edit {
            val count = if (it[sentDayKey] == epochDay) it[sentCountKey] ?: 0 else 0
            it[sentDayKey] = epochDay
            it[sentCountKey] = count + 1
        }
    }

    private val sentDayKey = longPreferencesKey("limit_warnings_sent_day")
    private val sentCountKey = intPreferencesKey("limit_warnings_sent_count")

    private fun warnedKey(packageName: String) = stringPreferencesKey("limit_warned_$packageName")

    private fun warnedMarker(epochDay: Long, limitMinutes: Int) = "$epochDay:$limitMinutes"

    suspend fun reset() {
        dataStore.edit { it.clear() }
    }
}
