package com.dollarblock.feature.permission

import androidx.lifecycle.ViewModel
import com.dollarblock.data.usage.UsageStatsProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class UsageAccessGateViewModel @Inject constructor(
    private val usageStatsProvider: UsageStatsProvider,
) : ViewModel() {

    private val _hasAccess = MutableStateFlow(usageStatsProvider.hasUsageAccess())
    val hasAccess: StateFlow<Boolean> = _hasAccess

    /** Chamado quando a tela volta a ficar visível (ex: ao retornar de Configurações). */
    fun recheckAccess() {
        _hasAccess.update { usageStatsProvider.hasUsageAccess() }
    }

    fun settingsIntent() = usageStatsProvider.usageAccessSettingsIntent()
}
