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
    val editingLimitFor: AppUsageRow? = null,
    val searchQuery: String = "",
    val totalMonitoredCount: Int = 0,
)

@HiltViewModel
class AppsViewModel @Inject constructor(
    private val installedAppsProvider: InstalledAppsProvider,
    private val monitoredAppRepository: MonitoredAppRepository,
) : ViewModel() {

    private val installedApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    private val loadingInstalled = MutableStateFlow(true)
    private val editingLimitForPackage = MutableStateFlow<String?>(null)
    private val searchQuery = MutableStateFlow("")

    val uiState: StateFlow<AppsUiState> = combine(
        installedApps,
        monitoredAppRepository.observeMonitoredAppsUsage(),
        loadingInstalled,
        editingLimitForPackage,
        searchQuery,
    ) { installed, monitoredUsage, isLoadingInstalled, editingPackage, query ->
        val usageByPackage = monitoredUsage.associateBy { it.packageName }
        val allRows = installed.map { app ->
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
        val filteredRows = if (query.isBlank()) {
            allRows
        } else {
            allRows.filter { it.label.contains(query, ignoreCase = true) }
        }
        AppsUiState(
            rows = filteredRows,
            isLoading = isLoadingInstalled,
            editingLimitFor = allRows.find { it.packageName == editingPackage },
            searchQuery = query,
            totalMonitoredCount = allRows.count { it.isMonitored },
        )
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

    /** Atualiza o termo de busca usado para filtrar a lista de apps por nome. */
    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    /** Abre o diálogo de definição de limite diário para o app informado. */
    fun openLimitEditor(packageName: String) {
        editingLimitForPackage.value = packageName
    }

    /** Fecha o diálogo sem salvar alterações. */
    fun dismissLimitEditor() {
        editingLimitForPackage.value = null
    }

    /** Salva o novo limite diário (em minutos) e fecha o diálogo. Use null para remover o limite. */
    fun confirmDailyLimit(packageName: String, minutes: Int?) {
        viewModelScope.launch {
            monitoredAppRepository.setDailyLimit(packageName, minutes)
        }
        editingLimitForPackage.value = null
    }
}
