package com.example.watchorderengine.ui.screens

import androidx.compose.runtime.*
import com.example.watchorderengine.data.model.SyncProgress
import com.example.watchorderengine.ui.components.FactLoadingView

@Composable
fun SyncingScreen(
    syncProgress: SyncProgress?
) {
    FactLoadingView(
        stage = syncProgress?.stage,
        progress = syncProgress?.progress
    )
}
