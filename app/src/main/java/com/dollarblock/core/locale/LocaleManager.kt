package com.dollarblock.core.locale

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.dollarblock.data.local.prefs.AppLanguage

/**
 * Ponto único para aplicar o idioma do app via a API oficial de "per-app language"
 * do AndroidX ([AppCompatDelegate.setApplicationLocales]).
 *
 * Com `autoStoreLocales=true` no manifest, o AppCompat **persiste e restaura** a escolha
 * sozinho a cada reinício do app (nativo no Android 13+, backport abaixo disso). Por isso
 * só precisamos chamar [apply] quando o usuário troca o idioma — não no cold start.
 *
 * [AppLanguage.SYSTEM] usa uma lista de locales vazia, que faz o sistema seguir o idioma
 * do celular: pt-BR/pt-PT/… resolvem para `values-pt`, qualquer outro para o `values` padrão
 * (inglês). Trocar o idioma recria as Activities automaticamente.
 */
object LocaleManager {

    fun apply(language: AppLanguage) {
        val locales = if (language.languageTag.isEmpty()) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(language.languageTag)
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }
}
