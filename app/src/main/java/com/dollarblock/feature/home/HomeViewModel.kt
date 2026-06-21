package com.dollarblock.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dollarblock.data.apps.InstalledApp
import com.dollarblock.data.apps.InstalledAppsProvider
import com.dollarblock.data.local.prefs.BlockPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val installedApps: List<InstalledApp> = emptyList(),
    val blockedPackages: Set<String> = emptySet(),
    val selectedPackage: String? = null,
    val loadingApps: Boolean = true,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val installedAppsProvider: InstalledAppsProvider,
    private val blockPreferences: BlockPreferences,
) : ViewModel() {

    private val installedApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    private val loading = MutableStateFlow(true)
    private val selected = MutableStateFlow<String?>(null)

    val uiState: StateFlow<HomeUiState> = combine(
        installedApps,
        blockPreferences.blockedPackages,
        selected,
        loading,
    ) { apps, blocked, selectedPkg, isLoading ->
        HomeUiState(
            installedApps = apps,
            blockedPackages = blocked,
            selectedPackage = selectedPkg,
            loadingApps = isLoading,
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
    }

    fun selectApp(packageName: String) {
        selected.update { packageName }
    }

    fun setBlocked(packageName: String, blocked: Boolean) {
        viewModelScope.launch {
            blockPreferences.setBlocked(packageName, blocked)
        }
    }
}
