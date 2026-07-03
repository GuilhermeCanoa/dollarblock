package com.dollarblock.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val monitoredAppRepository: MonitoredAppRepository,
    eventsRepository: EventsRepository,
    moneySummaryRepository: MoneySummaryRepository,
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = combine(
        eventsRepository.recentEvents(RECENT_EVENTS_LIMIT),
        monitoredAppRepository.observeMonitoredAppsUsage(),
        eventsRepository.blockAttemptsToday(),
        moneySummaryRepository.observeTotalSpent(),
        moneySummaryRepository.observeTotalSaved(),
    ) { events, monitoredUsage, blockAttempts, totalSpent, totalSaved ->
        val metrics = HomeMetrics.compute(monitoredUsage)
        HomeUiState(
            recentEvents = events,
            moneyLostToday = metrics.moneyLostToday,
            currentlyBlockedCount = metrics.currentlyBlockedCount,
            addictionAttempts = blockAttempts,
            moneySpentTotal = totalSpent,
            moneySavedTotal = totalSaved,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
    )

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
