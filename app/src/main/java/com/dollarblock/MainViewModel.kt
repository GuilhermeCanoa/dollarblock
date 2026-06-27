package com.dollarblock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dollarblock.data.local.prefs.AppTheme
import com.dollarblock.data.local.prefs.ThemePreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    themePreferences: ThemePreferences,
) : ViewModel() {
    val theme: StateFlow<AppTheme?> = themePreferences.theme
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
