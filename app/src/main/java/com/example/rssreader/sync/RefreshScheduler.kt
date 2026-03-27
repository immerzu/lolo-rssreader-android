package com.example.rssreader.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.rssreader.data.settings.AppPreferences
import java.util.concurrent.TimeUnit

class RefreshScheduler(
    context: Context,
    private val workManager: WorkManager = WorkManager.getInstance(context)
) {
    fun sync(settings: AppPreferences) {
        if (settings.refreshIntervalHours <= 0) {
            workManager.cancelUniqueWork(BACKGROUND_REFRESH_WORK_NAME)
            return
        }

        workManager.enqueueUniquePeriodicWork(
            BACKGROUND_REFRESH_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            buildBackgroundRefreshRequest(settings.refreshIntervalHours)
        )
    }
}

internal const val BACKGROUND_REFRESH_WORK_NAME = "rss_background_refresh"

internal fun buildBackgroundRefreshRequest(refreshIntervalHours: Int): PeriodicWorkRequest {
    return PeriodicWorkRequestBuilder<BackgroundRefreshWorker>(
        refreshIntervalHours.toLong(),
        TimeUnit.HOURS
    )
        .setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
        )
        .build()
}



