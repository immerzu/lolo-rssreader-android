package com.example.rssreader.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.example.rssreader.data.settings.AppPreferences
import java.util.concurrent.TimeUnit
import java.util.concurrent.Executors
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RefreshSchedulerTest {

    private lateinit var context: Context
    private lateinit var workManager: WorkManager
    private lateinit var scheduler: RefreshScheduler

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder()
                .setExecutor(Executors.newSingleThreadExecutor())
                .setTaskExecutor(Executors.newSingleThreadExecutor())
                .build()
        )
        workManager = WorkManager.getInstance(context)
        scheduler = RefreshScheduler(context, workManager)
    }

    @After
    fun tearDown() {
        workManager.cancelUniqueWork(BACKGROUND_REFRESH_WORK_NAME)
    }

    @Test
    fun syncCancelsUniqueWorkWhenRefreshIsDisabled() {
        scheduler.sync(AppPreferences(refreshIntervalHours = 6))
        scheduler.sync(AppPreferences(refreshIntervalHours = 0))

        val workInfos = workManager.getWorkInfosForUniqueWork(BACKGROUND_REFRESH_WORK_NAME).get()

        assertTrue(workInfos.all { it.state == WorkInfo.State.CANCELLED })
    }

    @Test
    fun syncEnqueuesUniquePeriodicWorkWhenRefreshIsEnabled() {
        scheduler.sync(AppPreferences(refreshIntervalHours = 6))

        val workInfos = workManager.getWorkInfosForUniqueWork(BACKGROUND_REFRESH_WORK_NAME).get()

        assertEquals(1, workInfos.size)
        assertEquals(WorkInfo.State.ENQUEUED, workInfos.single().state)
    }

    @Test
    fun syncUsesExpectedWorkNameAndConnectedNetworkConstraint() {
        scheduler.sync(AppPreferences(refreshIntervalHours = 12))

        val workInfos = workManager.getWorkInfosForUniqueWork(BACKGROUND_REFRESH_WORK_NAME).get()

        assertEquals(1, workInfos.size)
        assertEquals(
            NetworkType.CONNECTED,
            buildBackgroundRefreshRequest(12).workSpec.constraints.requiredNetworkType
        )
    }

    @Test
    fun repeatedSyncDoesNotCreateMultipleActiveSchedules() {
        scheduler.sync(AppPreferences(refreshIntervalHours = 4))
        scheduler.sync(AppPreferences(refreshIntervalHours = 4))

        val workInfos = workManager.getWorkInfosForUniqueWork(BACKGROUND_REFRESH_WORK_NAME).get()
        val activeInfos = workInfos.count { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING }

        assertEquals(1, activeInfos)
    }

    @Test
    fun buildBackgroundRefreshRequestKeepsExpectedRepeatIntervalAndWorkerType() {
        val request = buildBackgroundRefreshRequest(6)

        assertEquals(TimeUnit.HOURS.toMillis(6), request.workSpec.intervalDuration)
        assertEquals(
            BackgroundRefreshWorker::class.java.name,
            request.workSpec.workerClassName
        )
    }
}
