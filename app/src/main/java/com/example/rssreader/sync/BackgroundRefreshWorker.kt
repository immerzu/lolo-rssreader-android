package com.example.rssreader.sync

import android.content.Context
import android.net.ConnectivityManager
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.rssreader.RssReaderApplication
import com.example.rssreader.notifications.ArticleUpdateNotifier

class BackgroundRefreshWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as RssReaderApplication
        val isUnmeteredNetwork = isUnmeteredConnection()
        val result = app.repository.refreshAllInBackground(isUnmeteredNetwork = isUnmeteredNetwork)
        runCatching {
            val settings = app.settingsRepository.getCurrentSettings()
            if (settings.notificationsEnabled) {
                ArticleUpdateNotifier(applicationContext).showNewArticlesNotification(
                    newArticles = result.newArticles,
                    refreshedFeeds = result.refreshedFeeds
                )
            }
        }.onFailure { throwable ->
            Log.w(TAG, "Benachrichtigung nach Hintergrundaktualisierung fehlgeschlagen", throwable)
        }

        return Result.success()
    }

    private fun isUnmeteredConnection(): Boolean {
        val connectivityManager = applicationContext.getSystemService(ConnectivityManager::class.java)
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        if (!capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            return false
        }
        return !connectivityManager.isActiveNetworkMetered
    }

    companion object {
        private const val TAG = "BackgroundRefresh"
    }
}


========================================================================================================================