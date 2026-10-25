package com.dollarblock.core.locale

import android.content.res.Resources
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
 * do celular: pt-BR/pt-PT/… resolvem para `values-pt`, hi para `values-hi`, qualquer outro
 * para o `values` padrão (inglês). Exceção: com a região do celular na Índia, SYSTEM aplica
 * hindi — a maioria dos aparelhos lá vem em inglês (en-IN), e o padrão do app na Índia é
 * hindi. Trocar o idioma recria as Activities automaticamente.
 */
object LocaleManager {

    fun apply(language: AppLanguage) {
        val tag = resolveLanguageTag(language, systemRegion())
        val locales = if (tag.isEmpty()) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(tag)
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }

    /** Tag efetiva para [language]; vazio = seguir o sistema. Puro, para teste em JVM. */
    fun resolveLanguageTag(language: AppLanguage, systemRegion: String): String =
        if (language == AppLanguage.SYSTEM && systemRegion.equals("IN", ignoreCase = true)) {
            AppLanguage.HINDI.languageTag
        } else {
            language.languageTag
        }

    // `Resources.getSystem()` reflete o locale do aparelho, não o override por app — então a
    // região continua sendo a do celular mesmo depois de aplicarmos hindi.
    private fun systemRegion(): String =
        Resources.getSystem().configuration.locales[0]?.country.orEmpty()
}
