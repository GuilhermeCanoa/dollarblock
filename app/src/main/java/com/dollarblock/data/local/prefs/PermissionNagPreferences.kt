package com.dollarblock.data.local.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private val Context.permissionNagDataStore by preferencesDataStore(name = "dollarblock_permission_nag")

/**
 * Controla a frequência do aviso de "app degradado sem permissões": no máximo uma
 * exibição por dia de navegação (epochDay local). Sem isso o modal viraria spam a cada
 * troca de aba. Exceção: se uma permissão que estava concedida foi revogada (ex.: usuário
 * desativou a Acessibilidade nas Configurações), o aviso furA o limite diário — o usuário
 * precisa saber na hora, não no dia seguinte.
 */
@Singleton
class PermissionNagPreferences @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val dataStore = context.permissionNagDataStore
    private val lastNagDayKey = longPreferencesKey("last_nag_epoch_day")
    private val lastMissingKey = stringSetPreferencesKey("last_missing_permissions")

    suspend fun lastNagEpochDay(): Long? = dataStore.data.first()[lastNagDayKey]

    suspend fun lastMissingPermissions(): Set<String> = dataStore.data.first()[lastMissingKey].orEmpty()

    suspend fun setNaggedOn(epochDay: Long, missing: Set<String>) {
        dataStore.edit {
            it[lastNagDayKey] = epochDay
            it[lastMissingKey] = missing
        }
    }
}
