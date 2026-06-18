package com.example.watchorderengine.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.*
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.example.watchorderengine.ui.screens.*
import com.example.watchorderengine.ui.screens.home.HomeScreenWrapper
import com.example.watchorderengine.ui.theme.LocalAppTheme
import com.example.watchorderengine.ui.timeline.TimelineScreen

sealed class Screen(val route: String) {
    object Opening   : Screen("opening")
    object Home      : Screen("home")
    object Discovery : Screen("discovery")
    object Search    : Screen("search")
    object Graph     : Screen("graph")
    object Community : Screen("community")
    object Profile   : Screen("profile")
    object Settings  : Screen("settings")
    object Detail    : Screen("detail/{mediaId}") {
        fun route(mediaId: String) = "detail/$mediaId"
    }
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val showBottomBar = currentRoute in listOf(
        Screen.Home.route,
        Screen.Discovery.route,
        Screen.Graph.route,
        Screen.Community.route,
        Screen.Profile.route
    )

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                AppBottomBar(
                    currentRoute = currentRoute,
                    onNavigate = { route ->
                        navController.navigate(route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Opening.route,
            enterTransition = { fadeIn(tween(300)) },
            exitTransition = { fadeOut(tween(300)) },
            modifier = Modifier.padding(padding)
        ) {
            composable(Screen.Opening.route) {
                OpeningScreen(
                    onEnter = { navController.navigate(Screen.Home.route) },
                    onSkip = { navController.navigate(Screen.Home.route) }
                )
            }
            composable(Screen.Home.route) {
                HomeScreenWrapper(
                    onMediaClick = { navController.navigate(Screen.Detail.route(it)) },
                    onSettingsClick = { navController.navigate(Screen.Settings.route) }
                )
            }
            composable(Screen.Discovery.route) {
                DiscoveryScreen(
                    onMediaClick = { navController.navigate(Screen.Detail.route(it)) },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.Graph.route) {
                com.example.watchorderengine.ui.universe.UniverseListScreen(
                    onUniverseClick = { universeId -> 
                        navController.navigate("timeline/$universeId")
                    }
                )
            }
            composable(
                route = "timeline/{universeId}",
                arguments = listOf(navArgument("universeId") { type = NavType.StringType })
            ) { backStackEntry ->
                val universeId = backStackEntry.arguments?.getString("universeId") ?: ""
                TimelineScreen(
                    universeId = universeId,
                    onBack = { navController.popBackStack() },
                    onNodeDetail = { navController.navigate(Screen.Detail.route(it)) }
                )
            }
            composable(Screen.Community.route) {
                CommunityScreen()
            }
            composable(Screen.Profile.route) {
                ProfileScreen(onSettingsClick = { navController.navigate(Screen.Settings.route) })
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
}

@Composable
fun AppBottomBar(currentRoute: String?, onNavigate: (String) -> Unit) {
    val theme = LocalAppTheme.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .background(theme.background),
        color = theme.background
    ) {
        Column {
            HorizontalDivider(color = Color.White.copy(alpha = 0.1f), thickness = 1.dp)
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BottomNavItem(
                    label = "Home",
                    icon = if (currentRoute == Screen.Home.route) Icons.Filled.Home else Icons.Outlined.Home,
                    isSelected = currentRoute == Screen.Home.route,
                    onClick = { onNavigate(Screen.Home.route) }
                )
                BottomNavItem(
                    label = "Discover",
                    icon = if (currentRoute == Screen.Discovery.route) Icons.Filled.Explore else Icons.Outlined.Explore,
                    isSelected = currentRoute == Screen.Discovery.route,
                    onClick = { onNavigate(Screen.Discovery.route) }
                )
                BottomNavItem(
                    label = "Graph",
                    icon = if (currentRoute == Screen.Graph.route) Icons.Filled.AccountTree else Icons.Outlined.AccountTree,
                    isSelected = currentRoute == Screen.Graph.route,
                    onClick = { onNavigate(Screen.Graph.route) }
                )
                BottomNavItem(
                    label = "Community",
                    icon = if (currentRoute == Screen.Community.route) Icons.Filled.People else Icons.Outlined.People,
                    isSelected = currentRoute == Screen.Community.route,
                    onClick = { onNavigate(Screen.Community.route) }
                )
                BottomNavItem(
                    label = "Profile",
                    icon = if (currentRoute == Screen.Profile.route) Icons.Filled.Person else Icons.Outlined.Person,
                    isSelected = currentRoute == Screen.Profile.route,
                    onClick = { onNavigate(Screen.Profile.route) }
                )
            }
        }
    }
}

@Composable
fun BottomNavItem(label: String, icon: ImageVector, isSelected: Boolean, onClick: () -> Unit) {
    val theme = LocalAppTheme.current
    Column(
        modifier = Modifier
            .clickable { onClick() }
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isSelected) theme.accent else Color.Gray,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = label,
            fontSize = 10.sp,
            color = if (isSelected) theme.accent else Color.Gray,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
fun CommunityScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Community Screen - Coming Soon", color = Color.Gray)
    }
}
