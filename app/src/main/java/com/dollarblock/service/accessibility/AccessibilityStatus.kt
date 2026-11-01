package com.dollarblock.service.accessibility

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityManager

/**
 * Verifica se o serviço de bloqueio do DollarBlock está habilitado nas Configurações.
 *
 * Duas fontes, basta uma dizer "sim":
 * 1. `ENABLED_ACCESSIBILITY_SERVICES` — lido com [isServiceInEnabledList], que aceita o
 *    formato completo (AOSP, Samsung) e o abreviado `pkg/.Classe` (Motorola e outros).
 *    Comparar a string exata fazia o onboarding pedir a permissão para sempre nesses aparelhos.
 * 2. `AccessibilityManager` — API oficial, cobre ROMs que guardam o valor de outro jeito.
 */
fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val pkg = context.packageName
    val cls = DollarBlockAccessibilityService::class.java.name
    val setting = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
    )
    if (isServiceInEnabledList(setting, pkg, cls)) return true

    val manager = context.getSystemService(AccessibilityManager::class.java) ?: return false
    return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        .any { it.resolveInfo?.serviceInfo?.let { s -> s.packageName == pkg && s.name == cls } == true }
}

/**
 * `true` se [setting] (lista separada por `:` de `pacote/classe`) contém o serviço
 * [pkg]/[cls]. Mesma regra do `ComponentName.unflattenFromString`: classe começando
 * com `.` é relativa ao pacote.
 */
internal fun isServiceInEnabledList(setting: String?, pkg: String, cls: String): Boolean {
    if (setting.isNullOrBlank()) return false
    return setting.split(':').any { entry ->
        val slash = entry.indexOf('/')
        if (slash <= 0) return@any false
        val entryPkg = entry.substring(0, slash).trim()
        var entryCls = entry.substring(slash + 1).trim()
        if (entryCls.startsWith('.')) entryCls = entryPkg + entryCls
        entryPkg.equals(pkg, ignoreCase = true) && entryCls.equals(cls, ignoreCase = true)
    }
}

/** Intent para abrir a tela de Acessibilidade do sistema. */
fun accessibilitySettingsIntent(): Intent =
    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
