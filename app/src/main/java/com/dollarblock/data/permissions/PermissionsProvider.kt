package com.dollarblock.data.permissions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.dollarblock.data.usage.UsageStatsProvider
import com.dollarblock.service.accessibility.accessibilitySettingsIntent
import com.dollarblock.service.accessibility.isAccessibilityServiceEnabled
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** As quatro permissões que o onboarding (E2) explica e solicita. */
enum class AppPermission { USAGE_ACCESS, ACCESSIBILITY, OVERLAY, NOTIFICATIONS }

/** Estado consolidado das permissões usado pelo onboarding e (futuramente) pelo Profile. */
data class PermissionsState(
    val usageAccess: Boolean = false,
    val accessibility: Boolean = false,
    val overlay: Boolean = false,
    val notifications: Boolean = false,
) {
    fun isGranted(permission: AppPermission): Boolean = when (permission) {
        AppPermission.USAGE_ACCESS -> usageAccess
        AppPermission.ACCESSIBILITY -> accessibility
        AppPermission.OVERLAY -> overlay
        AppPermission.NOTIFICATIONS -> notifications
    }
}

/**
 * Fonte única para verificar e solicitar as permissões do DollarBlock. Agrega as
 * permissões especiais (Usage Access, Acessibilidade, Sobreposição — concedidas em
 * telas do sistema) e a permissão de runtime de Notificações (Android 13+).
 */
@Singleton
class PermissionsProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val usageStatsProvider: UsageStatsProvider,
) {
    /** Lê o estado atual de todas as permissões. Barato — pode ser chamado em cada ON_RESUME. */
    fun currentState(): PermissionsState = PermissionsState(
        usageAccess = hasUsageAccess(),
        accessibility = hasAccessibility(),
        overlay = hasOverlay(),
        notifications = hasNotifications(),
    )

    fun hasUsageAccess(): Boolean = usageStatsProvider.hasUsageAccess()

    fun hasAccessibility(): Boolean = isAccessibilityServiceEnabled(context)

    fun hasOverlay(): Boolean = Settings.canDrawOverlays(context)

    /** No Android < 13 a notificação é concedida por padrão (não há runtime permission). */
    fun hasNotifications(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

    fun usageAccessIntent(): Intent = usageStatsProvider.usageAccessSettingsIntent()

    fun accessibilityIntent(): Intent = accessibilitySettingsIntent()

    /** Abre a tela de sobreposição já apontando para o pacote do DollarBlock. */
    fun overlayIntent(): Intent = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:${context.packageName}"),
    )
}
