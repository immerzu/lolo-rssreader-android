package com.example.rssreader.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.rssreader.data.settings.AppPreferences
import java.util.concurrent.TimeUnit

class RefreshScheduler(
    context: Context
) {
    private val workManager = WorkManager.getInstance(context)

    fun sync(settings: AppPreferences) {
        if (settings.refreshIntervalHours <= 0) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }

        val request = PeriodicWorkRequestBuilder<BackgroundRefreshWorker>(
            settings.refreshIntervalHours.toLong(),
            TimeUnit.HOURS
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        workManager.enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    private companion object {
        const val WORK_NAME = "rss_background_refresh"
    }
}



========================================================================================================================