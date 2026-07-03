package com.dollarblock.feature.apps

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dollarblock.data.apps.InstalledApp
import com.dollarblock.data.apps.InstalledAppsProvider
import com.dollarblock.data.local.prefs.BlockPreferences
import com.dollarblock.domain.model.MonitoredAppUsage
import com.dollarblock.domain.repository.MonitoredAppRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Combina o app instalado (label/ícone) com seu estado de monitoramento/uso em Room. */
data class AppUsageRow(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap?,
    val isMonitored: Boolean,
    val isTracked: Boolean,
    val dailyLimitMinutes: Int?,
    val usedMinutesToday: Int,
    val unlockedToday: Boolean,
)

data class AppsUiState(
    val monitoredRows: List<AppUsageRow> = emptyList(),
    val deactivatedRows: List<AppUsageRow> = emptyList(),
    val suggestedRows: List<AppUsageRow> = emptyList(),
    val searchSuggestions: List<AppUsageRow> = emptyList(),
    val isLoading: Boolean = true,
    val editingLimitFor: AppUsageRow? = null,
    val searchQuery: String = "",
)

/** Aviso exibido após o usuário trocar um limite já existente (aumento ou redução). */
data class LimitChangeNotice(
    val increased: Boolean,
    val previousMinutes: Int,
    val newMinutes: Int,
)

/**
 * Classifica a troca de limite: só há aviso quando havia limite antes e o novo é diferente.
 * Definir o primeiro limite ou remover o limite não gera aviso.
 */
fun classifyLimitChange(previousMinutes: Int?, newMinutes: Int?): LimitChangeNotice? {
    if (previousMinutes == null || newMinutes == null || previousMinutes == newMinutes) return null
    return LimitChangeNotice(
        increased = newMinutes > previousMinutes,
        previousMinutes = previousMinutes,
        newMinutes = newMinutes,
    )
}

@HiltViewModel
class AppsViewModel @Inject constructor(
    private val installedAppsProvider: InstalledAppsProvider,
    private val monitoredAppRepository: MonitoredAppRepository,
    private val blockPreferences: BlockPreferences,
) : ViewModel() {

    private val installedApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    private val loadingInstalled = MutableStateFlow(true)
    private val editingLimitForPackage = MutableStateFlow<String?>(null)
    private val searchQuery = MutableStateFlow("")

    private val _limitChangeNotice = MutableStateFlow<LimitChangeNotice?>(null)

    /** Aviso pós-troca de limite (irônico no aumento, seco no corte); null quando não há nada a dizer. */
    val limitChangeNotice: StateFlow<LimitChangeNotice?> = _limitChangeNotice

    private data class BaseState(
        val installed: List<InstalledApp>,
        val monitoredUsage: List<MonitoredAppUsage>,
        val isLoadingInstalled: Boolean,
        val editingPackage: String?,
        val query: String,
    )

    private val baseState = combine(
        installedApps,
        monitoredAppRepository.observeMonitoredAppsUsage(),
        loadingInstalled,
        editingLimitForPackage,
        searchQuery,
    ) { installed, monitoredUsage, isLoadingInstalled, editingPackage, query ->
        BaseState(installed, monitoredUsage, isLoadingInstalled, editingPackage, query)
    }

    val uiState: StateFlow<AppsUiState> = combine(
        baseState,
        blockPreferences.unlockGrants,
    ) { base, unlockGrants ->
        val (installed, monitoredUsage, isLoadingInstalled, editingPackage, query) = base
        val usageByPackage = monitoredUsage.associateBy { it.packageName }
        val now = System.currentTimeMillis()
        val allRows = installed.map { app ->
            val usage = usageByPackage[app.packageName]
            val grant = unlockGrants[app.packageName]
            AppUsageRow(
                packageName = app.packageName,
                label = app.label,
                icon = app.icon,
                isMonitored = usage?.isMonitored ?: false,
                isTracked = usage != null,
                dailyLimitMinutes = usage?.dailyLimitMinutes,
                usedMinutesToday = usage?.usedMinutesToday ?: 0,
                unlockedToday = grant != null && now < grant.unlockUntilMs,
            )
        }
        val monitoredRows = allRows.filter { it.isMonitored }
        val deactivatedRows = allRows.filter { it.isTracked && !it.isMonitored }
        val trackedPackages = allRows.filter { it.isTracked }.map { it.packageName }.toSet()
        val suggestions = if (query.isBlank()) emptyList() else {
            allRows.filter { !trackedPackages.contains(it.packageName) && it.label.contains(query, ignoreCase = true) }
        }
        // Sugeridos: só os efetivamente instalados (allRows vem do PackageManager) e ainda
        // não rastreados — se nenhum estiver instalado, a seção simplesmente não aparece.
        val suggestedRows = SUGGESTED_PACKAGES.mapNotNull { pkg ->
            allRows.find { it.packageName == pkg && !it.isTracked }
        }
        AppsUiState(
            monitoredRows = monitoredRows,
            deactivatedRows = deactivatedRows,
            suggestedRows = suggestedRows,
            searchSuggestions = suggestions,
            isLoading = isLoadingInstalled,
            editingLimitFor = allRows.find { it.packageName == editingPackage },
            searchQuery = query,
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
            while (isActive) {
                monitoredAppRepository.syncTodayUsage()
                delay(SYNC_INTERVAL_MS)
            }
        }
    }

    fun addMonitoredFromSearch(packageName: String, appName: String) {
        searchQuery.value = ""
        viewModelScope.launch {
            monitoredAppRepository.setMonitored(packageName, appName, true)
            monitoredAppRepository.syncTodayUsage()
        }
    }

    fun setMonitored(packageName: String, appName: String, monitored: Boolean) {
        viewModelScope.launch {
            monitoredAppRepository.setMonitored(packageName, appName, monitored)
            if (monitored) {
                monitoredAppRepository.syncTodayUsage()
            }
        }
    }

    /** Remove permanentemente um app desativado da lista de "Desativados". */
    fun deleteDeactivatedApp(packageName: String) {
        viewModelScope.launch {
            monitoredAppRepository.deleteMonitoredApp(packageName)
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
        val previousMinutes = uiState.value.editingLimitFor
            ?.takeIf { it.packageName == packageName }
            ?.dailyLimitMinutes
        _limitChangeNotice.value = classifyLimitChange(previousMinutes, minutes)
        viewModelScope.launch {
            monitoredAppRepository.setDailyLimit(packageName, minutes)
        }
        editingLimitForPackage.value = null
    }

    /** Fecha o aviso de troca de limite. */
    fun dismissLimitChangeNotice() {
        _limitChangeNotice.value = null
    }

    private companion object {
        const val SYNC_INTERVAL_MS = 5_000L

        /** Ralos de tempo clássicos, sugeridos por padrão quando instalados e fora do taxímetro. */
        val SUGGESTED_PACKAGES = listOf(
            "com.instagram.android",
            "com.facebook.katana",
            "com.zhiliaoapp.musically", // TikTok
            "com.google.android.youtube",
        )
    }
}
