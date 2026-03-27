package com.example.rssreader.sync

import android.content.Context
import android.net.ConnectivityManager
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.rssreader.RssReaderApplication
import com.example.rssreader.data.repository.RefreshRunStats
import com.example.rssreader.data.settings.AppPreferences
import com.example.rssreader.notifications.ArticleUpdateNotifier

class BackgroundRefreshWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    private var runtime: BackgroundRefreshRuntime = createBackgroundRefreshRuntime(appContext)

    internal constructor(
        appContext: Context,
        params: WorkerParameters,
        runtime: BackgroundRefreshRuntime
    ) : this(appContext, params) {
        this.runtime = runtime
    }

    override suspend fun doWork(): Result {
        val isUnmeteredNetwork = runtime.isUnmeteredConnection()
        val result = runCatching {
            runtime.refreshAllInBackground(isUnmeteredNetwork)
        }.getOrElse { throwable ->
            Log.w(TAG, "Hintergrundaktualisierung komplett fehlgeschlagen", throwable)
            return Result.retry()
        }

        if (shouldRetryBackgroundRefresh(result)) {
            return Result.retry()
        }

        runCatching {
            maybeShowNotification(result)
        }.onFailure { throwable ->
            Log.w(TAG, "Benachrichtigung nach Hintergrundaktualisierung fehlgeschlagen", throwable)
        }

        return Result.success()
    }

    private suspend fun maybeShowNotification(result: RefreshRunStats) {
        val settings = runtime.getCurrentSettings()
        if (!shouldAttemptBackgroundRefreshNotification(settings, result)) {
            return
        }
        runtime.showNewArticlesNotification(
            result.newArticles,
            result.refreshedFeeds
        )
    }

    companion object {
        private const val TAG = "BackgroundRefresh"
    }
}

internal data class BackgroundRefreshRuntime(
    val refreshAllInBackground: suspend (Boolean) -> RefreshRunStats,
    val getCurrentSettings: suspend () -> AppPreferences,
    val showNewArticlesNotification: (Int, Int) -> Unit,
    val isUnmeteredConnection: () -> Boolean
)

internal fun createBackgroundRefreshRuntime(appContext: Context): BackgroundRefreshRuntime {
    val application = appContext.applicationContext as RssReaderApplication
    return BackgroundRefreshRuntime(
        refreshAllInBackground = { isUnmeteredNetwork ->
            application.repository.refreshAllInBackground(isUnmeteredNetwork = isUnmeteredNetwork)
        },
        getCurrentSettings = {
            application.settingsRepository.getCurrentSettings()
        },
        showNewArticlesNotification = { newArticles, refreshedFeeds ->
            ArticleUpdateNotifier(appContext).showNewArticlesNotification(
                newArticles = newArticles,
                refreshedFeeds = refreshedFeeds
            )
        },
        isUnmeteredConnection = {
            isUnmeteredConnection(appContext)
        }
    )
}

internal fun shouldRetryBackgroundRefresh(result: RefreshRunStats): Boolean {
    return result.refreshedFeeds == 0 && result.retryableFeeds > 0
}

internal fun shouldAttemptBackgroundRefreshNotification(
    settings: AppPreferences,
    result: RefreshRunStats
): Boolean {
    return settings.notificationsEnabled && result.newArticles > 0
}

internal fun isUnmeteredConnection(context: Context): Boolean {
    val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
    val activeNetwork = connectivityManager.activeNetwork ?: return false
    val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
    if (!capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
        return false
    }
    return !connectivityManager.isActiveNetworkMetered
}


