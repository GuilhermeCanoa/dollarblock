package com.dollarblock.data.apps

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lê os apps lançáveis instalados via [android.content.pm.PackageManager].
 * Usado pelo seletor de apps na Home (bloqueio).
 */
@Singleton
class InstalledAppsProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    // Carregar label + ícone de ~100 apps leva segundos; sem cache, a aba Apps refazia
    // esse trabalho a cada navegação. O cache é descartado quando um app é instalado,
    // removido ou atualizado: o serviço de acessibilidade mantém o processo vivo por dias,
    // então "esperar o processo morrer" deixava um app reinstalado (ex.: Instagram) fora da
    // aba Apps indefinidamente, embora seguisse no banco e aparecendo na Home.
    @Volatile
    private var cachedApps: List<InstalledApp>? = null
    private val cacheMutex = Mutex()

    private val _changes = MutableStateFlow(0)

    /** Muda a cada instalação/remoção de app — quem lista apps recarrega ao coletar. */
    val changes: StateFlow<Int> = _changes.asStateFlow()

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            cachedApps = null
            _changes.value++
        }
    }

    init {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        // Broadcasts de pacote vêm do sistema: chegam mesmo com NOT_EXPORTED.
        ContextCompat.registerReceiver(context, packageReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    /**
     * Hoje é o dia em que o DollarBlock foi instalado (dia local de `firstInstallTime`)?
     * Base do passe livre do 1º dia (E24). Reinstalar conta como um novo primeiro dia.
     */
    fun isInstallDay(): Boolean = runCatching {
        val installedAt = context.packageManager.getPackageInfo(context.packageName, 0).firstInstallTime
        val zone = java.time.ZoneId.systemDefault()
        java.time.Instant.ofEpochMilli(installedAt).atZone(zone).toLocalDate() == java.time.LocalDate.now(zone)
    }.getOrDefault(false)

    suspend fun getLaunchableApps(): List<InstalledApp> {
        cachedApps?.let { return it }
        return cacheMutex.withLock {
            cachedApps ?: loadLaunchableApps().also { cachedApps = it }
        }
    }

    private suspend fun loadLaunchableApps(): List<InstalledApp> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(intent, 0)
            .asSequence()
            .map { it.activityInfo.packageName }
            .distinct()
            .filter { it != context.packageName }
            .mapNotNull { pkg ->
                runCatching {
                    val appInfo = pm.getApplicationInfo(pkg, 0)
                    val label = pm.getApplicationLabel(appInfo).toString()
                    val icon = runCatching {
                        pm.getApplicationIcon(appInfo).toBitmap(ICON_PX, ICON_PX).asImageBitmap()
                    }.getOrNull()
                    InstalledApp(packageName = pkg, label = label, icon = icon)
                }.getOrNull()
            }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    suspend fun getIconForPackage(packageName: String): ImageBitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
            context.packageManager.getApplicationIcon(appInfo).toBitmap(ICON_PX, ICON_PX).asImageBitmap()
        }.getOrNull()
    }

    private companion object {
        const val ICON_PX = 96
    }
}
