package com.dollarblock.feature.onboarding

import android.content.Context
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dollarblock.data.apps.InstalledAppsProvider
import com.dollarblock.data.local.prefs.OnboardingPreferences
import com.dollarblock.data.permissions.AppPermission
import com.dollarblock.data.permissions.PermissionsProvider
import com.dollarblock.data.permissions.PermissionsState
import com.dollarblock.data.usage.UsageStatsProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class QuickSummaryEntry(
    val appName: String,
    val icon: ImageBitmap?,
    val millis: Long,
    val percentage: Float,
)

data class QuickSummaryState(
    val topApps: List<QuickSummaryEntry> = emptyList(),
    val totalTime: String = "",
    val isLoading: Boolean = true,
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val permissions: PermissionsProvider,
    private val onboardingPreferences: OnboardingPreferences,
    private val usageStatsProvider: UsageStatsProvider,
    private val installedAppsProvider: InstalledAppsProvider,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val onboardingCompleted: StateFlow<Boolean?> = onboardingPreferences.completed
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _permissionsState = MutableStateFlow(permissions.currentState())
    val permissionsState: StateFlow<PermissionsState> = _permissionsState

    private val _quickSummaryState = MutableStateFlow(QuickSummaryState())
    val quickSummaryState: StateFlow<QuickSummaryState> = _quickSummaryState

    init {
        viewModelScope.launch { loadQuickSummary() }
    }

    fun reloadQuickSummary() {
        if (_quickSummaryState.value.isLoading) return
        _quickSummaryState.value = _quickSummaryState.value.copy(isLoading = true)
        viewModelScope.launch { loadQuickSummary() }
    }

    private suspend fun loadQuickSummary() {
        val usage = usageStatsProvider.getWeeklyUsageByPackage()
            .filter { it.key != context.packageName }
        val totalMillis = usage.values.sum()
        val pm = context.packageManager
        val top5 = usage.entries
            .sortedByDescending { it.value }
            .take(5)
            .map { (pkg, millis) ->
                val appName = runCatching {
                    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                }.getOrElse { pkg }
                val icon = installedAppsProvider.getIconForPackage(pkg)
                QuickSummaryEntry(
                    appName = appName,
                    icon = icon,
                    millis = millis,
                    percentage = if (totalMillis > 0) millis.toFloat() / totalMillis else 0f,
                )
            }
        _quickSummaryState.value = QuickSummaryState(
            topApps = top5,
            totalTime = formatMillis(totalMillis),
            isLoading = false,
        )
    }

    fun recheckPermissions() {
        _permissionsState.value = permissions.currentState()
    }

    fun intentFor(permission: AppPermission) = when (permission) {
        AppPermission.USAGE_ACCESS -> permissions.usageAccessIntent()
        AppPermission.ACCESSIBILITY -> permissions.accessibilityIntent()
        AppPermission.OVERLAY -> permissions.overlayIntent()
        AppPermission.NOTIFICATIONS -> null
    }

    fun completeOnboarding() {
        viewModelScope.launch { onboardingPreferences.setCompleted() }
    }

    private fun formatMillis(millis: Long): String {
        val totalMinutes = millis / 60_000L
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }
}
