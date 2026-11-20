package com.dollarblock.feature.celebration

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dollarblock.data.local.prefs.CelebrationPreferences
import com.dollarblock.data.local.prefs.MoneyPreferences
import com.dollarblock.domain.model.AppCurrency
import com.dollarblock.domain.repository.MoneySummaryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import kotlin.random.Random

/** O parabéns pronto para a tela. [saved] é null quando a carteira não respondeu a tempo. */
data class CelebrationUiState(
    val appLabel: String,
    val variant: Int,
    val saved: Double?,
    val refusedThisMonth: Int,
    val currency: AppCurrency,
)

@HiltViewModel
class CelebrationViewModel @Inject constructor(
    private val preferences: CelebrationPreferences,
    private val moneySummaryRepository: MoneySummaryRepository,
    private val moneyPreferences: MoneyPreferences,
) : ViewModel() {

    private val _state = MutableStateFlow<CelebrationUiState?>(null)
    val state: StateFlow<CelebrationUiState?> = _state.asStateFlow()

    /** Já preparado nesta instância (sobrevive à rotação): não sorteia nem grava de novo. */
    private var preparedFor: String? = null

    /**
     * Prepara o parabéns de [appLabel]: sorteia a frase (sem repetir a anterior), grava o
     * instante — é daqui que conta a janela de [CelebrationPolicy.INTERVAL_MS] — e lê a
     * carteira do mês.
     */
    fun prepare(appLabel: String, variantCount: Int) {
        if (preparedFor == appLabel) return
        preparedFor = appLabel
        viewModelScope.launch {
            val variant = CelebrationPolicy.pickVariant(variantCount, preferences.lastVariant(), Random.Default)
            preferences.markShown(System.currentTimeMillis(), variant)
            val wallet = withTimeoutOrNull(WALLET_TIMEOUT_MS) { moneySummaryRepository.observeMonthWallet().first() }
            _state.value = CelebrationUiState(
                appLabel = appLabel,
                variant = variant,
                saved = wallet?.saved,
                refusedThisMonth = wallet?.passesRefused ?: 0,
                currency = moneyPreferences.settings.first().currency,
            )
        }
    }

    fun dismiss() {
        _state.value = null
    }

    private companion object {
        const val WALLET_TIMEOUT_MS = 3_000L
    }
}
