package de.lolo.rssreader.sync

import android.content.Context
import android.net.ConnectivityManager
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import de.lolo.rssreader.BuildConfig
import de.lolo.rssreader.RssReaderApplication
import de.lolo.rssreader.data.repository.RefreshRunStats
import de.lolo.rssreader.data.settings.AppPreferences
import de.lolo.rssreader.debug.DebugLogger
import de.lolo.rssreader.notifications.ArticleUpdateNotifier

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
        val settings = runtime.getCurrentSettings()
        val hasWifiConnection = runtime.hasWifiConnection()
        val isUnmeteredNetwork = runtime.isUnmeteredConnection()
        DebugLogger.i(
            TAG,
            "Worker gestartet: notificationsEnabled=${settings.notificationsEnabled}, refreshOnlyOnWifi=${settings.refreshOnlyOnWifi}, intervalMinutes=${settings.refreshIntervalMinutes}, hasWifi=$hasWifiConnection, isUnmetered=$isUnmeteredNetwork"
        )
        if (shouldSkipBackgroundRefreshForWifiOnlySetting(settings, hasWifiConnection)) {
            DebugLogger.i(TAG, "Worker uebersprungen: wifiOnly=true und keine WLAN-Verbindung")
            return Result.success()
        }
        val result = runCatching {
            runtime.refreshAllInBackground(isUnmeteredNetwork, hasWifiConnection)
        }.getOrElse { throwable ->
            DebugLogger.w(TAG, "Worker fehlgeschlagen, versuche spaeter erneut", throwable)
            if (BuildConfig.DEBUG) {
                Log.w(TAG, "Hintergrundaktualisierung komplett fehlgeschlagen", throwable)
            }
            return Result.retry()
        }

        DebugLogger.i(
            TAG,
            "Worker beendet: refreshed=${result.refreshedFeeds}, failed=${result.failedFeeds}, retryable=${result.retryableFeeds}, skipped=${result.skippedFeeds}, new=${result.newArticles}"
        )
        if (shouldRetryBackgroundRefresh(result)) {
            DebugLogger.i(TAG, "Worker fordert Retry an")
            return Result.retry()
        }

        runCatching {
            maybeShowNotification(settings, result)
        }.onFailure { throwable ->
            DebugLogger.w(TAG, "Benachrichtigung nach Hintergrundaktualisierung fehlgeschlagen", throwable)
            if (BuildConfig.DEBUG) {
                Log.w(TAG, "Benachrichtigung nach Hintergrundaktualisierung fehlgeschlagen", throwable)
            }
        }

        DebugLogger.i(TAG, "Worker erfolgreich abgeschlossen")
        return Result.success()
    }

    private fun maybeShowNotification(settings: AppPreferences, result: RefreshRunStats) {
        val isAppInForeground = runtime.isAppInForeground()
        if (!shouldAttemptBackgroundRefreshNotification(settings, result, isAppInForeground)) {
            DebugLogger.i(
                TAG,
                "Keine Benachrichtigung: notificationsEnabled=${settings.notificationsEnabled}, newArticles=${result.newArticles}, isAppInForeground=$isAppInForeground"
            )
            return
        }
        DebugLogger.i(
            TAG,
            "Benachrichtigung wird angefragt: newArticles=${result.newArticles}, refreshedFeeds=${result.refreshedFeeds}"
        )
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
    val refreshAllInBackground: suspend (Boolean, Boolean) -> RefreshRunStats,
    val getCurrentSettings: suspend () -> AppPreferences,
    val showNewArticlesNotification: (Int, Int) -> Unit,
    val hasWifiConnection: () -> Boolean,
    val isUnmeteredConnection: () -> Boolean,
    val isAppInForeground: () -> Boolean
)

internal fun createBackgroundRefreshRuntime(appContext: Context): BackgroundRefreshRuntime {
    val application = appContext.applicationContext as RssReaderApplication
    return BackgroundRefreshRuntime(
        refreshAllInBackground = { isUnmeteredNetwork, hasWifiConnection ->
            application.repository.refreshAllInBackground(
                isUnmeteredNetwork = isUnmeteredNetwork,
                hasWifiConnection = hasWifiConnection
            )
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
        hasWifiConnection = {
            hasWifiConnection(appContext)
        },
        isUnmeteredConnection = {
            isUnmeteredConnection(appContext)
        },
        isAppInForeground = {
            ProcessLifecycleOwner
                .get()
                .lifecycle
                .currentState
                .isAtLeast(Lifecycle.State.STARTED)
        }
    )
}

internal fun shouldRetryBackgroundRefresh(result: RefreshRunStats): Boolean {
    return result.refreshedFeeds == 0 && result.retryableFeeds > 0
}

internal fun shouldAttemptBackgroundRefreshNotification(
    settings: AppPreferences,
    result: RefreshRunStats,
    isAppInForeground: Boolean
): Boolean {
    return settings.notificationsEnabled && result.newArticles > 0 && !isAppInForeground
}

internal fun shouldSkipBackgroundRefreshForWifiOnlySetting(
    settings: AppPreferences,
    hasWifiConnection: Boolean
): Boolean {
    return settings.refreshOnlyOnWifi && !hasWifiConnection
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

internal fun hasWifiConnection(context: Context): Boolean {
    val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
    val activeNetwork = connectivityManager.activeNetwork ?: return false
    val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
    if (!capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
        return false
    }
    val hasWifi = capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)
    val hasCellular = capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR)
    val hasEthernet = capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET)
    val hasVpn = capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN)

    return hasWifi || (hasVpn && !connectivityManager.isActiveNetworkMetered && !hasCellular && !hasEthernet)
}


