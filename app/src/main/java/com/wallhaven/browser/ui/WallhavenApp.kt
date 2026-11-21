package com.wallhaven.browser.ui

import android.net.Uri
import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Whatshot
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.wallhaven.browser.ui.components.GlassTabBar
import com.wallhaven.browser.ui.components.LocalHazeState
import com.wallhaven.browser.ui.components.LocalPreviewSettings
import com.wallhaven.browser.ui.components.PreviewSettings
import com.wallhaven.browser.ui.components.TabItem
import com.wallhaven.browser.ui.screens.DetailScreen
import com.wallhaven.browser.ui.screens.DownloadsScreen
import com.wallhaven.browser.ui.screens.ExploreScreen
import com.wallhaven.browser.ui.screens.FavoritesScreen
import com.wallhaven.browser.ui.screens.HistoryScreen
import com.wallhaven.browser.ui.screens.LoginScreen
import com.wallhaven.browser.ui.screens.LogScreen
import com.wallhaven.browser.ui.screens.SearchScreen
import com.wallhaven.browser.ui.screens.SettingsScreen
import com.wallhaven.browser.ui.screens.SimilarScreen
import com.wallhaven.browser.data.LocalStore
import com.wallhaven.browser.ui.vm.AccountViewModel
import com.wallhaven.browser.ui.vm.LibraryViewModel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

private const val ROUTE_EXPLORE = "explore"
private const val ROUTE_SEARCH = "search"
private const val ROUTE_FAVORITES = "favorites"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_HISTORY = "history"
private const val ROUTE_LOGS = "logs"
private const val ROUTE_DOWNLOADS = "downloads"
private const val ROUTE_LOGIN = "login"
private const val ROUTE_DETAIL = "detail"

@Composable
fun WallhavenApp() {
    val context = LocalContext.current
    val app = context.applicationContext as Application
    val library: LibraryViewModel = viewModel(factory = LibraryViewModel.factory(app))
    val account: AccountViewModel = viewModel(factory = AccountViewModel.factory(app))
    val previews = remember(app) { PreviewSettings(LocalStore(app)) }
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val baseRoute = currentRoute?.substringBefore('?')
    val hazeState = remember { HazeState() }

    val tabs = listOf(
        TabItem(ROUTE_EXPLORE, "榜单", Icons.Outlined.Whatshot, Icons.Filled.Whatshot),
        TabItem(ROUTE_SEARCH, "搜索", Icons.Outlined.Search, Icons.Filled.Search),
        TabItem(ROUTE_FAVORITES, "收藏", Icons.Outlined.FavoriteBorder, Icons.Filled.Favorite),
        TabItem(ROUTE_SETTINGS, "设置", Icons.Outlined.Settings, Icons.Filled.Settings),
    )
    val showTabs = tabs.any { it.route == baseRoute }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        CompositionLocalProvider(
            LocalHazeState provides hazeState,
            LocalPreviewSettings provides previews,
        ) {
            NavHost(
                navController = navController,
                startDestination = ROUTE_EXPLORE,
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(hazeState),
            ) {
                composable(ROUTE_EXPLORE) {
                    ExploreScreen(
                        library = library,
                        onOpenWallpaper = { id -> navController.navigate("$ROUTE_DETAIL/$id") },
                    )
                }
                composable(ROUTE_SEARCH) {
                    SearchScreen(
                        library = library,
                        account = account,
                        onOpenWallpaper = { id -> navController.navigate("$ROUTE_DETAIL/$id") },
                    )
                }
                composable(
                    route = "search?q={q}",
                    arguments = listOf(
                        navArgument("q") {
                            type = NavType.StringType
                            defaultValue = ""
                        },
                    ),
                ) { entry ->
                    SearchScreen(
                        library = library,
                        account = account,
                        onOpenWallpaper = { id -> navController.navigate("$ROUTE_DETAIL/$id") },
                        initialQuery = entry.arguments?.getString("q").orEmpty(),
                    )
                }
                composable(ROUTE_FAVORITES) {
                    FavoritesScreen(
                        library = library,
                        account = account,
                        onOpenWallpaper = { id -> navController.navigate("$ROUTE_DETAIL/$id") },
                        onOpenTag = { tag ->
                            navController.navigate("search?q=" + Uri.encode("+$tag"))
                        },
                        onOpenLogin = { navController.navigate(ROUTE_LOGIN) },
                    )
                }
                composable(ROUTE_SETTINGS) {
                    SettingsScreen(
                        library = library,
                        account = account,
                        onOpenHistory = { navController.navigate(ROUTE_HISTORY) },
                        onOpenLogs = { navController.navigate(ROUTE_LOGS) },
                        onOpenLogin = { navController.navigate(ROUTE_LOGIN) },
                        onOpenDownloads = { navController.navigate(ROUTE_DOWNLOADS) },
                    )
                }
                composable(ROUTE_DOWNLOADS) {
                    DownloadsScreen(onBack = { navController.popBackStack() })
                }
                composable(ROUTE_LOGIN) {
                    LoginScreen(
                        account = account,
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(
                    route = "similar/{id}",
                    arguments = listOf(navArgument("id") { type = NavType.StringType }),
                ) { entry ->
                    SimilarScreen(
                        seedId = entry.arguments?.getString("id").orEmpty(),
                        library = library,
                        onBack = { navController.popBackStack() },
                        onOpenWallpaper = { id -> navController.navigate("$ROUTE_DETAIL/$id") },
                    )
                }
                composable(ROUTE_LOGS) {
                    LogScreen(onBack = { navController.popBackStack() })
                }
                composable(ROUTE_HISTORY) {
                    HistoryScreen(
                        library = library,
                        onBack = { navController.popBackStack() },
                        onOpenWallpaper = { id -> navController.navigate("$ROUTE_DETAIL/$id") },
                    )
                }
                composable(
                    route = "$ROUTE_DETAIL/{id}",
                    arguments = listOf(navArgument("id") { type = NavType.StringType }),
                ) { entry ->
                DetailScreen(
                    wallpaperId = entry.arguments?.getString("id").orEmpty(),
                    library = library,
                    apiKey = account.apiKey,
                    onBack = { navController.popBackStack() },
                    onOpenTag = { tag ->
                        navController.navigate("search?q=" + Uri.encode("+$tag"))
                    },
                    onOpenDownloads = { navController.navigate(ROUTE_DOWNLOADS) },
                    onOpenSimilar = { id -> navController.navigate("similar/$id") },
                )
                }
            }

            if (showTabs) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(180.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Transparent,
                                    MaterialTheme.colorScheme.background.copy(alpha = 0.72f),
                                    MaterialTheme.colorScheme.background,
                                ),
                            ),
                        ),
                )
                GlassTabBar(
                    tabs = tabs,
                    currentRoute = baseRoute,
                    onSelect = { route ->
                        if (route != baseRoute) {
                            navController.navigate(route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                )
            }
        }
    }
}
