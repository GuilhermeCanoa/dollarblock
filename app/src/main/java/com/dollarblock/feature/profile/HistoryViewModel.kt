package com.dollarblock.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dollarblock.domain.model.RecentEvent
import com.dollarblock.domain.repository.EventsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Histórico completo de eventos (bloqueios + desbloqueios), ordenado do mais recente.
 * Reaproveita `EventsRepository.recentEvents` (mesma fonte da Home), só com um limite maior.
 */
@HiltViewModel
class HistoryViewModel @Inject constructor(
    eventsRepository: EventsRepository,
) : ViewModel() {

    val events: StateFlow<List<RecentEvent>> =
        eventsRepository.recentEvents(HISTORY_LIMIT)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList(),
            )

    private companion object {
        const val HISTORY_LIMIT = 200
    }
}
