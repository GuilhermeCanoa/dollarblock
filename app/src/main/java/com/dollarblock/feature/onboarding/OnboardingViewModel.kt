package com.dollarblock.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dollarblock.data.local.prefs.OnboardingPreferences
import com.dollarblock.data.permissions.AppPermission
import com.dollarblock.data.permissions.PermissionsProvider
import com.dollarblock.data.permissions.PermissionsState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val permissions: PermissionsProvider,
    private val onboardingPreferences: OnboardingPreferences,
) : ViewModel() {

    /**
     * `null` enquanto a flag ainda não foi lida do DataStore (evita piscar o
     * onboarding para quem já o concluiu). Depois resolve para `true`/`false`.
     */
    val onboardingCompleted: StateFlow<Boolean?> = onboardingPreferences.completed
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _permissionsState = MutableStateFlow(permissions.currentState())
    val permissionsState: StateFlow<PermissionsState> = _permissionsState

    /** Re-checa permissões ao voltar de uma tela de Configurações do sistema. */
    fun recheckPermissions() {
        _permissionsState.value = permissions.currentState()
    }

    fun intentFor(permission: AppPermission) = when (permission) {
        AppPermission.USAGE_ACCESS -> permissions.usageAccessIntent()
        AppPermission.ACCESSIBILITY -> permissions.accessibilityIntent()
        AppPermission.OVERLAY -> permissions.overlayIntent()
        AppPermission.NOTIFICATIONS -> null // tratada via runtime permission launcher
    }

    fun completeOnboarding() {
        viewModelScope.launch { onboardingPreferences.setCompleted() }
    }
}
