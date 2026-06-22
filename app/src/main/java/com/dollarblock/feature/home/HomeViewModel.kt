package com.dollarblock.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dollarblock.data.apps.InstalledApp
import com.dollarblock.data.apps.InstalledAppsProvider
import com.dollarblock.data.local.prefs.BlockPreferences
import com.dollarblock.domain.model.RecentEvent
import com.dollarblock.domain.repository.EventsRepository
import com.dollarblock.domain.repository.MonitoredAppRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

data class HomeUiState(
    val installedApps: List<InstalledApp> = emptyList(),
    val blockedPackages: Set<String> = emptySet(),
    val selectedPackage: String? = null,
    val loadingApps: Boolean = true,
    val recentEvents: List<RecentEvent> = emptyList(),
    /** Número de 0 a 100, ou null se nenhum app monitorado tem limite definido hoje. */
    val dailyScore: Int? = null,
    /** Minutos economizados hoje (soma de limite − usado, só quando positivo). */
    val timeSavedMinutes: Int = 0,
    /** Quantidade de apps monitorados com limite diário definido. */
    val activeLimitsCount: Int = 0,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val installedAppsProvider: InstalledAppsProvider,
    private val blockPreferences: BlockPreferences,
    private val monitoredAppRepository: MonitoredAppRepository,
    eventsRepository: EventsRepository,
) : ViewModel() {

    private val installedApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    private val loading = MutableStateFlow(true)
    private val selected = MutableStateFlow<String?>(null)

    /**
     * `combine` com lambda tipada só existe nativamente até 5 flows; agrupamos
     * `installedApps`+`loading` (ambos sobre o mesmo carregamento) em um único flow
     * auxiliar primeiro, para caber nesse limite sem recorrer a casts de `Array<*>`.
     */
    private val installedAppsState: Flow<Pair<List<InstalledApp>, Boolean>> =
        combine(installedApps, loading) { apps, isLoading -> apps to isLoading }

    val uiState: StateFlow<HomeUiState> = combine(
        installedAppsState,
        blockPreferences.blockedPackages,
        selected,
        eventsRepository.recentEvents(RECENT_EVENTS_LIMIT),
        monitoredAppRepository.observeMonitoredAppsUsage(),
    ) { (apps, isLoading), blocked, selectedPkg, events, monitoredUsage ->
        // Apenas apps monitorados COM limite diário definido contam para as métricas do
        // dia — sem uma meta para comparar, não há "economia" nem "score" para calcular.
        val withLimit = monitoredUsage.filter { it.isMonitored && it.dailyLimitMinutes != null }

        val timeSaved = withLimit.sumOf { app ->
            val limit = app.dailyLimitMinutes ?: 0
            (limit - app.usedMinutesToday).coerceAtLeast(0)
        }

        val dailyScore = if (withLimit.isEmpty()) {
            null
        } else {
            val perAppRatios = withLimit.map { app ->
                val limit = app.dailyLimitMinutes ?: 1
                ((limit - app.usedMinutesToday).toFloat() / limit).coerceIn(0f, 1f)
            }
            (perAppRatios.average() * 100).roundToInt()
        }

        HomeUiState(
            installedApps = apps,
            blockedPackages = blocked,
            selectedPackage = selectedPkg,
            loadingApps = isLoading,
            recentEvents = events,
            dailyScore = dailyScore,
            timeSavedMinutes = timeSaved,
            activeLimitsCount = withLimit.size,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
    )

    init {
        viewModelScope.launch {
            installedApps.value = installedAppsProvider.getLaunchableApps()
            loading.value = false
        }
        viewModelScope.launch {
            while (isActive) {
                monitoredAppRepository.syncTodayUsage()
                delay(SYNC_INTERVAL_MS)
            }
        }
    }

    fun selectApp(packageName: String) {
        selected.update { packageName }
    }

    fun setBlocked(packageName: String, blocked: Boolean) {
        blockPreferences.setBlocked(packageName, blocked)
    }

    private companion object {
        const val RECENT_EVENTS_LIMIT = 8
        const val SYNC_INTERVAL_MS = 5_000L
    }
}
