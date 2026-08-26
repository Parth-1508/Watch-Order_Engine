package com.example.watchorderengine

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.watchorderengine.data.prefs.ThemeMode
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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

    private var pendingTargetId by mutableStateOf<String?>(null)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        pendingTargetId = intent.getStringExtra("targetId")

        checkAndRequestNotificationPermission()
        
        // Ensure user is signed in (anonymously) so repository calls don't fail
        if (auth.currentUser == null) {
            auth.signInAnonymously().addOnFailureListener {
                android.util.Log.e("MainActivity", "Anonymous sign-in failed", it)
            }
        }

        enableEdgeToEdge()
        setContent {
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            val themeMode by settingsViewModel.themeMode.collectAsStateWithLifecycle()
            
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
                AppNavigation(
                    startTargetId = pendingTargetId,
                    onTargetIdConsumed = { pendingTargetId = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        val targetId = intent.getStringExtra("targetId")
        if (targetId != null) {
            pendingTargetId = targetId
        }
    }

    private fun checkAndRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
