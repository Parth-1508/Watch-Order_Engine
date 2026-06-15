package com.example.watchorderengine.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.watchorderengine.data.db.WatchOrderDatabase
import com.example.watchorderengine.data.prefs.LayoutStyle
import com.example.watchorderengine.data.prefs.ThemeMode
import com.example.watchorderengine.data.prefs.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefsRepository: UserPreferencesRepository,
    private val db: WatchOrderDatabase
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = prefsRepository.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.SYSTEM)

    val layoutStyle: StateFlow<LayoutStyle> = prefsRepository.layoutStyle
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LayoutStyle.COMFORT)

    val hideFiller: StateFlow<Boolean> = prefsRepository.hideFiller
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { prefsRepository.setThemeMode(mode) }
    }

    fun setLayoutStyle(style: LayoutStyle) {
        viewModelScope.launch { prefsRepository.setLayoutStyle(style) }
    }

    fun setHideFiller(hide: Boolean) {
        viewModelScope.launch { prefsRepository.setHideFiller(hide) }
    }

    fun clearCache() {
        viewModelScope.launch {
            db.clearAllTables()
        }
    }
}
