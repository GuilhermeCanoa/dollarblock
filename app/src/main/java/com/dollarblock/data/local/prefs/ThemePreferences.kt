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

enum class AppTheme { DARK, LIGHT, SYSTEM }

private val Context.themeDataStore by preferencesDataStore(name = "dollarblock_theme")

@Singleton
class ThemePreferences @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val dataStore = context.themeDataStore
    private val themeKey = stringPreferencesKey("app_theme")

    val theme: Flow<AppTheme> = dataStore.data.map { prefs ->
        when (prefs[themeKey]) {
            AppTheme.LIGHT.name -> AppTheme.LIGHT
            AppTheme.SYSTEM.name -> AppTheme.SYSTEM
            else -> AppTheme.DARK
        }
    }

    suspend fun setTheme(theme: AppTheme) {
        dataStore.edit { it[themeKey] = theme.name }
    }

    suspend fun reset() {
        dataStore.edit { it.clear() }
    }
}
