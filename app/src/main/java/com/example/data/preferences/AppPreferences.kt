package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.AppThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "hamari_bc_prefs")

class AppPreferences(private val context: Context) {
  private val themeKey = stringPreferencesKey("theme_mode")
  private val soundEnabledKey = booleanPreferencesKey("sound_enabled")

  val themeModeFlow: Flow<AppThemeMode> = context.dataStore.data.map { prefs ->
    AppThemeMode.fromKey(prefs[themeKey])
  }

  val soundEnabledFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
    prefs[soundEnabledKey] ?: true
  }

  suspend fun setThemeMode(mode: AppThemeMode) {
    context.dataStore.edit { prefs ->
      prefs[themeKey] = mode.storageKey
    }
  }

  suspend fun setSoundEnabled(enabled: Boolean) {
    context.dataStore.edit { prefs ->
      prefs[soundEnabledKey] = enabled
    }
  }
}
