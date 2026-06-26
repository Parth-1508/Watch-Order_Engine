package com.example.watchorderengine

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.watchorderengine.data.prefs.ThemeMode
import com.example.watchorderengine.ui.navigation.AppNavigation
import com.example.watchorderengine.ui.theme.AppThemeMode
import com.example.watchorderengine.ui.theme.WatchOrderEngineTheme
import com.example.watchorderengine.ui.viewmodel.SettingsViewModel
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Ensure user is signed in (anonymously) so repository calls don't fail
        if (auth.currentUser == null) {
            auth.signInAnonymously()
        }

        enableEdgeToEdge()
        setContent {
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            val themeMode by settingsViewModel.themeMode.collectAsState()
            
            val appThemeMode = when (themeMode) {
                ThemeMode.DEFAULT -> AppThemeMode.DEFAULT
                ThemeMode.DARK -> AppThemeMode.DARK
                ThemeMode.LIGHT -> AppThemeMode.LIGHT
                ThemeMode.COMIC -> AppThemeMode.COMIC
                ThemeMode.MANGA -> AppThemeMode.MANGA
                ThemeMode.FUNK -> AppThemeMode.FUNK
                ThemeMode.SYSTEM -> if (androidx.compose.foundation.isSystemInDarkTheme()) AppThemeMode.DARK else AppThemeMode.DEFAULT
            }

            WatchOrderEngineTheme(mode = appThemeMode) {
                AppNavigation()
            }
        }
    }
}
