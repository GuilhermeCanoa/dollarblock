package com.dollarblock.service.accessibility

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings

/** Verifica se o serviço de bloqueio do DollarBlock está habilitado nas Configurações. */
fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val expected = ComponentName(context, DollarBlockAccessibilityService::class.java)
        .flattenToString()
    val enabled = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
    ) ?: return false
    return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
}

/** Intent para abrir a tela de Acessibilidade do sistema. */
fun accessibilitySettingsIntent(): Intent =
    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
