package de.lolo.rssreader.data.repository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * RSS-DUP-001-005: sichert das Verhalten beider begrenzter Parallelitaets-Helfer ab.
 * RefreshCoordinator faengt Fehler pro Element (onFailure), runOpmlTasksBounded laesst sie
 * propagieren - die Unterschiede sind gewollt, eine Zusammenfuehrung waere nicht gleichwertig.
 */
class RefreshCoordinatorTest {

    @Test
    fun refreshCoordinatorKeepsParallelismLimit() = runTest {
        val current = AtomicInteger(0)
        val maxConcurrent = AtomicInteger(0)
        val coordinator = RefreshCoordinator<Int>(
            parallelism = 2,
            task = { item ->
                val running = current.incrementAndGet()
                maxConcurrent.updateAndGet { maxOf(it, running) }
                delay(10)
                current.decrementAndGet()
                RefreshFeedOutcome(insertedArticles = item, retryableFailure = false)
            },
            onFailure = { _, _ -> RefreshFeedOutcome(insertedArticles = null, retryableFailure = false) }
        )

        val outcomes = coordinator.run((1..6).toList())

        assertEquals(6, outcomes.size)
        assertTrue("Parallelitaet darf 2 nicht ueberschreiten (war ${maxConcurrent.get()})", maxConcurrent.get() <= 2)
        assertEquals("Reihenfolge der Ergebnisse bleibt erhalten", listOf(1, 2, 3, 4, 5, 6), outcomes.map { it.insertedArticles })
    }

    @Test
    fun refreshCoordinatorRoutesFailuresToOnFailure() = runTest {
        val coordinator = RefreshCoordinator<Int>(
            parallelism = 1,
            task = { item -> if (item == 2) error("boom") else RefreshFeedOutcome(item, false) },
            onFailure = { _, _ -> RefreshFeedOutcome(insertedArticles = null, retryableFailure = true) }
        )

        val outcomes = coordinator.run(listOf(1, 2, 3))

        assertEquals(3, outcomes.size)
        assertEquals(1, outcomes[0].insertedArticles)
        assertEquals(null, outcomes[1].insertedArticles)
        assertTrue("Der Fehler wird als retryable gemeldet", outcomes[1].retryableFailure)
        assertEquals(3, outcomes[2].insertedArticles)
    }

    @Test
    fun refreshCoordinatorRethrowsCancellation() = runTest {
        val coordinator = RefreshCoordinator<Int>(
            parallelism = 1,
            task = { throw CancellationException("abgebrochen") },
            onFailure = { _, _ -> RefreshFeedOutcome(null, false) }
        )

        try {
            coordinator.run(listOf(1))
            fail("CancellationException muss weitergereicht werden")
        } catch (_: CancellationException) {
            // erwartet
        }
    }

    @Test
    fun opmlTasksKeepOrderAndLimit() = runTest {
        val current = AtomicInteger(0)
        val maxConcurrent = AtomicInteger(0)

        val results = runOpmlTasksBounded((1..9).toList(), parallelism = 3) { item ->
            val running = current.incrementAndGet()
            maxConcurrent.updateAndGet { maxOf(it, running) }
            delay(5)
            current.decrementAndGet()
            item * 2
        }

        assertEquals((1..9).map { it * 2 }, results)
        assertTrue("Parallelitaet darf 3 nicht ueberschreiten (war ${maxConcurrent.get()})", maxConcurrent.get() <= 3)
    }

    @Test
    fun opmlTasksPropagateFailure() = runTest {
        try {
            runOpmlTasksBounded(listOf(1, 2, 3), parallelism = 2) { item ->
                if (item == 2) error("boom") else item
            }
            fail("Fehler muessen propagieren")
        } catch (expected: IllegalStateException) {
            assertEquals("boom", expected.message)
        }
    }
}
