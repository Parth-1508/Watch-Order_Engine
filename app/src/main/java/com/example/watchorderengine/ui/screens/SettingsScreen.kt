package com.example.watchorderengine.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.watchorderengine.BuildConfig
import com.example.watchorderengine.data.prefs.ThemeMode
import com.example.watchorderengine.ui.screens.home.ThemeBorderModifier
import com.example.watchorderengine.ui.theme.LocalAppTheme
import com.example.watchorderengine.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.delay

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val theme = LocalAppTheme.current
    val scrollState = rememberScrollState()
    val currentThemeMode by viewModel.themeMode.collectAsState()
    val hideFiller by viewModel.hideFiller.collectAsState()
    var showClearCacheDialog by remember { mutableStateOf(false) }
    var showClearedToast by remember { mutableStateOf(false) }

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
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = theme.textPrimary)
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
                ThemeMode.entries.filter { it != ThemeMode.SYSTEM }.forEach { mode ->
                    ThemeOptionRow(
                        mode = mode,
                        isSelected = currentThemeMode == mode,
                        onClick = { viewModel.setThemeMode(mode) }
                    )
                    if (mode != ThemeMode.entries.last()) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = theme.textPrimary.copy(alpha = 0.05f)
                        )
                    }
                }
            }
        }

        // Preferences Section — Spoiler Wall is the only toggle here with a
        // real system behind it (filters filler episodes in Detail's
        // episode list). The old Notifications toggle was removed: there is
        // no notification system anywhere in this app (no push, no
        // background work) to actually enable/disable, so it could only
        // ever be a switch that lied about doing something.
        SettingSectionTitle("PREFERENCES")
        Surface(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
                .then(ThemeBorderModifier()),
            color = theme.surface
        ) {
            PreferenceToggleRow(
                icon = Icons.Default.Shield,
                title = "SPOILER WALL",
                subtitle = if (hideFiller) "HIDING FILLER + UNSEEN EPISODES" else "HIDING UNSEEN EPISODES ONLY",
                checked = hideFiller,
                onCheckedChange = { viewModel.setHideFiller(it) }
            )
        }

        // Account Section — informational, since this app uses anonymous
        // Firebase auth with no email/password system. There is
        // deliberately no "Log Out" button: signing out of an anonymous
        // account generates a brand-new anonymous UID on next launch and
        // permanently orphans every bit of Firestore watch progress under
        // the old UID, with no way back in. A button that looks routine but
        // is actually irreversible data loss is worse than no button.
        SettingSectionTitle("ACCOUNT")
        Surface(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
                .then(ThemeBorderModifier()),
            color = theme.surface
        ) {
            Row(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.CloudDone, null, tint = theme.textPrimary)
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text("SYNCED TO THIS DEVICE", fontWeight = FontWeight.Black, fontSize = 14.sp, color = theme.textPrimary)
                    Text(
                        "Your progress is tied to this install — there's no account to log into elsewhere yet.",
                        fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Data Section
        SettingSectionTitle("DATA")
        Surface(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
                .height(64.dp)
                .then(ThemeBorderModifier())
                .clickable { showClearCacheDialog = true },
            color = theme.surface
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp).fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.DeleteSweep, null, tint = theme.textPrimary)
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text("CLEAR LOCAL CACHE", fontWeight = FontWeight.Black, fontSize = 14.sp, color = theme.textPrimary)
                    Text("Cached TMDB metadata only — progress is safe", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                }
            }
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
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = when(mode) {
                    ThemeMode.LIGHT -> Icons.Default.LightMode
                    ThemeMode.DARK -> Icons.Default.DarkMode
                    else -> Icons.Default.AutoAwesome
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
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = theme.textPrimary)
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(title, fontWeight = FontWeight.Black, fontSize = 14.sp, color = theme.textPrimary)
                Text(subtitle, fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Green,
                checkedTrackColor = Color.Green.copy(alpha = 0.3f)
            )
        )
    }
}
