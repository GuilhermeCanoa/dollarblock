package com.dollarblock.feature.home

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dollarblock.data.apps.InstalledAppsProvider
import com.dollarblock.data.local.prefs.MoneyPreferences
import com.dollarblock.domain.model.MoneySettings
import com.dollarblock.domain.model.MonthWallet
import com.dollarblock.domain.repository.EventsRepository
import com.dollarblock.domain.repository.MoneySummaryRepository
import com.dollarblock.domain.repository.MonitoredAppRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    /** Null enquanto nenhum app é monitorado (estado vazio: leva para Apps). */
    val moneyLostToday: Double? = null,
    val apps: List<HomeAppRow> = emptyList(),
    val budget: DailyBudget? = null,
    val attemptsToday: Int = 0,
    val attemptsTopPackage: String? = null,
    val nextToClose: NextToClose? = null,
    val wallet: MonthWallet? = null,
    val moneySettings: MoneySettings = MoneySettings(),
    val showSalaryTip: Boolean = false,
)

/** Ícone e cor de marca de um app, carregados uma vez por pacote. */
data class AppVisual(val icon: ImageBitmap?, val color: Color?)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val monitoredAppRepository: MonitoredAppRepository,
    private val moneyPreferences: MoneyPreferences,
    private val installedAppsProvider: InstalledAppsProvider,
    eventsRepository: EventsRepository,
    moneySummaryRepository: MoneySummaryRepository,
) : ViewModel() {

    private val activity = combine(
        monitoredAppRepository.observeMonitoredAppsUsage(),
        eventsRepository.blockAttemptsToday(),
        eventsRepository.blockAttemptsTodayByPackage(),
        moneySummaryRepository.observeMonthWallet(),
    ) { usage, attempts, attemptsByPackage, wallet ->
        loadVisuals(usage.filter { it.isMonitored }.map { it.packageName })
        HomeUiState(
            apps = HomeDashboard.appRows(usage),
            budget = HomeDashboard.budget(usage),
            attemptsToday = attempts,
            attemptsTopPackage = HomeDashboard.topAttempts(attemptsByPackage),
            nextToClose = HomeDashboard.nextToClose(usage),
            wallet = wallet,
        ) to usage
    }

    val uiState: StateFlow<HomeUiState> = combine(
        activity,
        moneyPreferences.settings,
        moneyPreferences.salaryTipShown,
    ) { (base, usage), settings, salaryTipShown ->
        base.copy(
            moneyLostToday = HomeMetrics.compute(usage, settings.monthlySalary).moneyLostToday,
            moneySettings = settings,
            showSalaryTip = !settings.salaryConfigured && !salaryTipShown,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
    )

    private val _visuals = MutableStateFlow<Map<String, AppVisual>>(emptyMap())

    /** Ícones e cores dos apps monitorados (pacote → visual), preenchidos sob demanda. */
    val visuals: StateFlow<Map<String, AppVisual>> = _visuals.asStateFlow()

    private fun loadVisuals(packages: List<String>) {
        val missing = packages.filter { it !in _visuals.value }
        if (missing.isEmpty()) return
        viewModelScope.launch {
            missing.forEach { pkg ->
                val icon = installedAppsProvider.getIconForPackage(pkg)
                val color = icon?.let { AppIconColor.dominant(it.asAndroidBitmap()) }?.let { Color(it) }
                _visuals.update { it + (pkg to AppVisual(icon, color)) }
            }
        }
    }

    /** Salva o salário líquido mensal; null volta para a referência padrão. */
    fun setMonthlySalary(value: Double?) {
        viewModelScope.launch { moneyPreferences.setMonthlySalary(value) }
    }

    /** Encerra o destaque de primeira vez do campo de salário; não volta a aparecer. */
    fun dismissSalaryTip() {
        viewModelScope.launch { moneyPreferences.setSalaryTipShown() }
    }

    init {
        viewModelScope.launch {
            while (isActive) {
                monitoredAppRepository.syncTodayUsage()
                delay(SYNC_INTERVAL_MS)
            }
        }
    }

    private companion object {
        const val SYNC_INTERVAL_MS = 5_000L
    }
}
