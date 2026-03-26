package com.example.rssreader.sync

import com.example.rssreader.data.repository.RefreshRunStats
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
}
