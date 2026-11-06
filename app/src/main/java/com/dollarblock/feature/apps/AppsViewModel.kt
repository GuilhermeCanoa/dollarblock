package com.dollarblock.feature.apps

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dollarblock.data.apps.InstalledApp
import com.dollarblock.data.apps.InstalledAppsProvider
import com.dollarblock.data.local.prefs.BlockPreferences
import com.dollarblock.data.local.prefs.MoneyPreferences
import com.dollarblock.data.usage.UsageStatsProvider
import com.dollarblock.domain.model.MonitoredAppUsage
import com.dollarblock.domain.model.PaymentMethod
import com.dollarblock.domain.repository.EventsRepository
import com.dollarblock.domain.repository.MonitoredAppRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Combina o app instalado (label/ícone) com seu estado de monitoramento/uso em Room. */
data class AppUsageRow(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap?,
    val isMonitored: Boolean,
    val isTracked: Boolean,
    val dailyLimitMinutes: Int?,
    val usedMinutesToday: Int,
    val unlockedToday: Boolean,
)

data class AppsUiState(
    val monitoredRows: List<AppUsageRow> = emptyList(),
    val deactivatedRows: List<AppUsageRow> = emptyList(),
    val suggestedRows: List<AppUsageRow> = emptyList(),
    val searchSuggestions: List<AppUsageRow> = emptyList(),
    val isLoading: Boolean = true,
    val editingLimitFor: AppUsageRow? = null,
    val searchQuery: String = "",
)

/** Aviso exibido após o usuário trocar um limite já existente (aumento ou redução). */
data class LimitChangeNotice(
    val increased: Boolean,
    val previousMinutes: Int,
    val newMinutes: Int,
)

/**
 * Classifica a troca de limite: só há aviso quando havia limite antes e o novo é diferente.
 * Definir o primeiro limite ou remover o limite não gera aviso.
 */
fun classifyLimitChange(previousMinutes: Int?, newMinutes: Int?): LimitChangeNotice? {
    if (previousMinutes == null || newMinutes == null || previousMinutes == newMinutes) return null
    return LimitChangeNotice(
        increased = newMinutes > previousMinutes,
        previousMinutes = previousMinutes,
        newMinutes = newMinutes,
    )
}

/**
 * Oferta do passe livre do 1º dia (E24): no dia da instalação, o usuário define um limite que
 * o uso de hoje já estourou. Em vez de trancar na hora, o app pergunta.
 */
data class FirstDayOffer(
    val packageName: String,
    val appName: String,
    val usedMinutes: Int,
    val limitMinutes: Int,
)

/**
 * Oferece o passe livre só no dia da instalação, quando o novo limite já foi alcançado hoje,
 * o app ainda não está liberado e o usuário ainda não respondeu a oferta para esse app.
 */
fun shouldOfferFirstDayPass(
    isInstallDay: Boolean,
    usedMinutesToday: Int,
    newLimitMinutes: Int?,
    unlockedToday: Boolean,
    alreadyAnswered: Boolean,
): Boolean = isInstallDay &&
    newLimitMinutes != null &&
    usedMinutesToday >= newLimitMinutes &&
    !unlockedToday &&
    !alreadyAnswered

@HiltViewModel
class AppsViewModel @Inject constructor(
    private val installedAppsProvider: InstalledAppsProvider,
    private val monitoredAppRepository: MonitoredAppRepository,
    private val blockPreferences: BlockPreferences,
    private val usageStatsProvider: UsageStatsProvider,
    private val eventsRepository: EventsRepository,
    private val moneyPreferences: MoneyPreferences,
) : ViewModel() {

    private val installedApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    private val weeklyUsage = MutableStateFlow<Map<String, Long>>(emptyMap())
    private val loadingInstalled = MutableStateFlow(true)
    private val editingLimitForPackage = MutableStateFlow<String?>(null)
    private val searchQuery = MutableStateFlow("")

    private val _limitChangeNotice = MutableStateFlow<LimitChangeNotice?>(null)

    /** Aviso pós-troca de limite (irônico no aumento, seco no corte); null quando não há nada a dizer. */
    val limitChangeNotice: StateFlow<LimitChangeNotice?> = _limitChangeNotice

    private val _firstDayOffer = MutableStateFlow<FirstDayOffer?>(null)

    /** Pergunta do passe livre do 1º dia; null quando não há nada a perguntar. */
    val firstDayOffer: StateFlow<FirstDayOffer?> = _firstDayOffer

    /** Apps cuja oferta do 1º dia já foi respondida nesta tela (não pergunta de novo). */
    private val firstDayAnswered = mutableSetOf<String>()

    private data class BaseState(
        val installed: List<InstalledApp>,
        val monitoredUsage: List<MonitoredAppUsage>,
        val isLoadingInstalled: Boolean,
        val editingPackage: String?,
        val query: String,
    )

    private val baseState = combine(
        installedApps,
        monitoredAppRepository.observeMonitoredAppsUsage(),
        loadingInstalled,
        editingLimitForPackage,
        searchQuery,
    ) { installed, monitoredUsage, isLoadingInstalled, editingPackage, query ->
        BaseState(installed, monitoredUsage, isLoadingInstalled, editingPackage, query)
    }

    val uiState: StateFlow<AppsUiState> = combine(
        baseState,
        blockPreferences.unlockGrants,
        weeklyUsage,
    ) { base, unlockGrants, weeklyUsageByPkg ->
        val (installed, monitoredUsage, isLoadingInstalled, editingPackage, query) = base
        val usageByPackage = monitoredUsage.associateBy { it.packageName }
        val now = System.currentTimeMillis()
        val allRows = installed.map { app ->
            val usage = usageByPackage[app.packageName]
            val grant = unlockGrants[app.packageName]
            AppUsageRow(
                packageName = app.packageName,
                label = app.label,
                icon = app.icon,
                isMonitored = usage?.isMonitored ?: false,
                isTracked = usage != null,
                dailyLimitMinutes = usage?.dailyLimitMinutes,
                usedMinutesToday = usage?.usedMinutesToday ?: 0,
                unlockedToday = grant != null && now < grant.unlockUntilMs,
            )
        }
        val monitoredRows = allRows.filter { it.isMonitored }
        val deactivatedRows = allRows.filter { it.isTracked && !it.isMonitored }
        val trackedPackages = allRows.filter { it.isTracked }.map { it.packageName }.toSet()
        val suggestions = if (query.isBlank()) emptyList() else {
            allRows.filter { !trackedPackages.contains(it.packageName) && it.label.contains(query, ignoreCase = true) }
        }
        // Sugeridos ("os suspeitos de sempre"): primeiro os top 5 apps mais usados do
        // próprio usuário na semana (recomendação personalizada), depois os ralos de
        // tempo clássicos. Sempre apenas apps instalados (allRows vem do PackageManager)
        // e ainda fora da conta; sem candidatos, a seção simplesmente não aparece.
        val topUsedPackages = weeklyUsageByPkg.entries
            .sortedByDescending { it.value }
            .map { it.key }
            .take(5)
        val suggestedRows = (topUsedPackages + SUGGESTED_PACKAGES)
            .distinct()
            .mapNotNull { pkg -> allRows.find { it.packageName == pkg && !it.isTracked } }
        AppsUiState(
            monitoredRows = monitoredRows,
            deactivatedRows = deactivatedRows,
            suggestedRows = suggestedRows,
            searchSuggestions = suggestions,
            isLoading = isLoadingInstalled,
            editingLimitFor = allRows.find { it.packageName == editingPackage },
            searchQuery = query,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppsUiState(),
    )

    init {
        viewModelScope.launch {
            installedApps.value = installedAppsProvider.getLaunchableApps()
            loadingInstalled.value = false
        }
        viewModelScope.launch {
            weeklyUsage.value = usageStatsProvider.getWeeklyUsageByPackage()
        }
        viewModelScope.launch {
            while (isActive) {
                monitoredAppRepository.syncTodayUsage()
                delay(SYNC_INTERVAL_MS)
            }
        }
    }

    fun addMonitoredFromSearch(packageName: String, appName: String) {
        searchQuery.value = ""
        viewModelScope.launch {
            monitoredAppRepository.setMonitored(packageName, appName, true)
            monitoredAppRepository.syncTodayUsage()
        }
    }

    fun setMonitored(packageName: String, appName: String, monitored: Boolean) {
        viewModelScope.launch {
            monitoredAppRepository.setMonitored(packageName, appName, monitored)
            if (monitored) {
                monitoredAppRepository.syncTodayUsage()
            }
        }
    }

    /** Remove permanentemente um app desativado da lista de "Desativados". */
    fun deleteDeactivatedApp(packageName: String) {
        viewModelScope.launch {
            monitoredAppRepository.deleteMonitoredApp(packageName)
        }
    }

    /** Atualiza o termo de busca usado para filtrar a lista de apps por nome. */
    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    /** Abre o diálogo de definição de limite diário para o app informado. */
    fun openLimitEditor(packageName: String) {
        editingLimitForPackage.value = packageName
    }

    /** Fecha o diálogo sem salvar alterações. */
    fun dismissLimitEditor() {
        editingLimitForPackage.value = null
    }

    /** Salva o novo limite diário (em minutos) e fecha o diálogo. Use null para remover o limite. */
    fun confirmDailyLimit(packageName: String, minutes: Int?) {
        val row = uiState.value.editingLimitFor?.takeIf { it.packageName == packageName }
        val previousMinutes = row?.dailyLimitMinutes
        if (row != null && minutes != null && shouldOfferFirstDayPass(
                isInstallDay = installedAppsProvider.isInstallDay(),
                usedMinutesToday = row.usedMinutesToday,
                newLimitMinutes = minutes,
                unlockedToday = row.unlockedToday,
                alreadyAnswered = packageName in firstDayAnswered,
            )
        ) {
            // O limite só é salvo depois da resposta: salvar antes trancaria o app na hora.
            _firstDayOffer.value = FirstDayOffer(packageName, row.label, row.usedMinutesToday, minutes)
            editingLimitForPackage.value = null
            return
        }
        _limitChangeNotice.value = classifyLimitChange(previousMinutes, minutes)
        viewModelScope.launch {
            monitoredAppRepository.setDailyLimit(packageName, minutes)
        }
        editingLimitForPackage.value = null
    }

    /**
     * "Sim, hoje é passe livre": libera o app até a meia-noite sem cobrar (recibo de valor 0)
     * e só então salva o limite — nessa ordem, o serviço de acessibilidade nunca vê o limite
     * estourado sem o passe.
     */
    fun acceptFirstDayPass() {
        val offer = _firstDayOffer.value ?: return
        _firstDayOffer.value = null
        firstDayAnswered += offer.packageName
        blockPreferences.grantUnlockForToday(offer.packageName)
        viewModelScope.launch {
            monitoredAppRepository.setDailyLimit(offer.packageName, offer.limitMinutes)
            eventsRepository.recordUnlock(
                packageName = offer.packageName,
                appLabel = offer.appName,
                amount = FREE_PASS_AMOUNT,
                currency = moneyPreferences.settings.first().currency.code,
                method = PaymentMethod.FREE_PASS,
            )
        }
    }

    /** "Não, pode trancar": salva o limite; o bloqueio dispara na próxima abertura do app. */
    fun declineFirstDayPass() {
        val offer = _firstDayOffer.value ?: return
        _firstDayOffer.value = null
        firstDayAnswered += offer.packageName
        viewModelScope.launch { monitoredAppRepository.setDailyLimit(offer.packageName, offer.limitMinutes) }
    }

    /** Fechou a pergunta sem responder: nada muda, o limite não é salvo. */
    fun dismissFirstDayOffer() {
        _firstDayOffer.value = null
    }

    /** Fecha o aviso de troca de limite. */
    fun dismissLimitChangeNotice() {
        _limitChangeNotice.value = null
    }

    private companion object {
        const val SYNC_INTERVAL_MS = 5_000L
        const val FREE_PASS_AMOUNT = "0.00"

        /** Ralos de tempo clássicos, sugeridos por padrão quando instalados e fora da conta. */
        val SUGGESTED_PACKAGES = listOf(
            "com.instagram.android",
            "com.facebook.katana",
            "com.zhiliaoapp.musically", // TikTok
            "com.google.android.youtube",
        )
    }
}
