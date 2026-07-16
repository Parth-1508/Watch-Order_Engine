package com.example.watchorderengine.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.*
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.example.watchorderengine.ui.screens.*
import com.example.watchorderengine.ui.screens.home.HomeScreenWrapper
import com.example.watchorderengine.ui.theme.LocalAppTheme
import com.example.watchorderengine.ui.timeline.TimelineScreen
import com.example.watchorderengine.util.ConnectivityObserver
import kotlinx.coroutines.launch

// ─── Navigation helper ───
private fun safeMediaId(raw: String): String =
    if (raw.startsWith("tmdb_") || raw.startsWith("anilist_")) raw else "tmdb_$raw"

sealed class Screen(val route: String) {
    object Opening            : Screen("opening")
    object Login              : Screen("login")
    object TasteProfileSetup  : Screen("taste_profile_setup")
    object Home               : Screen("home")
    object Discovery          : Screen("discovery")
    object Search             : Screen("search")
    object Graph              : Screen("graph")
    object Community          : Screen("community")
    object Profile            : Screen("profile")
    object EditProfile        : Screen("edit_profile")
    object Settings           : Screen("settings")
    object PublicProfile      : Screen("public_profile/{userId}") {
        fun route(userId: String) = "public_profile/${java.net.URLEncoder.encode(userId, "UTF-8")}"
    }
    object ImportList         : Screen("import_list")
    object ManualReset        : Screen("manual_reset")
    object ResetSuccess       : Screen("reset_success")
    object ResetPassword      : Screen("reset-password?oobCode={oobCode}&all={all}") {
        fun route(oobCode: String) = "reset-password?oobCode=$oobCode"
    }
    object Detail    : Screen("detail/{mediaId}") {
        fun route(mediaId: String) = "detail/$mediaId"
    }
    object CharacterDetail : Screen("character/{tmdbPersonId}/{characterName}/{showTitle}/{isAnime}/{anilistId}") {
        fun route(tmdbPersonId: Int, characterName: String, showTitle: String, isAnime: Boolean, anilistId: Int? = null): String {
            val encodedName = java.net.URLEncoder.encode(characterName, "UTF-8")
            val encodedTitle = java.net.URLEncoder.encode(showTitle, "UTF-8")
            return "character/$tmdbPersonId/$encodedName/$encodedTitle/$isAnime/${anilistId ?: -1}"
        }
    }
}

private const val TAB_DURATION_MS   = 180
private const val SLIDE_OFFSET_EXIT = 0.30f

private val TAB_ROUTES = setOf(
    Screen.Home.route,
    Screen.Discovery.route,
    Screen.Search.route,
    Screen.Graph.route,
    Screen.Community.route,
    Screen.Profile.route
)

private fun AnimatedContentTransitionScope<NavBackStackEntry>.isBothTabs(): Boolean {
    val from = initialState.destination.route
    val to   = targetState.destination.route
    return from in TAB_ROUTES && to in TAB_ROUTES
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.appEnterTransition()
    : EnterTransition =
    if (isBothTabs()) {
        fadeIn(tween(TAB_DURATION_MS))
    } else {
        slideInHorizontally(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness    = Spring.StiffnessMediumLow
            ),
            initialOffsetX = { fullWidth -> fullWidth }
        ) + fadeIn(tween(TAB_DURATION_MS))
    }

private fun AnimatedContentTransitionScope<NavBackStackEntry>.appExitTransition()
    : ExitTransition =
    if (isBothTabs()) {
        fadeOut(tween(TAB_DURATION_MS))
    } else {
        slideOutHorizontally(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness    = Spring.StiffnessMediumLow
            ),
            targetOffsetX = { fullWidth -> -(fullWidth * SLIDE_OFFSET_EXIT).toInt() }
        ) + fadeOut(tween(TAB_DURATION_MS))
    }

private fun AnimatedContentTransitionScope<NavBackStackEntry>.appPopEnterTransition()
    : EnterTransition =
    if (isBothTabs()) {
        fadeIn(tween(TAB_DURATION_MS))
    } else {
        slideInHorizontally(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness    = Spring.StiffnessMediumLow
            ),
            initialOffsetX = { fullWidth -> -(fullWidth * SLIDE_OFFSET_EXIT).toInt() }
        ) + fadeIn(tween(TAB_DURATION_MS))
    }

private fun AnimatedContentTransitionScope<NavBackStackEntry>.appPopExitTransition()
    : ExitTransition =
    if (isBothTabs()) {
        fadeOut(tween(TAB_DURATION_MS))
    } else {
        slideOutHorizontally(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness    = Spring.StiffnessMediumLow
            ),
            targetOffsetX = { fullWidth -> fullWidth }
        ) + fadeOut(tween(TAB_DURATION_MS))
    }

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val navViewModel: com.example.watchorderengine.ui.viewmodel.NavViewModel = hiltViewModel()
    val scope = rememberCoroutineScope()

    val navigateTopLevel: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    val showBottomBar = currentRoute in listOf(
        Screen.Home.route,
        Screen.Discovery.route,
        Screen.Search.route,
        Screen.Graph.route,
        Screen.Community.route,
        Screen.Profile.route
    )

    val connectivityStatus by navViewModel.connectivityStatus.collectAsState()

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                AppBottomBar(
                    currentRoute = currentRoute,
                    onNavigate = navigateTopLevel
                )
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (connectivityStatus != ConnectivityObserver.Status.Available) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFFFF4B4B)
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 4.dp, horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudOff,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Offline Mode",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            NavHost(
                navController    = navController,
                startDestination = Screen.Opening.route,
                enterTransition  = { appEnterTransition() },
                exitTransition   = { appExitTransition() },
                popEnterTransition  = { appPopEnterTransition() },
                popExitTransition   = { appPopExitTransition() },
                modifier = Modifier.weight(1f)
            ) {
                composable(Screen.Opening.route) {
                    OpeningScreen(
                    onEnter = {
                        scope.launch {
                            val target = navViewModel.getInitialRoute()
                            if (target != "login") {
                                navViewModel.syncDataOnLogin()
                            }
                            navController.navigate(target) {
                                popUpTo(Screen.Opening.route) { inclusive = true }
                            }
                        }
                    },
                    onSkip = {
                        navController.navigate(Screen.Login.route) {
                            popUpTo(Screen.Opening.route) { inclusive = true }
                        }
                    }
                )
                }

                composable(Screen.Login.route) {
                    LoginScreen(
                        onLoginSuccess = {
                            scope.launch {
                                navViewModel.syncDataOnLogin()
                                val target = navViewModel.getInitialRoute()
                                navController.navigate(target) {
                                    popUpTo(Screen.Login.route) { inclusive = true }
                                }
                            }
                        },
                        onManualResetClick = {
                            navController.navigate(Screen.ManualReset.route)
                        },
                        onCodeDetected = { code ->
                            navController.navigate(Screen.ResetPassword.route(code))
                        }
                    )
                }

                composable(Screen.TasteProfileSetup.route) {
                    val onboardingViewModel: com.example.watchorderengine.ui.viewmodel.TasteProfileViewModel = hiltViewModel()
                    TasteProfileSetupScreen(
                        onComplete = { genres ->
                            onboardingViewModel.saveSelectedGenres(genres)
                            navController.navigate(Screen.Home.route) {
                                popUpTo(Screen.TasteProfileSetup.route) { inclusive = true }
                            }
                        },
                        onSkip = {
                            navController.navigate(Screen.Home.route) {
                                popUpTo(Screen.TasteProfileSetup.route) { inclusive = true }
                            }
                        }
                    )
                }

                composable(Screen.Home.route) {
                    HomeScreenWrapper(
                        onMediaClick    = { navController.navigate(Screen.Detail.route(safeMediaId(it))) },
                        onSearchClick   = { navigateTopLevel(Screen.Search.route) },
                        onSettingsClick = { navigateTopLevel(Screen.Settings.route) },
                        onProfileClick  = { navigateTopLevel(Screen.Profile.route) }
                    )
                }

                composable(Screen.Search.route) {
                    SearchScreen(
                        onMediaClick = { navController.navigate(Screen.Detail.route(safeMediaId(it))) },
                        onBack       = { navController.popBackStack() }
                    )
                }

                composable(Screen.Discovery.route) {
                    DiscoveryScreen(
                        onMediaClick = { navController.navigate(Screen.Detail.route(safeMediaId(it))) },
                        onBack       = { navController.popBackStack() }
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
                    route     = "timeline/{universeId}",
                    arguments = listOf(navArgument("universeId") { type = NavType.StringType })
                ) { backStackEntry ->
                    val universeId = backStackEntry.arguments?.getString("universeId") ?: ""
                    TimelineScreen(
                        universeId  = universeId,
                        onBack      = { navController.popBackStack() },
                        onNodeDetail = { navController.navigate(Screen.Detail.route(safeMediaId(it))) }
                    )
                }

                composable(Screen.Community.route) {
                    CommunityScreen(
                        onMediaClick = { navController.navigate(Screen.Detail.route(safeMediaId(it))) },
                        onAuthorClick = { userId -> navController.navigate(Screen.PublicProfile.route(userId)) }
                    )
                }

                composable(Screen.Profile.route) {
                    ProfileScreen(
                        onMediaClick = { navController.navigate(Screen.Detail.route(safeMediaId(it))) },
                        onRateMediaClick = { navController.navigate(Screen.Discovery.route) },
                        onImportClick = { navController.navigate(Screen.ImportList.route) },
                        onEditProfileClick = { navController.navigate(Screen.EditProfile.route) }
                    )
                }

                composable(Screen.EditProfile.route) {
                    EditProfileScreen(
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(
                    route     = Screen.PublicProfile.route,
                    arguments = listOf(navArgument("userId") { type = NavType.StringType })
                ) {
                    PublicProfileScreen(
                        onBack       = { navController.popBackStack() },
                        onMediaClick = { navController.navigate(Screen.Detail.route(safeMediaId(it))) }
                    )
                }

                composable(Screen.Settings.route) {
                    SettingsScreen(
                        onBack = { navController.popBackStack() },
                        onLogout = {
                            navController.navigate(Screen.Opening.route) {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    )
                }

                composable(Screen.ImportList.route) {
                    ImportListScreen(onBack = { navController.popBackStack() })
                }

                composable(Screen.ManualReset.route) {
                    ManualResetScreen(
                        onCodeExtracted = { code ->
                            navController.navigate(Screen.ResetPassword.route(code))
                        },
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(
                    route = Screen.ResetSuccess.route,
                    deepLinks = listOf(
                        navDeepLink {
                            uriPattern = "https://watch-order-engine.firebaseapp.com/success"
                        },
                        navDeepLink {
                            uriPattern = "https://watch-order-engine.web.app/success"
                        }
                    )
                ) {
                    ResetSuccessScreen(
                        onDone = {
                            navController.navigate(Screen.Login.route) {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    )
                }

                composable(
                    route = Screen.ResetPassword.route,
                    arguments = listOf(
                        navArgument("oobCode") { type = NavType.StringType; defaultValue = "" },
                        navArgument("all") { type = NavType.StringType; defaultValue = "" }
                    ),
                    deepLinks = listOf(
                        navDeepLink {
                            uriPattern = "https://watch-order-engine.firebaseapp.com/__/auth/action?{all}"
                        },
                        navDeepLink {
                            uriPattern = "https://watch-order-engine.web.app/__/auth/action?{all}"
                        }
                    )
                ) { backStackEntry ->
                    val oobCodeArg = backStackEntry.arguments?.getString("oobCode") ?: ""
                    val allArg = backStackEntry.arguments?.getString("all") ?: ""
                    
                    val finalCode = if (oobCodeArg.isNotBlank()) oobCodeArg else {
                        // Safe extraction: Decode nested 'link' parameter with null check
                        val decodedUrl = try {
                            if (allArg.contains("link=")) {
                                allArg.substringAfter("link=").substringBefore("&")
                                    .let { java.net.URLDecoder.decode(it, "UTF-8") }
                            } else allArg
                        } catch (e: Exception) { allArg }
                        
                        Regex("oobCode(?:=|%3D)([^&%\\s]+)").find(decodedUrl ?: "")?.groupValues?.get(1) ?: ""
                    }
                    
                    ResetPasswordScreen(
                        oobCode = finalCode,
                        onSuccess = {
                            navController.navigate(Screen.Login.route) {
                                popUpTo(0) { inclusive = true }
                            }
                        },
                        onBack = {
                            navController.popBackStack()
                        }
                    )
                }

                composable(
                    route     = Screen.Detail.route,
                    arguments = listOf(navArgument("mediaId") { type = NavType.StringType })
                ) { backStackEntry ->
                    val mediaId = backStackEntry.arguments?.getString("mediaId") ?: ""
                    MediaDetailScreen(
                        mediaId         = safeMediaId(mediaId),
                        onBack          = { navController.popBackStack() },
                        onUniverseClick = { universeId ->
                            navController.navigate("timeline/$universeId")
                        },
                        onCharacterClick = { tmdbPersonId, characterName, showTitle, isAnime, anilistId ->
                            navController.navigate(
                                Screen.CharacterDetail.route(tmdbPersonId, characterName, showTitle, isAnime, anilistId)
                            )
                        },
                        onAuthorClick = { userId ->
                            navController.navigate(Screen.PublicProfile.route(userId))
                        }
                    )
                }

                composable(
                    route = Screen.CharacterDetail.route,
                    arguments = listOf(
                        navArgument("tmdbPersonId")   { type = NavType.IntType },
                        navArgument("characterName")  { type = NavType.StringType },
                        navArgument("showTitle")      { type = NavType.StringType },
                        navArgument("isAnime")        { type = NavType.BoolType },
                        navArgument("anilistId")      { type = NavType.IntType; defaultValue = -1 }
                    )
                ) { backStackEntry ->
                    val args = backStackEntry.arguments
                    CharacterDetailScreen(
                        tmdbPersonId  = args?.getInt("tmdbPersonId") ?: 0,
                        characterName = java.net.URLDecoder.decode(args?.getString("characterName") ?: "", "UTF-8"),
                        showTitle     = java.net.URLDecoder.decode(args?.getString("showTitle") ?: "", "UTF-8"),
                        isAnime       = args?.getBoolean("isAnime") ?: false,
                        anilistId     = args?.getInt("anilistId")?.takeIf { it > 0 },
                        onBack        = { navController.popBackStack() },
                        onMediaClick  = { mediaId ->
                            navController.navigate(Screen.Detail.route(safeMediaId(mediaId)))
                        }
                    )
                }
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
                    label      = "Home",
                    icon       = if (currentRoute == Screen.Home.route) Icons.Filled.Home else Icons.Outlined.Home,
                    isSelected = currentRoute == Screen.Home.route,
                    onClick    = { onNavigate(Screen.Home.route) }
                )
                BottomNavItem(
                    label      = "Discover",
                    icon       = if (currentRoute == Screen.Discovery.route) Icons.Filled.Explore else Icons.Outlined.Explore,
                    isSelected = currentRoute == Screen.Discovery.route,
                    onClick    = { onNavigate(Screen.Discovery.route) }
                )
                BottomNavItem(
                    label      = "Search",
                    icon       = if (currentRoute == Screen.Search.route) Icons.Filled.Search else Icons.Outlined.Search,
                    isSelected = currentRoute == Screen.Search.route,
                    onClick    = { onNavigate(Screen.Search.route) }
                )
                BottomNavItem(
                    label      = "Graph",
                    icon       = if (currentRoute == Screen.Graph.route) Icons.Filled.AccountTree else Icons.Outlined.AccountTree,
                    isSelected = currentRoute == Screen.Graph.route,
                    onClick    = { onNavigate(Screen.Graph.route) }
                )
                BottomNavItem(
                    label      = "Community",
                    icon       = if (currentRoute == Screen.Community.route) Icons.Filled.People else Icons.Outlined.People,
                    isSelected = currentRoute == Screen.Community.route,
                    onClick    = { onNavigate(Screen.Community.route) }
                )
                BottomNavItem(
                    label      = "Profile",
                    icon       = if (currentRoute == Screen.Profile.route) Icons.Filled.Person else Icons.Outlined.Person,
                    isSelected = currentRoute == Screen.Profile.route,
                    onClick    = { onNavigate(Screen.Profile.route) }
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
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector    = icon,
            contentDescription = label,
            tint     = if (isSelected) theme.accent else Color.Gray,
            modifier = Modifier.size(22.dp)
        )
        Text(
            text       = label,
            fontSize   = 9.sp,
            color      = if (isSelected) theme.accent else Color.Gray,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            maxLines   = 1
        )
    }
}
