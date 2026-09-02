package com.example.watchorderengine.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.watchorderengine.BuildConfig
import com.example.watchorderengine.data.backup.ImportStrategy
import com.example.watchorderengine.data.prefs.ThemeMode
import com.example.watchorderengine.ui.screens.home.ThemeBorderModifier
import com.example.watchorderengine.ui.theme.LocalAppTheme
import com.example.watchorderengine.ui.viewmodel.BackupUiState
import com.example.watchorderengine.ui.viewmodel.ChangePasswordState
import com.example.watchorderengine.ui.viewmodel.SettingsViewModel
import com.example.watchorderengine.ui.viewmodel.WipeAccountState
import com.example.watchorderengine.worker.AiringAlertWorker
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onLogout: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val theme = LocalAppTheme.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val scrollState = rememberScrollState()
    val currentThemeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val hideFiller by viewModel.hideFiller.collectAsStateWithLifecycle()
    val hideUnwatchedSpoilers by viewModel.hideUnwatchedSpoilers.collectAsStateWithLifecycle()
    val cloudSyncEnabled by viewModel.cloudSyncEnabled.collectAsStateWithLifecycle()
    val dynamicShowTheming by viewModel.dynamicShowTheming.collectAsStateWithLifecycle()
    val airingAlertsEnabled by viewModel.airingAlertsEnabled.collectAsStateWithLifecycle()
    val preferredHomeLanguages by viewModel.preferredHomeLanguages.collectAsStateWithLifecycle()
    val wipeAccountState by viewModel.wipeAccountState.collectAsStateWithLifecycle()
    val backupState by viewModel.backupState.collectAsStateWithLifecycle()
    val changePasswordState by viewModel.changePasswordState.collectAsStateWithLifecycle()

    var showClearCacheDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showChangePasswordDialog by remember { mutableStateOf(false) }
    var showImportStrategyDialog by remember { mutableStateOf(false) }
    var pendingImportUri by remember { mutableStateOf<android.net.Uri?>(null) }
    
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf<String?>(null) }

    var showClearedToast by remember { mutableStateOf(false) }
    var showWipeAccountDialog by remember { mutableStateOf(false) }

    // Lock to default "Engine" colors for critical account actions
    val engineAccent = Color(0xFFFFC300)

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.setAiringAlertsEnabled(true)
        if (granted) AiringAlertWorker.schedule(context) else AiringAlertWorker.cancel(context)
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { viewModel.exportBackup(it) } }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> 
        uri?.let { 
            pendingImportUri = it
            showImportStrategyDialog = true 
        } 
    }

    if (showImportStrategyDialog) {
        AlertDialog(
            onDismissRequest = { showImportStrategyDialog = false },
            title = { Text("Import Backup", fontWeight = FontWeight.Black) },
            text = { Text("MERGE adds/updates data from the file to your current list. REPLACE ALL erases everything first (destructive).") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.importBackup(pendingImportUri!!, ImportStrategy.MERGE)
                    showImportStrategyDialog = false
                }) { Text("MERGE", fontWeight = FontWeight.Bold, color = theme.accent) }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.importBackup(pendingImportUri!!, ImportStrategy.REPLACE_ALL)
                    showImportStrategyDialog = false
                }) { Text("REPLACE ALL", fontWeight = FontWeight.Bold, color = Color.Red) }
            }
        )
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Log Out?", fontWeight = FontWeight.Black) },
            text = { Text("You will be signed out and redirected to the opening screen. Your local cache will be cleared.") },
            confirmButton = {
                TextButton(onClick = {
                    // Sign out of Google to ensure the account picker shows next time
                    val gso = com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestIdToken("103547777861-dnhefnhft99dnhugodfbtcbtis13jd3b.apps.googleusercontent.com")
                        .requestEmail()
                        .build()
                    val client = com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(context, gso)
                    
                    client.signOut().addOnCompleteListener {
                        viewModel.signOut()
                        showLogoutDialog = false
                        onLogout()
                    }
                }) { Text("Log Out", fontWeight = FontWeight.Bold, color = Color(0xFFFF4500)) }
            },
            dismissButton = { TextButton(onClick = { showLogoutDialog = false }) { Text("Cancel") } }
        )
    }

    if (showChangePasswordDialog) {
        AlertDialog(
            onDismissRequest = { 
                if (changePasswordState !is ChangePasswordState.Loading) {
                    showChangePasswordDialog = false
                    viewModel.acknowledgePasswordResult()
                    newPassword = ""; confirmPassword = ""; passwordError = null
                }
            },
            containerColor = theme.surface,
            titleContentColor = engineAccent,
            title = { Text("Update Password", fontWeight = FontWeight.Black) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    when (changePasswordState) {
                        is ChangePasswordState.Idle, is ChangePasswordState.Loading, is ChangePasswordState.Error -> {
                            Text("Enter and confirm your new password (min. 6 characters).", color = Color.Gray, fontSize = 12.sp)
                            
                            OutlinedTextField(
                                value = newPassword,
                                onValueChange = { newPassword = it; passwordError = null },
                                label = { Text("New Password") },
                                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth(),
                                enabled = changePasswordState !is ChangePasswordState.Loading,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = engineAccent,
                                    unfocusedBorderColor = theme.border,
                                    focusedTextColor = theme.textPrimary,
                                    unfocusedTextColor = theme.textPrimary
                                )
                            )

                            OutlinedTextField(
                                value = confirmPassword,
                                onValueChange = { confirmPassword = it; passwordError = null },
                                label = { Text("Confirm New Password") },
                                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth(),
                                enabled = changePasswordState !is ChangePasswordState.Loading,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = engineAccent,
                                    unfocusedBorderColor = theme.border,
                                    focusedTextColor = theme.textPrimary,
                                    unfocusedTextColor = theme.textPrimary
                                )
                            )

                            val displayError = passwordError ?: (changePasswordState as? ChangePasswordState.Error)?.message
                            if (displayError != null) {
                                Text(displayError, color = Color.Red, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            
                            if (changePasswordState is ChangePasswordState.Loading) {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = engineAccent)
                            }
                        }
                        is ChangePasswordState.Success -> {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.CheckCircle, null, tint = Color.Green, modifier = Modifier.size(48.dp))
                                Spacer(Modifier.height(8.dp))
                                Text("Password updated successfully!", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (changePasswordState is ChangePasswordState.Success) {
                    TextButton(onClick = { 
                        showChangePasswordDialog = false
                        viewModel.acknowledgePasswordResult()
                        newPassword = ""; confirmPassword = ""; passwordError = null
                    }) {
                        Text("DONE", color = engineAccent, fontWeight = FontWeight.Black)
                    }
                } else {
                    Button(
                        enabled = changePasswordState !is ChangePasswordState.Loading,
                        onClick = {
                            if (newPassword.length < 6) {
                                passwordError = "Password must be at least 6 characters."
                                return@Button
                            }
                            if (newPassword != confirmPassword) {
                                passwordError = "Passwords do not match."
                                return@Button
                            }
                            viewModel.changePassword(newPassword)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = engineAccent)
                    ) {
                        Text("UPDATE", color = Color.Black, fontWeight = FontWeight.Black)
                    }
                }
            },
            dismissButton = {
                if (changePasswordState !is ChangePasswordState.Success) {
                    TextButton(
                        enabled = changePasswordState !is ChangePasswordState.Loading,
                        onClick = { 
                            showChangePasswordDialog = false
                            viewModel.acknowledgePasswordResult()
                            newPassword = ""; confirmPassword = ""; passwordError = null
                        }
                    ) {
                        Text("CANCEL", color = Color.Gray)
                    }
                }
            }
        )
    }

    if (showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            title = { Text("Clear Local Cache?", fontWeight = FontWeight.Black) },
            text = {
                Text(
                    "This removes cached show, episode, and cast data — it'll re-download from TMDB next time you open a title. " +
                    "Your watch progress, ratings, and AI-generated watch orders are NOT affected; those live in your account, not here."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearCache()
                    showClearCacheDialog = false
                    showClearedToast = true
                }) { Text("Clear", fontWeight = FontWeight.Bold, color = Color(0xFFFF4500)) }
            },
            dismissButton = { TextButton(onClick = { showClearCacheDialog = false }) { Text("Cancel") } }
        )
    }

    if (showWipeAccountDialog) {
        AlertDialog(
            onDismissRequest = { showWipeAccountDialog = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFF4500)) },
            title = { Text("Clear All Account Data?", fontWeight = FontWeight.Black) },
            text = {
                Text(
                    "This permanently deletes all your watch progress, ratings, and generated watch orders from the cloud and this device. " +
                    "This action is irreversible and you will be logged out."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showWipeAccountDialog = false
                    // Sign out of Google as part of the wipe process
                    val gso = com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestIdToken("103547777861-dnhefnhft99dnhugodfbtcbtis13jd3b.apps.googleusercontent.com")
                        .requestEmail()
                        .build()
                    val client = com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(context, gso)
                    client.revokeAccess().addOnCompleteListener {
                        client.signOut().addOnCompleteListener {
                            viewModel.wipeAllAccountData()
                        }
                    }
                }) { Text("Clear Everything", fontWeight = FontWeight.Bold, color = Color(0xFFFF4500)) }
            },
            dismissButton = { TextButton(onClick = { showWipeAccountDialog = false }) { Text("Cancel") } }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.background)
            .verticalScroll(scrollState)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = theme.textPrimary)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "SETTINGS",
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                fontStyle = FontStyle.Italic,
                color = theme.textPrimary
            )
        }

        // Appearance Section
        SettingSectionTitle("APPEARANCE")
        Surface(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
                .then(ThemeBorderModifier()),
            color = theme.surface
        ) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                val visibleThemes = ThemeMode.entries.filter { it != ThemeMode.SYSTEM }
                visibleThemes.forEachIndexed { index, mode ->
                    ThemeOptionRow(
                        mode = mode,
                        isSelected = currentThemeMode == mode,
                        onClick = { viewModel.setThemeMode(mode) }
                    )
                    if (index < visibleThemes.size - 1) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = theme.textPrimary.copy(alpha = 0.05f)
                        )
                    }
                }
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = theme.textPrimary.copy(alpha = 0.05f)
                )
                PreferenceToggleRow(
                    icon = Icons.Default.Palette,
                    title = "DYNAMIC SHOW COLORS",
                    subtitle = if (dynamicShowTheming) "DETAIL SCREEN TINTED FROM POSTER ART" else "USING FIXED THEME ACCENT",
                    checked = dynamicShowTheming,
                    onCheckedChange = { viewModel.setDynamicShowTheming(it) }
                )
            }
        }

        // Preferences Section
        SettingSectionTitle("PREFERENCES")
        Surface(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
                .then(ThemeBorderModifier()),
            color = theme.surface
        ) {
            Column {
                PreferenceToggleRow(
                    icon = Icons.Default.FilterList,
                    title = "HIDE FILLERS",
                    subtitle = if (hideFiller) "FILLER EPISODES ARE HIDDEN" else "SHOW ALL EPISODES",
                    checked = hideFiller,
                    onCheckedChange = { viewModel.setHideFiller(it) }
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = theme.textPrimary.copy(alpha = 0.05f)
                )
                PreferenceToggleRow(
                    icon = Icons.Default.VisibilityOff,
                    title = "SPOILER SHIELD",
                    subtitle = if (hideUnwatchedSpoilers) "UNWATCHED SYNOPSES BLURRED" else "SHOW ALL SYNOPSES",
                    checked = hideUnwatchedSpoilers,
                    onCheckedChange = { viewModel.setHideUnwatchedSpoilers(it) }
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = theme.textPrimary.copy(alpha = 0.05f)
                )
                PreferenceToggleRow(
                    icon = Icons.Default.CloudSync,
                    title = "CLOUD SYNC",
                    subtitle = if (cloudSyncEnabled) "PROGRESS SYNCED TO FIRESTORE" else "LOCAL ONLY — DATA STAYS ON DEVICE",
                    checked = cloudSyncEnabled,
                    onCheckedChange = { viewModel.setCloudSyncEnabled(it) }
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = theme.textPrimary.copy(alpha = 0.05f)
                )
                PreferenceToggleRow(
                    icon     = Icons.Default.NotificationsActive,
                    title    = "AIRING ALERTS",
                    subtitle = if (airingAlertsEnabled)
                                   "CHECKS ~2X DAILY FOR NEW EPISODES"
                               else
                                   "GET NOTIFIED WHEN A SHOW YOU'RE WATCHING DROPS A NEW EPISODE",
                    checked  = airingAlertsEnabled,
                    onCheckedChange = { enabled ->
                        if (!enabled) {
                            viewModel.setAiringAlertsEnabled(false)
                            AiringAlertWorker.cancel(context)
                            return@PreferenceToggleRow
                        }
                        val alreadyGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                                PackageManager.PERMISSION_GRANTED
                        if (alreadyGranted) {
                            viewModel.setAiringAlertsEnabled(true)
                            AiringAlertWorker.schedule(context)
                        } else {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                )
            }
        }

        // Home Languages Section
        SettingSectionTitle("HOME SCREEN LANGUAGES")
        Surface(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
                .then(ThemeBorderModifier()),
            color = theme.surface
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "CHOOSE REGIONAL LANGUAGE CAROUSELS FOR HOME",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                com.example.watchorderengine.network.TmdbConfig.HOME_LANGUAGE_SECTIONS.forEach { section ->
                    val isSelected = section.code in preferredHomeLanguages
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.toggleHomeLanguage(section.code) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            section.label,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = theme.textPrimary
                        )
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { viewModel.toggleHomeLanguage(section.code) },
                            colors = CheckboxDefaults.colors(checkedColor = theme.accent)
                        )
                    }
                }
            }
        }

        // Data Section
        SettingSectionTitle("DATA")
        Surface(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
                .then(ThemeBorderModifier()),
            color = theme.surface
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clickable { showClearCacheDialog = true }
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.DeleteSweep, null, tint = theme.textPrimary)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("CLEAR LOCAL CACHE", fontWeight = FontWeight.Black, fontSize = 14.sp, color = theme.textPrimary)
                        Text("Cached TMDB metadata only — progress is safe", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = theme.textPrimary.copy(alpha = 0.05f))
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clickable { 
                            val timestamp = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                            exportLauncher.launch("watchorderengine_backup_$timestamp.json")
                        }
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Upload, null, tint = theme.textPrimary)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("EXPORT BACKUP", fontWeight = FontWeight.Black, fontSize = 14.sp, color = theme.textPrimary)
                        Text("Save your data to a local JSON file", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = theme.textPrimary.copy(alpha = 0.05f))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clickable { importLauncher.launch(arrayOf("application/json")) }
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Download, null, tint = theme.textPrimary)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("IMPORT BACKUP", fontWeight = FontWeight.Black, fontSize = 14.sp, color = theme.textPrimary)
                        Text("Restore your data from a local JSON file", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        when (val state = backupState) {
            is BackupUiState.Working -> {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = theme.accent)
                }
            }
            is BackupUiState.ExportSuccess -> {
                Surface(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    color = Color(0xFF4ADE80).copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF4ADE80))
                        Spacer(Modifier.width(8.dp))
                        Text("Backup exported (${state.itemCount} items).", color = Color(0xFF4ADE80), fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { viewModel.acknowledgeBackupResult() }) { Text("Dismiss") }
                    }
                }
            }
            is BackupUiState.ImportSuccess -> {
                Surface(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    color = Color(0xFF4ADE80).copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF4ADE80))
                            Spacer(Modifier.width(8.dp))
                            Text("Backup imported successfully!", color = Color(0xFF4ADE80), fontWeight = FontWeight.Bold)
                        }
                        Text("Restored ${state.summary.totalCount} items from backup.", fontSize = 11.sp, color = Color.Gray)
                        TextButton(onClick = { viewModel.acknowledgeBackupResult() }) { Text("Dismiss") }
                    }
                }
            }
            is BackupUiState.Failed -> {
                Surface(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    color = Color.Red.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Operation failed", color = Color.Red, fontWeight = FontWeight.Bold)
                        Text(state.message, color = Color.Red, fontSize = 11.sp)
                        TextButton(onClick = { viewModel.acknowledgeBackupResult() }) { Text("Dismiss") }
                    }
                }
            }
            else -> Unit
        }

        if (showClearedToast) {
            LaunchedEffect(Unit) {
                delay(2500)
                showClearedToast = false
            }
            Surface(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                color = Color(0xFF4ADE80).copy(alpha = 0.15f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Cache cleared.", modifier = Modifier.padding(12.dp), color = Color(0xFF4ADE80), fontWeight = FontWeight.Bold)
            }
        }

        // Account Section
        SettingSectionTitle("ACCOUNT")
        Surface(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
                .height(64.dp)
                .then(ThemeBorderModifier())
                .clickable { showLogoutDialog = true },
            color = theme.surface
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp).fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, null, tint = theme.textPrimary)
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text("LOG OUT", fontWeight = FontWeight.Black, fontSize = 14.sp, color = theme.textPrimary)
                    Text("Sign out of Firebase and reset account", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        Surface(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .height(64.dp)
                .then(ThemeBorderModifier())
                .clickable { showChangePasswordDialog = true },
            color = theme.surface
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp).fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Password, null, tint = theme.textPrimary)
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text("CHANGE PASSWORD", fontWeight = FontWeight.Black, fontSize = 14.sp, color = theme.textPrimary)
                    Text("Update your account security", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Danger Zone
        SettingSectionTitle("DANGER ZONE")
        Surface(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
                .border(2.dp, Color.Red, RoundedCornerShape(4.dp))
                .clickable(enabled = wipeAccountState != WipeAccountState.InProgress) { showWipeAccountDialog = true },
            color = theme.surface
        ) {
            Row(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (wipeAccountState == WipeAccountState.InProgress) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.Red,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Default.DeleteForever, null, tint = Color.Red)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text("CLEAR ALL ACCOUNT DATA", fontWeight = FontWeight.Black, fontSize = 14.sp, color = Color.Red)
                    Text(
                        "Permanently wipes your progress and cloud data",
                        fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        when (val state = wipeAccountState) {
            is WipeAccountState.Success -> {
                LaunchedEffect(state) {
                    onLogout()
                }
            }
            is WipeAccountState.Failure -> {
                Surface(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    color = Color(0xFFFF4500).copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Couldn't wipe account.", color = Color(0xFFFF4500), fontWeight = FontWeight.Bold)
                        Text(state.message, color = Color(0xFFFF4500), fontSize = 11.sp)
                        TextButton(onClick = { viewModel.acknowledgeWipeResult() }) { Text("Dismiss") }
                    }
                }
            }
            else -> Unit
        }

        // About Section
        SettingSectionTitle("ABOUT")
        Surface(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
                .then(ThemeBorderModifier()),
            color = theme.surface
        ) {
            Row(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("VERSION", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = theme.textPrimary)
                Text(BuildConfig.VERSION_NAME, fontSize = 13.sp, color = Color.Gray)
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun SettingSectionTitle(title: String) {
    Text(
        text = title,
        color = Color.Black,
        fontSize = 14.sp,
        fontWeight = FontWeight.Black,
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .drawBehind {
                drawRect(Color.Magenta)
            }
            .border(2.dp, Color.Black)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    )
}

@Composable
fun ThemeOptionRow(mode: ThemeMode, isSelected: Boolean, onClick: () -> Unit) {
    val theme = LocalAppTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // `selectable` (not plain `clickable`) is correct for a radio-style, single-
            // selection list — it sets Role.RadioButton and folds `isSelected` into the
            // merged node. The RadioButton below has onClick = null (decoration only) so
            // it doesn't create a second, competing focus stop.
            .selectable(selected = isSelected, onClick = onClick, role = Role.RadioButton)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = when(mode) {
                    ThemeMode.LIGHT -> Icons.Default.LightMode
                    ThemeMode.DARK -> Icons.Default.DarkMode
                    ThemeMode.COMIC -> Icons.Default.BurstMode
                    ThemeMode.MANGA -> Icons.Default.HistoryEdu
                    ThemeMode.FUNK -> Icons.Default.AutoAwesome
                    ThemeMode.DEFAULT -> Icons.Default.RocketLaunch
                    else -> Icons.Default.Palette
                },
                contentDescription = null,
                tint = theme.textPrimary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                mode.name.replace("_", " "),
                fontWeight = FontWeight.Black,
                fontSize = 14.sp,
                color = theme.textPrimary
            )
        }
        RadioButton(
            selected = isSelected,
            onClick = null,
            colors = RadioButtonDefaults.colors(
                selectedColor = Color.Green,
                unselectedColor = Color.Gray
            )
        )
    }
}

@Composable
fun PreferenceToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val theme = LocalAppTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Without this, TalkBack hits the Switch as its own silent stop — "On,
            // Switch" with no label — because nothing connects it to the Title text next
            // to it. Making the whole row toggleable merges title+subtitle+state into one
            // announcement ("Cloud Sync, Sync..., Switch, On") and, as a bonus, gives a
            // much bigger tap target than the bare Switch alone.
            .toggleable(
                value = checked,
                onValueChange = onCheckedChange,
                role = Role.Switch
            )
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = theme.textPrimary)
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(title, fontWeight = FontWeight.Black, fontSize = 14.sp, color = theme.textPrimary)
                Text(subtitle, fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            // Semantics already come from the row's own `toggleable` above; left alone,
            // the Switch would set up its own separate merge boundary and split one
            // setting into two confusing TalkBack stops (see the row modifier above).
            modifier = Modifier.clearAndSetSemantics {},
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Green,
                checkedTrackColor = Color.Green.copy(alpha = 0.3f)
            )
        )
    }
}
