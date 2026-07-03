package com.dollarblock.data.local.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dollarBlockDataStore by preferencesDataStore(name = "dollarblock_prefs")

/**
 * Persiste passes do dia pós-pagamento: um pagamento libera o app até a meia-noite local
 * ([UnlockGrant.unlockUntilMs]). A expiração é wall-clock — o serviço de acessibilidade
 * só compara `now < unlockUntilMs`.
 */
@Singleton
class BlockPreferences @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val dataStore = context.dollarBlockDataStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val unlockGrantsKey = stringSetPreferencesKey("unlock_grants")

    /** package → passe do dia concedido após pagamento. */
    private val _unlockGrants = MutableStateFlow<Map<String, UnlockGrant>>(emptyMap())

    /** Observável para a UI (ex.: indicador de "passe do dia ativo" na lista de apps). */
    val unlockGrants: StateFlow<Map<String, UnlockGrant>> = _unlockGrants.asStateFlow()

    data class UnlockGrant(val unlockUntilMs: Long)

    init {
        scope.launch {
            val prefs = dataStore.data.first()
            _unlockGrants.value = parseGrants(prefs[unlockGrantsKey] ?: emptySet())
        }
    }

    /** Registra um passe do dia: o app fica liberado até a meia-noite local. */
    fun grantUnlockForToday(packageName: String) {
        val until = endOfTodayMillis()
        _unlockGrants.update { it + (packageName to UnlockGrant(until)) }
        persist()
    }

    /** Retorna o grant ativo para [packageName], ou null se não houver. */
    fun getUnlockGrant(packageName: String): UnlockGrant? = _unlockGrants.value[packageName]

    private fun persist() {
        val grants = _unlockGrants.value
        scope.launch {
            dataStore.edit { prefs ->
                prefs[unlockGrantsKey] = serializeGrants(grants)
            }
        }
    }

    /** Apaga todos os unlock grants (uso exclusivo do reset de debug). */
    fun reset() {
        _unlockGrants.value = emptyMap()
        scope.launch { dataStore.edit { it.clear() } }
    }

    companion object {
        /** Meia-noite local do fim do dia que contém [nowMs]. */
        fun endOfDayMillis(nowMs: Long, zone: ZoneId): Long =
            Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
                .plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

        private fun endOfTodayMillis(): Long =
            endOfDayMillis(System.currentTimeMillis(), ZoneId.systemDefault())

        /** Formato v2: `"pkg|unlockUntilMs"`. */
        fun serializeGrants(grants: Map<String, UnlockGrant>): Set<String> =
            grants.map { (pkg, g) -> "$pkg|${g.unlockUntilMs}" }.toSet()

        /**
         * Entradas no formato antigo de 3 partes (`pkg|grantedAt|duration`, janela de 5 min)
         * são ignoradas — grants pré-migração simplesmente expiram.
         */
        fun parseGrants(raw: Set<String>): Map<String, UnlockGrant> =
            raw.mapNotNull { entry ->
                val parts = entry.split('|')
                if (parts.size != 2) return@mapNotNull null
                val pkg = parts[0]
                val until = parts[1].toLongOrNull() ?: return@mapNotNull null
                pkg to UnlockGrant(until)
            }.toMap()
    }
}
