package com.dollarblock.data.apps

import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
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
    suspend fun getLaunchableApps(): List<InstalledApp> = withContext(Dispatchers.IO) {
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
