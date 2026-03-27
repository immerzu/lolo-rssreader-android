package com.example.rssreader.data.repository

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedRepositoryRefreshSupportTest {

    @Test
    fun refreshCoordinatorContinuesAfterTaskFailure() = runTest {
        val coordinator = RefreshCoordinator<Int>(
            parallelism = 2,
            task = { value: Int ->
                if (value == 2) {
                    error("boom")
                }
                delay(10)
                RefreshFeedOutcome(insertedArticles = value, retryableFailure = false)
            },
            onFailure = { _: Int, _: Throwable ->
                RefreshFeedOutcome(insertedArticles = null, retryableFailure = true)
            }
        )

        val outcomes = coordinator.run(listOf(1, 2, 3))

        assertEquals(3, outcomes.size)
        assertEquals(1, outcomes[0].insertedArticles)
        assertEquals(null, outcomes[1].insertedArticles)
        assertTrue(outcomes[1].retryableFailure)
        assertEquals(3, outcomes[2].insertedArticles)
    }

    @Test
    fun refreshCoordinatorHonorsConcurrencyLimit() = runTest {
        val inFlight = AtomicInteger(0)
        val maxInFlight = AtomicInteger(0)
        val coordinator = RefreshCoordinator<Int>(
            parallelism = 2,
            task = { value: Int ->
                val current = inFlight.incrementAndGet()
                maxInFlight.updateAndGet { previous -> maxOf(previous, current) }
                try {
                    delay(10)
                    RefreshFeedOutcome(insertedArticles = value, retryableFailure = false)
                } finally {
                    inFlight.decrementAndGet()
                }
            },
            onFailure = { _: Int, throwable: Throwable -> throw throwable }
        )

        val outcomes = coordinator.run(listOf(1, 2, 3, 4))

        assertEquals(4, outcomes.size)
        assertEquals(2, maxInFlight.get())
    }
}
