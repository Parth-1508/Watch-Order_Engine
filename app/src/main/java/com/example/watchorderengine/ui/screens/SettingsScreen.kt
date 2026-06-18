package com.example.watchorderengine.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.watchorderengine.data.prefs.ThemeMode
import com.example.watchorderengine.ui.screens.home.ThemeBorderModifier
import com.example.watchorderengine.ui.theme.LocalAppTheme
import com.example.watchorderengine.ui.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val theme = LocalAppTheme.current
    val scrollState = rememberScrollState()
    val currentThemeMode by viewModel.themeMode.collectAsState()

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
                    icon = Icons.Default.Notifications,
                    title = "NOTIFICATIONS",
                    subtitle = "ENABLED",
                    checked = true
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = theme.textPrimary.copy(alpha = 0.05f)
                )
                PreferenceToggleRow(
                    icon = Icons.Default.Shield,
                    title = "SPOILER WALL",
                    subtitle = "HIDING UNSEEN EPISODES",
                    checked = true
                )
            }
        }

        // Sync Devices
        Surface(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
                .height(64.dp)
                .then(ThemeBorderModifier())
                .clickable { },
            color = theme.surface
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Smartphone, null, tint = theme.textPrimary)
                Spacer(modifier = Modifier.width(16.dp))
                Text("SYNC DEVICES", fontWeight = FontWeight.Bold, color = theme.textPrimary)
            }
        }

        // Logout
        Button(
            onClick = { },
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
                .height(64.dp)
                .then(ThemeBorderModifier()),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4500)),
            shape = RoundedCornerShape(0.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.Logout, null, tint = Color.White)
                Spacer(modifier = Modifier.width(12.dp))
                Text("LOG OUT", fontWeight = FontWeight.Black, color = Color.White, letterSpacing = 1.sp)
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
fun PreferenceToggleRow(icon: ImageVector, title: String, subtitle: String, checked: Boolean) {
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
            onCheckedChange = { },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Green,
                checkedTrackColor = Color.Green.copy(alpha = 0.3f)
            )
        )
    }
}
