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
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dollarBlockDataStore by preferencesDataStore(name = "dollarblock_prefs")

/**
 * Fonte única do estado de bloqueio.
 *
 * Janela de desbloqueio: após pagamento, armazena (grantedAtMs, durationMs).
 * A verificação de expiração por *tempo de uso real* é feita no serviço de acessibilidade
 * via [com.dollarblock.data.usage.UsageStatsProvider.getUsageMillisSince] — não há
 * session tracking aqui. Para bloqueios manuais, usa wall-clock (rápido e síncrono).
 */
@Singleton
class BlockPreferences @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val dataStore = context.dollarBlockDataStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val blockedKey = stringSetPreferencesKey("blocked_packages")
    private val unlockGrantsKey = stringSetPreferencesKey("unlock_grants")

    private val _blockedPackages = MutableStateFlow<Set<String>>(emptySet())
    val blockedPackages: StateFlow<Set<String>> = _blockedPackages.asStateFlow()

    /** package → (grantedAtMs, durationMs) — concedido após pagamento. */
    private val _unlockGrants = MutableStateFlow<Map<String, UnlockGrant>>(emptyMap())

    data class UnlockGrant(val grantedAtMs: Long, val durationMs: Long)

    init {
        scope.launch {
            val prefs = dataStore.data.first()
            _blockedPackages.value = prefs[blockedKey] ?: emptySet()
            _unlockGrants.value = parseGrants(prefs[unlockGrantsKey] ?: emptySet())
        }
    }

    fun setBlocked(packageName: String, blocked: Boolean) {
        _blockedPackages.update { if (blocked) it + packageName else it - packageName }
        persist()
    }

    /** Registra uma janela de desbloqueio de [durationMs] a partir de agora. */
    fun grantUnlock(packageName: String, durationMs: Long) {
        _unlockGrants.update { it + (packageName to UnlockGrant(System.currentTimeMillis(), durationMs)) }
        persist()
    }

    /** Retorna o grant ativo para [packageName], ou null se não houver. */
    fun getUnlockGrant(packageName: String): UnlockGrant? = _unlockGrants.value[packageName]

    /**
     * True se o app está bloqueado manualmente e fora da janela de desbloqueio (wall-clock).
     * Usado no path síncrono do AccessibilityService — sem IO.
     */
    fun shouldBlock(packageName: String): Boolean {
        if (!_blockedPackages.value.contains(packageName)) return false
        val grant = _unlockGrants.value[packageName] ?: return true
        return System.currentTimeMillis() >= grant.grantedAtMs + grant.durationMs
    }

    private fun persist() {
        val blocked = _blockedPackages.value
        val grants = _unlockGrants.value
        scope.launch {
            dataStore.edit { prefs ->
                prefs[blockedKey] = blocked
                prefs[unlockGrantsKey] = grants.map { (pkg, g) ->
                    "$pkg|${g.grantedAtMs}|${g.durationMs}"
                }.toSet()
            }
        }
    }

    private fun parseGrants(raw: Set<String>): Map<String, UnlockGrant> =
        raw.mapNotNull { entry ->
            val parts = entry.split('|')
            if (parts.size != 3) return@mapNotNull null
            val pkg = parts[0]
            val grantedAt = parts[1].toLongOrNull() ?: return@mapNotNull null
            val duration = parts[2].toLongOrNull() ?: return@mapNotNull null
            pkg to UnlockGrant(grantedAt, duration)
        }.toMap()
}
