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

private val Context.nudgeDataStore by preferencesDataStore(name = "dollarblock_nudges")

/**
 * Estado das "cobranças do gerente" (ver `service/nudge`): liga/desliga, contagem do dia
 * para o teto diário, última cobrança enviada (para não repetir) e a data da última vez
 * de cada gatilho (para "uma vez por dia" e os intervalos entre repetições).
 */
@Singleton
class NudgePreferences @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val dataStore = context.nudgeDataStore
    private val enabledKey = booleanPreferencesKey("nudges_enabled")
    private val sentDayKey = longPreferencesKey("sent_day")
    private val sentCountKey = intPreferencesKey("sent_count")
    private val lastNudgeKey = stringPreferencesKey("last_nudge")
    private val sessionThresholdKey = intPreferencesKey("debug_session_threshold_min")

    val enabled: Flow<Boolean> = dataStore.data.map { it[enabledKey] ?: true }

    suspend fun isEnabled(): Boolean = enabled.first()

    suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { it[enabledKey] = enabled }
    }

    /** Cobranças enviadas em [epochDay] (zera sozinho quando o dia vira). */
    suspend fun sentOn(epochDay: Long): Int {
        val prefs = dataStore.data.first()
        return if (prefs[sentDayKey] == epochDay) prefs[sentCountKey] ?: 0 else 0
    }

    /** Registra uma cobrança enviada: soma no contador do dia (se [countsTowardCap]) e guarda qual foi. */
    suspend fun recordSent(epochDay: Long, nudgeName: String, countsTowardCap: Boolean) {
        dataStore.edit { prefs ->
            if (countsTowardCap) {
                val current = if (prefs[sentDayKey] == epochDay) prefs[sentCountKey] ?: 0 else 0
                prefs[sentDayKey] = epochDay
                prefs[sentCountKey] = current + 1
            }
            prefs[lastNudgeKey] = nudgeName
        }
    }

    suspend fun lastNudgeName(): String? = dataStore.data.first()[lastNudgeKey]

    /** Dia (epochDay) em que o gatilho [trigger] disparou pela última vez, ou null. */
    suspend fun lastFired(trigger: String): Long? = dataStore.data.first()[triggerKey(trigger)]

    suspend fun markFired(trigger: String, epochDay: Long) {
        dataStore.edit { it[triggerKey(trigger)] = epochDay }
    }

    /** Só debug: encurta a "sessão longa" para testar sem esperar 30 min. Null = padrão. */
    suspend fun debugSessionThresholdMinutes(): Int? = dataStore.data.first()[sessionThresholdKey]

    suspend fun setDebugSessionThresholdMinutes(minutes: Int?) {
        dataStore.edit { if (minutes == null) it.remove(sessionThresholdKey) else it[sessionThresholdKey] = minutes }
    }

    private fun triggerKey(trigger: String) = longPreferencesKey("fired_$trigger")

    suspend fun reset() {
        dataStore.edit { it.clear() }
    }
}
