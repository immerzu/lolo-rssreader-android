package com.example.rssreader.ui

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.rssreader.data.repository.FeedRepository
import com.example.rssreader.data.settings.AppPreferences
import com.example.rssreader.data.settings.SettingsRepository
import com.example.rssreader.data.translation.ArticleTranslationManager
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
    articleTranslationManager: ArticleTranslationManager,
    settingsRepository: SettingsRepository,
    refreshScheduler: RefreshScheduler
) {
    val navController = rememberNavController()
    val settings by settingsRepository.settings.collectAsState(initial = null)

    LaunchedEffect(settings?.refreshIntervalHours) {
        settings?.let { loadedSettings ->
            refreshScheduler.sync(loadedSettings)
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
                        translationManager = articleTranslationManager,
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
                        translationManager = articleTranslationManager,
                        showImages = settings?.showImages ?: true,
                        articleBodyTextSizeOffset = settings?.articleBodyTextSizeOffset ?: 0,
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}


