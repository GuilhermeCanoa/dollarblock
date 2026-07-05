package com.dollarblock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dollarblock.data.local.prefs.AppTheme
import com.dollarblock.data.local.prefs.PermissionNagPreferences
import com.dollarblock.data.local.prefs.ThemePreferences
import com.dollarblock.data.permissions.AppPermission
import com.dollarblock.data.permissions.PermissionsProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    themePreferences: ThemePreferences,
    private val permissionsProvider: PermissionsProvider,
    private val permissionNagPreferences: PermissionNagPreferences,
) : ViewModel() {
    val theme: StateFlow<AppTheme?> = themePreferences.theme
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _missingPermissionsNag = MutableStateFlow<List<AppPermission>?>(null)

    /**
     * Lista de permissões faltando quando o aviso diário de "taxímetro às cegas" deve
     * aparecer; null quando não há nada a avisar (tudo concedido ou já avisado hoje).
     */
    val missingPermissionsNag: StateFlow<List<AppPermission>?> = _missingPermissionsNag.asStateFlow()

    /** Checa permissões e decide se o aviso do dia deve subir. Chamar ao abrir as abas. */
    fun checkPermissionNag() {
        viewModelScope.launch {
            val state = permissionsProvider.currentState()
            val missing = AppPermission.entries.filterNot(state::isGranted)
            if (missing.isEmpty()) {
                _missingPermissionsNag.value = null
                return@launch
            }
            val today = LocalDate.now(ZoneId.systemDefault()).toEpochDay()
            if (permissionNagPreferences.lastNagEpochDay() != today) {
                _missingPermissionsNag.value = missing
            }
        }
    }

    /** Fecha o aviso e carimba o dia — só volta a aparecer no próximo dia de navegação. */
    fun dismissPermissionNag() {
        _missingPermissionsNag.value = null
        viewModelScope.launch {
            permissionNagPreferences.setNaggedOn(LocalDate.now(ZoneId.systemDefault()).toEpochDay())
        }
    }
}
