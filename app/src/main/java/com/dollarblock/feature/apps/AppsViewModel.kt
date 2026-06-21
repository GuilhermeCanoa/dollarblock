package com.dollarblock.feature.apps

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dollarblock.data.apps.InstalledApp
import com.dollarblock.data.apps.InstalledAppsProvider
import com.dollarblock.domain.repository.MonitoredAppRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Combina o app instalado (label/ícone) com seu estado de monitoramento/uso em Room. */
data class AppUsageRow(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap?,
    val isMonitored: Boolean,
    val dailyLimitMinutes: Int?,
    val usedMinutesToday: Int,
)

data class AppsUiState(
    val rows: List<AppUsageRow> = emptyList(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class AppsViewModel @Inject constructor(
    private val installedAppsProvider: InstalledAppsProvider,
    private val monitoredAppRepository: MonitoredAppRepository,
) : ViewModel() {

    private val installedApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    private val loadingInstalled = MutableStateFlow(true)

    val uiState: StateFlow<AppsUiState> = combine(
        installedApps,
        monitoredAppRepository.observeMonitoredAppsUsage(),
        loadingInstalled,
    ) { installed, monitoredUsage, isLoadingInstalled ->
        val usageByPackage = monitoredUsage.associateBy { it.packageName }
        val rows = installed.map { app ->
            val usage = usageByPackage[app.packageName]
            AppUsageRow(
                packageName = app.packageName,
                label = app.label,
                icon = app.icon,
                isMonitored = usage?.isMonitored ?: false,
                dailyLimitMinutes = usage?.dailyLimitMinutes,
                usedMinutesToday = usage?.usedMinutesToday ?: 0,
            )
        }
        AppsUiState(rows = rows, isLoading = isLoadingInstalled)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppsUiState(),
    )

    init {
        viewModelScope.launch {
            installedApps.value = installedAppsProvider.getLaunchableApps()
            loadingInstalled.value = false
        }
        viewModelScope.launch {
            monitoredAppRepository.syncTodayUsage()
        }
    }

    fun setMonitored(packageName: String, appName: String, monitored: Boolean) {
        viewModelScope.launch {
            monitoredAppRepository.setMonitored(packageName, appName, monitored)
            if (monitored) {
                // garante que o uso de hoje já apareça imediatamente após ativar o toggle
                monitoredAppRepository.syncTodayUsage()
            }
        }
    }
}
