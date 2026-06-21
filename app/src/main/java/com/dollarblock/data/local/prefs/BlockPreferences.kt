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
 * Fonte única do estado de bloqueio. Mantém o estado em memória ([StateFlow]) para
 * leitura síncrona pelo [com.dollarblock.service.accessibility.DollarBlockAccessibilityService]
 * (evita corrida entre conceder o desbloqueio e reabrir o app), e persiste em DataStore.
 */
@Singleton
class BlockPreferences @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val dataStore = context.dollarBlockDataStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val blockedKey = stringSetPreferencesKey("blocked_packages")
    private val unlockedKey = stringSetPreferencesKey("unlocked_until")

    private val _blockedPackages = MutableStateFlow<Set<String>>(emptySet())
    val blockedPackages: StateFlow<Set<String>> = _blockedPackages.asStateFlow()

    /** package -> instante (epoch millis) até o qual o app está liberado. */
    private val _unlockedUntil = MutableStateFlow<Map<String, Long>>(emptyMap())
    val unlockedUntil: StateFlow<Map<String, Long>> = _unlockedUntil.asStateFlow()

    init {
        scope.launch {
            val prefs = dataStore.data.first()
            _blockedPackages.value = prefs[blockedKey] ?: emptySet()
            _unlockedUntil.value = parseUnlocks(prefs[unlockedKey] ?: emptySet())
        }
    }

    fun setBlocked(packageName: String, blocked: Boolean) {
        _blockedPackages.update { if (blocked) it + packageName else it - packageName }
        persist()
    }

    /** Concede acesso temporário a [packageName] por [durationMs] (após pagamento). */
    fun grantUnlock(packageName: String, durationMs: Long) {
        val until = System.currentTimeMillis() + durationMs
        _unlockedUntil.update { it + (packageName to until) }
        persist()
    }

    /** True se o app está bloqueado e fora de uma janela de desbloqueio ativa. */
    fun shouldBlock(packageName: String): Boolean {
        if (!_blockedPackages.value.contains(packageName)) return false
        val until = _unlockedUntil.value[packageName] ?: 0L
        return System.currentTimeMillis() >= until
    }

    private fun persist() {
        val blocked = _blockedPackages.value
        val unlocks = _unlockedUntil.value
        scope.launch {
            dataStore.edit { prefs ->
                prefs[blockedKey] = blocked
                prefs[unlockedKey] = unlocks.map { (pkg, until) -> "$pkg|$until" }.toSet()
            }
        }
    }

    private fun parseUnlocks(raw: Set<String>): Map<String, Long> =
        raw.mapNotNull { entry ->
            val idx = entry.lastIndexOf('|')
            if (idx <= 0) return@mapNotNull null
            val pkg = entry.substring(0, idx)
            val until = entry.substring(idx + 1).toLongOrNull() ?: return@mapNotNull null
            pkg to until
        }.toMap()
}
