package com.dollarblock.feature.profile

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dollarblock.data.local.db.DollarBlockDatabase
import com.dollarblock.data.local.db.dao.EventDao
import com.dollarblock.data.local.prefs.AppTheme
import com.dollarblock.data.local.prefs.BlockPreferences
import com.dollarblock.data.local.prefs.OnboardingPreferences
import com.dollarblock.data.local.prefs.ThemePreferences
import com.dollarblock.data.permissions.AppPermission
import com.dollarblock.data.permissions.PermissionsProvider
import com.dollarblock.data.permissions.PermissionsState
import com.dollarblock.domain.repository.MonitoredAppRepository
import com.dollarblock.feature.home.HomeMetrics
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/** Estatísticas reais exibidas no cabeçalho do Profile. */
data class ProfileStats(
    val activeLimitsCount: Int = 0,
    val moneyLostToday: Double? = null,
    val blocksToday: Int = 0,
)

/**
 * Estado e ações da aba Profile (E8).
 *
 * - **Permissões reais**: lê o estado via [PermissionsProvider], re-checado a cada
 *   [refresh] (chamado em `ON_RESUME`, igual ao onboarding).
 * - **Estatísticas reais**: limites ativos e tempo economizado hoje reaproveitam
 *   [HomeMetrics] sobre os apps monitorados; bloqueios de hoje vêm do [EventDao].
 */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val permissionsProvider: PermissionsProvider,
    private val database: DollarBlockDatabase,
    private val onboardingPreferences: OnboardingPreferences,
    private val blockPreferences: BlockPreferences,
    private val themePreferences: ThemePreferences,
    monitoredAppRepository: MonitoredAppRepository,
    eventDao: EventDao,
) : ViewModel() {

    private val _permissions = MutableStateFlow(permissionsProvider.currentState())
    val permissions: StateFlow<PermissionsState> = _permissions.asStateFlow()

    val theme: StateFlow<AppTheme> = themePreferences.theme
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppTheme.DARK)

    fun setTheme(theme: AppTheme) {
        viewModelScope.launch { themePreferences.setTheme(theme) }
    }

    val stats: StateFlow<ProfileStats> = run {
        val zone = ZoneId.systemDefault()
        val startOfDay = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
        val endOfDay = startOfDay + DAY_MS - 1

        combine(
            monitoredAppRepository.observeMonitoredAppsUsage(),
            eventDao.countBlocksInRange(startOfDay, endOfDay),
        ) { monitoredUsage, blocksToday ->
            // Mesma lógica pura da Home — limites ativos e tempo economizado de hoje.
            val metrics = HomeMetrics.compute(monitoredUsage)
            ProfileStats(
                activeLimitsCount = metrics.currentlyBlockedCount,
                moneyLostToday = metrics.moneyLostToday,
                blocksToday = blocksToday,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ProfileStats(),
        )
    }

    /** Re-lê o estado das permissões. Barato — seguro chamar em cada ON_RESUME. */
    fun refresh() {
        _permissions.value = permissionsProvider.currentState()
    }

    /**
     * Apaga todos os dados locais e reseta o onboarding. Só disponível em builds debug
     * (a UI condiciona a exibição via BuildConfig.DEBUG).
     */
    fun resetAllData() {
        viewModelScope.launch(Dispatchers.IO) {
            database.clearAllTables()
            onboardingPreferences.reset()
            blockPreferences.reset()
        }
    }

    /**
     * Intent da tela do sistema para conceder [permission], ou `null` para NOTIFICATIONS
     * (runtime permission — a tela trata via launcher, igual ao onboarding).
     */
    fun intentFor(permission: AppPermission): Intent? = when (permission) {
        AppPermission.USAGE_ACCESS -> permissionsProvider.usageAccessIntent()
        AppPermission.ACCESSIBILITY -> permissionsProvider.accessibilityIntent()
        AppPermission.OVERLAY -> permissionsProvider.overlayIntent()
        AppPermission.NOTIFICATIONS -> null
    }

    private companion object {
        const val DAY_MS = 24L * 60 * 60 * 1000
    }
}
