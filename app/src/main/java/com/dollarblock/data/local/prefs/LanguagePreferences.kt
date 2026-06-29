package com.dollarblock.data.local.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Idioma do app.
 *
 * - [SYSTEM]: segue o idioma do celular — português (pt-BR/pt-PT/…) cai em `values-pt`,
 *   qualquer outro idioma cai no `values` padrão (inglês).
 * - [ENGLISH] / [PORTUGUESE]: força o idioma independentemente do celular.
 *
 * O [languageTag] é o que entra no `LocaleListCompat` aplicado via
 * `AppCompatDelegate.setApplicationLocales`. Vazio = "siga o sistema".
 */
enum class AppLanguage(val languageTag: String) {
    SYSTEM(""),
    ENGLISH("en"),
    PORTUGUESE("pt"),
}

private val Context.languageDataStore by preferencesDataStore(name = "dollarblock_language")

/**
 * Persiste a preferência de idioma (espelha [ThemePreferences]).
 *
 * Observação: a aplicação efetiva do locale é feita via `AppCompatDelegate`
 * (que por sua vez já persiste a escolha quando `autoStoreLocales=true`); guardamos
 * também aqui para ter uma fonte única em DataStore, exibir o valor atual no Profile
 * e incluir no reset de debug.
 */
@Singleton
class LanguagePreferences @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val dataStore = context.languageDataStore
    private val languageKey = stringPreferencesKey("app_language")

    val language: Flow<AppLanguage> = dataStore.data.map { prefs ->
        when (prefs[languageKey]) {
            AppLanguage.ENGLISH.name -> AppLanguage.ENGLISH
            AppLanguage.PORTUGUESE.name -> AppLanguage.PORTUGUESE
            else -> AppLanguage.SYSTEM
        }
    }

    suspend fun setLanguage(language: AppLanguage) {
        dataStore.edit { it[languageKey] = language.name }
    }

    suspend fun reset() {
        dataStore.edit { it.clear() }
    }
}
