package com.example.rssreader.ui

import android.os.SystemClock
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavType
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.rssreader.debug.DebugLogger
import com.example.rssreader.data.repository.FeedRepository
import com.example.rssreader.data.settings.AppPreferences
import com.example.rssreader.data.settings.SettingsRepository
import com.example.rssreader.sync.RefreshScheduler
import com.example.rssreader.ui.navigation.Screen
import com.example.rssreader.ui.screens.ArticleListScreen
import com.example.rssreader.ui.screens.ArticleReaderScreen
import com.example.rssreader.ui.screens.FeedConfigScreen
import com.example.rssreader.ui.screens.HomeScreen
import com.example.rssreader.ui.screens.SearchScreen
import com.example.rssreader.ui.screens.SettingsRouteScreen
import com.example.rssreader.ui.theme.RssReaderTheme

@Composable
fun RssReaderApp(
    repository: FeedRepository,
    settingsRepository: SettingsRepository,
    refreshScheduler: RefreshScheduler
) {
    val navController = rememberNavController()
    val settings by settingsRepository.settings.collectAsState(initial = null)
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route
    val latestCurrentRoute by rememberUpdatedState(currentRoute)
    var lastBackgroundedAtElapsedMs by remember { mutableLongStateOf(0L) }

    LaunchedEffect(settings?.refreshIntervalMinutes, settings?.refreshOnlyOnWifi) {
        settings?.let { loadedSettings ->
            refreshScheduler.sync(loadedSettings)
        }
    }

    LaunchedEffect(currentRoute) {
        DebugLogger.i("RssReaderApp", "Route aktiv: ${currentRoute ?: "(none)"}")
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    lastBackgroundedAtElapsedMs = SystemClock.elapsedRealtime()
                    DebugLogger.i(
                        "RssReaderApp",
                        "App im Hintergrund: currentRoute=${latestCurrentRoute ?: "(none)"}"
                    )
                }

                Lifecycle.Event.ON_START -> {
                    DebugLogger.i(
                        "RssReaderApp",
                        "App im Vordergrund: currentRoute=${latestCurrentRoute ?: "(none)"}, lastBackgroundedAtElapsedMs=$lastBackgroundedAtElapsedMs"
                    )
                    if (
                        latestCurrentRoute == Screen.Reader.route &&
                        shouldResetReaderAfterInactivity(
                            lastBackgroundedAtElapsedMs = lastBackgroundedAtElapsedMs,
                            nowElapsedMs = SystemClock.elapsedRealtime()
                        )
                    ) {
                        DebugLogger.i(
                            "RssReaderApp",
                            "Reader-Inaktivitaet erkannt, springe zur Hauptseite zurueck"
                        )
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Home.route) { inclusive = false }
                            launchSingleTop = true
                        }
                    }
                }

                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    RssReaderTheme(themeMode = settings?.themeMode ?: AppPreferences().themeMode) {
        Surface {
            NavHost(navController = navController, startDestination = Screen.Home.route) {
                composable(Screen.Home.route) {
                    val loadedSettings = settings ?: AppPreferences()
                    HomeScreen(
                        repository = repository,
                        settings = loadedSettings,
                        settingsLoaded = settings != null,
                        onOpenFeed = { feedId ->
                            navController.navigate(Screen.ArticleList.create(feedId))
                        },
                        onAddFeed = {
                            navController.navigate(Screen.AddFeed.route)
                        },
                        onEditFeed = { feedId ->
                            navController.navigate(Screen.EditFeed.create(feedId))
                        },
                        onOpenSearch = {
                            navController.navigate(Screen.Search.route)
                        },
                        onOpenSettings = {
                            navController.navigate(Screen.Settings.route)
                        }
                    )
                }
                composable(Screen.Search.route) {
                    SearchScreen(
                        repository = repository,
                        onOpenArticle = { articleId ->
                            navController.navigate(Screen.Reader.create(articleId))
                        },
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(Screen.Settings.route) {
                    val loadedSettings = settings ?: AppPreferences()
                    SettingsRouteScreen(
                        repository = repository,
                        settings = loadedSettings,
                        settingsRepository = settingsRepository,
                        onOpenOverview = {
                            navController.navigate(Screen.Home.route) {
                                popUpTo(Screen.Home.route) { inclusive = false }
                                launchSingleTop = true
                            }
                        }
                    )
                }
                composable(Screen.AddFeed.route) {
                    FeedConfigScreen(
                        repository = repository,
                        feedId = null,
                        onBack = { navController.popBackStack() },
                        onDone = { navController.popBackStack() }
                    )
                }
                composable(
                    route = Screen.EditFeed.route,
                    arguments = listOf(navArgument("feedId") { type = NavType.LongType })
                ) { backStackEntry ->
                    val feedId = backStackEntry.arguments?.getLong("feedId") ?: return@composable
                    FeedConfigScreen(
                        repository = repository,
                        feedId = feedId,
                        onBack = { navController.popBackStack() },
                        onDone = { navController.popBackStack() }
                    )
                }
                composable(
                    route = Screen.ArticleList.route,
                    arguments = listOf(navArgument("feedId") { type = NavType.LongType })
                ) { backStackEntry ->
                    val feedId = backStackEntry.arguments?.getLong("feedId") ?: return@composable
                    ArticleListScreen(
                        feedId = feedId,
                        repository = repository,
                        settings = settings ?: AppPreferences(),
                        entrySortOrder = settings?.entrySortOrder ?: AppPreferences().entrySortOrder,
                        onOpenArticle = { articleId ->
                            navController.navigate(Screen.Reader.create(articleId))
                        },
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(
                    route = Screen.Reader.route,
                    arguments = listOf(navArgument("articleId") { type = NavType.LongType })
                ) { backStackEntry ->
                    val articleId = backStackEntry.arguments?.getLong("articleId") ?: return@composable
                    ArticleReaderScreen(
                        articleId = articleId,
                        repository = repository,
                        showImages = settings?.showImages ?: true,
                        articleBodyTextSizeOffset = settings?.articleBodyTextSizeOffset ?: 0,
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}

internal fun shouldResetReaderAfterInactivity(
    lastBackgroundedAtElapsedMs: Long,
    nowElapsedMs: Long,
    timeoutMs: Long = READER_INACTIVITY_RESET_TIMEOUT_MS
): Boolean {
    if (lastBackgroundedAtElapsedMs <= 0L || nowElapsedMs <= lastBackgroundedAtElapsedMs) {
        return false
    }
    return nowElapsedMs - lastBackgroundedAtElapsedMs >= timeoutMs
}

internal const val READER_INACTIVITY_RESET_TIMEOUT_MS = 15L * 60L * 1000L


