package com.example.watchorderengine.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.*
import androidx.navigation.compose.*
import com.example.watchorderengine.ui.screens.*

sealed class Screen(val route: String) {
    object Home     : Screen("home")
    object Search   : Screen("search")
    object Profile  : Screen("profile")
    object Settings : Screen("settings")
    object Universes : Screen("universes")
    object Timeline  : Screen("timeline/{universeId}") {
        fun route(universeId: String) = "timeline/$universeId"
    }
    object Detail   : Screen("detail/{mediaId}") {
        fun route(mediaId: String) = "detail/$mediaId"
    }
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        enterTransition = { fadeIn(tween(300)) },
        exitTransition = { fadeOut(tween(300)) }
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onMediaClick = { navController.navigate(Screen.Detail.route(it)) },
                onSearchClick = { navController.navigate(Screen.Search.route) },
                onSettingsClick = { navController.navigate(Screen.Settings.route) },
                onUniversesClick = { navController.navigate(Screen.Universes.route) }
            )
        }
        composable(Screen.Search.route) {
            SearchScreen(
                onMediaClick = { navController.navigate(Screen.Detail.route(it)) },
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.Universes.route) {
            com.example.watchorderengine.ui.universe.UniverseListScreen(
                onUniverseClick = { navController.navigate(Screen.Timeline.route(it)) }
            )
        }
        composable(
            route = Screen.Timeline.route,
            arguments = listOf(navArgument("universeId") { type = NavType.StringType })
        ) { backStackEntry ->
            val universeId = backStackEntry.arguments?.getString("universeId") ?: ""
            com.example.watchorderengine.ui.timeline.TimelineScreen(
                universeId = universeId,
                onBack = { navController.popBackStack() },
                onNodeDetail = { navController.navigate(Screen.Detail.route(it)) }
            )
        }
        composable(Screen.Profile.route) {
            ProfileScreenPlaceholder(onSettingsClick = { navController.navigate(Screen.Settings.route) })
        }
        composable(Screen.Settings.route) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(
            route = Screen.Detail.route,
            arguments = listOf(navArgument("mediaId") { type = NavType.StringType })
        ) { backStackEntry ->
            val mediaId = backStackEntry.arguments?.getString("mediaId") ?: ""
            MediaDetailScreen(mediaId = mediaId, onBack = { navController.popBackStack() })
        }
    }
}

@Composable fun ProfileScreenPlaceholder(onSettingsClick: () -> Unit) { 
    Column {
        Text("Profile Screen Placeholder")
        Button(onClick = onSettingsClick) { Text("Settings") }
    }
}
