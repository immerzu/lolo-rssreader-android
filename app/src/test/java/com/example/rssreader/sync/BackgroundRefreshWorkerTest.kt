package com.example.rssreader.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.example.rssreader.data.repository.RefreshRunStats
import com.example.rssreader.data.settings.AppPreferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class BackgroundRefreshWorkerTest {

    @Test
    fun shouldRetryWhenNoFeedWasRefreshedButRetryableFailuresExist() {
        assertTrue(
            shouldRetryBackgroundRefresh(
                RefreshRunStats(
                    refreshedFeeds = 0,
                    failedFeeds = 2,
                    retryableFeeds = 2,
                    newArticles = 0
                )
            )
        )
    }

    @Test
    fun shouldNotRetryWhenAtLeastOneFeedWasRefreshed() {
        assertFalse(
            shouldRetryBackgroundRefresh(
                RefreshRunStats(
                    refreshedFeeds = 1,
                    failedFeeds = 1,
                    retryableFeeds = 1,
                    newArticles = 3
                )
            )
        )
    }

    @Test
    fun shouldNotRetryForOnlyPermanentFailures() {
        assertFalse(
            shouldRetryBackgroundRefresh(
                RefreshRunStats(
                    refreshedFeeds = 0,
                    failedFeeds = 2,
                    retryableFeeds = 0,
                    newArticles = 0
                )
            )
        )
    }

    @Test
    fun shouldAttemptNotificationOnlyWhenEnabled() {
        assertTrue(
            shouldAttemptBackgroundRefreshNotification(
                AppPreferences(notificationsEnabled = true),
                RefreshRunStats(newArticles = 1)
            )
        )
        assertFalse(
            shouldAttemptBackgroundRefreshNotification(
                AppPreferences(notificationsEnabled = false),
                RefreshRunStats(newArticles = 1)
            )
        )
        assertFalse(
            shouldAttemptBackgroundRefreshNotification(
                AppPreferences(notificationsEnabled = true),
                RefreshRunStats(newArticles = 0)
            )
        )
    }

    @Test
    fun doWorkReturnsRetryWhenRepositoryThrows() = runTest {
        val worker = buildWorker(
            runtime = testRuntime(
                refreshResult = null,
                refreshThrowable = IllegalStateException("boom")
            )
        )

        assertEquals(ListenableWorker.Result.retry(), worker.doWork())
    }

    @Test
    fun doWorkReturnsRetryWhenOnlyRetryableFailuresOccurred() = runTest {
        val notifications = mutableListOf<Pair<Int, Int>>()
        val worker = buildWorker(
            runtime = testRuntime(
                refreshResult = RefreshRunStats(
                    refreshedFeeds = 0,
                    failedFeeds = 2,
                    retryableFeeds = 2,
                    newArticles = 0
                ),
                notificationsEnabled = true,
                notificationSink = notifications
            )
        )

        assertEquals(ListenableWorker.Result.retry(), worker.doWork())
        assertTrue(notifications.isEmpty())
    }

    @Test
    fun doWorkReturnsSuccessWhenAtLeastOneFeedWasRefreshed() = runTest {
        val notifications = mutableListOf<Pair<Int, Int>>()
        val worker = buildWorker(
            runtime = testRuntime(
                refreshResult = RefreshRunStats(
                    refreshedFeeds = 1,
                    failedFeeds = 1,
                    retryableFeeds = 1,
                    newArticles = 3
                ),
                notificationsEnabled = true,
                notificationSink = notifications
            )
        )

        assertEquals(ListenableWorker.Result.success(), worker.doWork())
        assertEquals(listOf(3 to 1), notifications)
    }

    @Test
    fun doWorkDoesNotAttemptNotificationWhenDisabled() = runTest {
        val notifications = mutableListOf<Pair<Int, Int>>()
        val worker = buildWorker(
            runtime = testRuntime(
                refreshResult = RefreshRunStats(
                    refreshedFeeds = 2,
                    failedFeeds = 0,
                    retryableFeeds = 0,
                    newArticles = 4
                ),
                notificationsEnabled = false,
                notificationSink = notifications
            )
        )

        assertEquals(ListenableWorker.Result.success(), worker.doWork())
        assertTrue(notifications.isEmpty())
    }

    @Test
    fun doWorkDoesNotAttemptNotificationWhenNoNewArticlesExist() = runTest {
        val notifications = mutableListOf<Pair<Int, Int>>()
        val worker = buildWorker(
            runtime = testRuntime(
                refreshResult = RefreshRunStats(
                    refreshedFeeds = 2,
                    failedFeeds = 0,
                    retryableFeeds = 0,
                    newArticles = 0
                ),
                notificationsEnabled = true,
                notificationSink = notifications
            )
        )

        assertEquals(ListenableWorker.Result.success(), worker.doWork())
        assertTrue(notifications.isEmpty())
    }

    @Test
    fun notificationFailureDoesNotFailWorker() = runTest {
        val worker = buildWorker(
            runtime = testRuntime(
                refreshResult = RefreshRunStats(
                    refreshedFeeds = 1,
                    failedFeeds = 0,
                    retryableFeeds = 0,
                    newArticles = 2
                ),
                notificationsEnabled = true,
                notificationThrowable = IllegalStateException("notify")
            )
        )

        assertEquals(ListenableWorker.Result.success(), worker.doWork())
    }

    @Test
    fun repositoryFailureDoesNotAttemptNotificationBeforeRetry() = runTest {
        val notifications = mutableListOf<Pair<Int, Int>>()
        val worker = buildWorker(
            runtime = testRuntime(
                refreshResult = null,
                refreshThrowable = IllegalStateException("boom"),
                notificationsEnabled = true,
                notificationSink = notifications
            )
        )

        assertEquals(ListenableWorker.Result.retry(), worker.doWork())
        assertTrue(notifications.isEmpty())
    }

    private fun buildWorker(runtime: BackgroundRefreshRuntime): BackgroundRefreshWorker {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return TestListenableWorkerBuilder<BackgroundRefreshWorker>(context)
            .setWorkerFactory(TestBackgroundRefreshWorkerFactory(runtime))
            .build()
    }

    private fun testRuntime(
        refreshResult: RefreshRunStats?,
        refreshThrowable: Throwable? = null,
        notificationsEnabled: Boolean = true,
        notificationThrowable: Throwable? = null,
        notificationSink: MutableList<Pair<Int, Int>> = mutableListOf()
    ): BackgroundRefreshRuntime {
        return BackgroundRefreshRuntime(
            refreshAllInBackground = {
                refreshThrowable?.let { throw it }
                checkNotNull(refreshResult)
            },
            getCurrentSettings = {
                AppPreferences(notificationsEnabled = notificationsEnabled)
            },
            showNewArticlesNotification = { newArticles, refreshedFeeds ->
                notificationThrowable?.let { throw it }
                notificationSink += (newArticles to refreshedFeeds)
            },
            isUnmeteredConnection = { true }
        )
    }
}

private class TestBackgroundRefreshWorkerFactory(
    private val runtime: BackgroundRefreshRuntime
) : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters
    ): ListenableWorker? {
        if (workerClassName != BackgroundRefreshWorker::class.java.name) {
            return null
        }
        return BackgroundRefreshWorker(appContext, workerParameters, runtime)
    }
}
