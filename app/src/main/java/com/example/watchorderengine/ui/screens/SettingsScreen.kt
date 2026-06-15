package com.example.watchorderengine.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.watchorderengine.data.prefs.LayoutStyle
import com.example.watchorderengine.data.prefs.ThemeMode
import com.example.watchorderengine.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val themeMode by viewModel.themeMode.collectAsState()
    val layoutStyle by viewModel.layoutStyle.collectAsState()
    val hideFiller by viewModel.hideFiller.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Theme Section
            SettingsSection(title = "Appearance") {
                SettingsPreferenceItem(
                    title = "Theme",
                    subtitle = themeMode.name.lowercase().replaceFirstChar { it.uppercase() }
                ) {
                    var showDialog by remember { mutableStateOf(false) }
                    if (showDialog) {
                        ThemeSelectionDialog(
                            currentMode = themeMode,
                            onDismiss = { showDialog = false },
                            onSelect = { 
                                viewModel.setThemeMode(it)
                                showDialog = false
                            }
                        )
                    }
                    Box(Modifier.clickable { showDialog = true }) {
                        Text(themeMode.name, color = MaterialTheme.colorScheme.primary)
                    }
                }

                SettingsPreferenceItem(
                    title = "Layout Style",
                    subtitle = if (layoutStyle == LayoutStyle.COMFORT) "Comfort Grid (Detailed)" else "Compact Grid (Dense)"
                ) {
                    Switch(
                        checked = layoutStyle == LayoutStyle.COMPACT,
                        onCheckedChange = { viewModel.setLayoutStyle(if (it) LayoutStyle.COMPACT else LayoutStyle.COMFORT) }
                    )
                }
            }

            // Data Section
            SettingsSection(title = "Data & Content") {
                SettingsPreferenceItem(
                    title = "Hide Filler Episodes",
                    subtitle = "Automatically collapse filler content in timelines"
                ) {
                    Switch(
                        checked = hideFiller,
                        onCheckedChange = { viewModel.setHideFiller(it) }
                    )
                }

                Button(
                    onClick = { viewModel.clearCache() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Clear Local Cache")
                }
            }
        }
    }
}

@Composable
fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                content()
            }
        }
    }
}

@Composable
fun SettingsPreferenceItem(
    title: String,
    subtitle: String? = null,
    action: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        action()
    }
}

@Composable
fun ThemeSelectionDialog(
    currentMode: ThemeMode,
    onDismiss: () -> Unit,
    onSelect: (ThemeMode) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Theme") },
        text = {
            Column {
                ThemeMode.entries.forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(mode) }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = mode == currentMode, onClick = null)
                        Spacer(Modifier.width(8.dp))
                        Text(mode.name.lowercase().replaceFirstChar { it.uppercase() })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
