package com.gparv.ploy

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private val themeKey = stringPreferencesKey("now_playing_theme")
    private val powerKey = booleanPreferencesKey("power_saving")

    val theme: Flow<NowPlayingTheme> = context.dataStore.data.map { prefs ->
        NowPlayingTheme.values().firstOrNull { it.name == prefs[themeKey] }
            ?: NowPlayingTheme.Default
    }
    val powerSaving: Flow<Boolean> = context.dataStore.data.map { it[powerKey] ?: false }

    suspend fun setTheme(t: NowPlayingTheme) { context.dataStore.edit { it[themeKey] = t.name } }
    suspend fun setPowerSaving(on: Boolean) { context.dataStore.edit { it[powerKey] = on } }
}

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = SettingsRepository(app)

    val theme: StateFlow<NowPlayingTheme> =
        repo.theme.stateIn(viewModelScope, SharingStarted.Eagerly, NowPlayingTheme.Default)
    val powerSaving: StateFlow<Boolean> =
        repo.powerSaving.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun setTheme(t: NowPlayingTheme) { viewModelScope.launch { repo.setTheme(t) } }
    fun setPowerSaving(on: Boolean) { viewModelScope.launch { repo.setPowerSaving(on) } }
}