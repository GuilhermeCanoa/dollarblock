package com.dollarblock.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dollarblock.data.local.prefs.MoneyPreferences
import com.dollarblock.domain.model.MoneySettings
import com.dollarblock.domain.model.RecentEvent
import com.dollarblock.domain.repository.EventsRepository
import com.dollarblock.domain.repository.MoneySummaryRepository
import com.dollarblock.domain.repository.MonitoredAppRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val recentEvents: List<RecentEvent> = emptyList(),
    val moneyLostToday: Double? = null,
    val currentlyBlockedCount: Int = 0,
    val addictionAttempts: Int = 0,
    val moneySpentTotal: Double? = null,
    val moneySavedTotal: Double? = null,
    val moneySettings: MoneySettings = MoneySettings(),
    val showSalaryTip: Boolean = false,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val monitoredAppRepository: MonitoredAppRepository,
    private val moneyPreferences: MoneyPreferences,
    eventsRepository: EventsRepository,
    moneySummaryRepository: MoneySummaryRepository,
) : ViewModel() {

    private val baseState = combine(
        eventsRepository.recentEvents(RECENT_EVENTS_LIMIT),
        monitoredAppRepository.observeMonitoredAppsUsage(),
        eventsRepository.blockAttemptsToday(),
        moneySummaryRepository.observeTotalSpent(),
        moneySummaryRepository.observeTotalSaved(),
    ) { events, monitoredUsage, blockAttempts, totalSpent, totalSaved ->
        HomeUiState(
            recentEvents = events,
            addictionAttempts = blockAttempts,
            moneySpentTotal = totalSpent,
            moneySavedTotal = totalSaved,
        ) to monitoredUsage
    }

    val uiState: StateFlow<HomeUiState> = combine(
        baseState,
        moneyPreferences.settings,
        moneyPreferences.salaryTipShown,
    ) { (base, monitoredUsage), settings, salaryTipShown ->
        val metrics = HomeMetrics.compute(monitoredUsage, settings.monthlySalary)
        base.copy(
            moneyLostToday = metrics.moneyLostToday,
            currentlyBlockedCount = metrics.currentlyBlockedCount,
            moneySettings = settings,
            showSalaryTip = !settings.salaryConfigured && !salaryTipShown,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
    )

    /** Salva o salário líquido mensal; null volta para a referência padrão. */
    fun setMonthlySalary(value: Double?) {
        viewModelScope.launch { moneyPreferences.setMonthlySalary(value) }
    }

    /** Fecha o balão-tutorial do card de salário; não volta a aparecer depois disso. */
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
        const val RECENT_EVENTS_LIMIT = 8
        const val SYNC_INTERVAL_MS = 5_000L
    }
}
