package com.example.rssreader.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.rssreader.data.settings.AppPreferences
import com.example.rssreader.debug.DebugLogger
import java.util.concurrent.TimeUnit

class RefreshScheduler(
    context: Context,
    private val workManager: WorkManager = WorkManager.getInstance(context)
) {
    fun sync(settings: AppPreferences) {
        if (settings.refreshIntervalMinutes <= 0) {
            DebugLogger.i(
                "RefreshScheduler",
                "Hintergrundaktualisierung deaktiviert: intervalMinutes=${settings.refreshIntervalMinutes}, wifiOnly=${settings.refreshOnlyOnWifi}"
            )
            workManager.cancelUniqueWork(BACKGROUND_REFRESH_WORK_NAME)
            return
        }

        DebugLogger.i(
            "RefreshScheduler",
            "Hintergrundaktualisierung geplant: intervalMinutes=${settings.refreshIntervalMinutes}, wifiOnly=${settings.refreshOnlyOnWifi}"
        )
        workManager.enqueueUniquePeriodicWork(
            BACKGROUND_REFRESH_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            buildBackgroundRefreshRequest(
                refreshIntervalMinutes = settings.refreshIntervalMinutes,
                refreshOnlyOnWifi = settings.refreshOnlyOnWifi
            )
        )
    }
}

internal const val BACKGROUND_REFRESH_WORK_NAME = "rss_background_refresh"

internal fun buildBackgroundRefreshRequest(
    refreshIntervalMinutes: Int,
    refreshOnlyOnWifi: Boolean = false
): PeriodicWorkRequest {
    return PeriodicWorkRequestBuilder<BackgroundRefreshWorker>(
        refreshIntervalMinutes.toLong(),
        TimeUnit.MINUTES
    )
        .setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(
                    if (refreshOnlyOnWifi) {
                        NetworkType.UNMETERED
                    } else {
                        NetworkType.CONNECTED
                    }
                )
                .build()
        )
        .build()
}



