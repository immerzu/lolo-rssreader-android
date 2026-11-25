package de.lolo.rssreader.data.repository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Regressionstest fuer RSS-A34: Ein fehlgeschlagener Datenbank-Lesefluss darf die App
 * nicht abstuerzen lassen, muss gemeldet werden und darf Coroutine-Abbruch nicht schlucken.
 */
class DataReadSafetyTest {

    @Test
    fun successfulReadPassesValuesThrough() = runTest {
        var failure: DataReadFailure? = null
        var dataCallbacks = 0

        val values = flowOf(1, 2, 3)
            .guardDatabaseRead(
                source = "feed_summaries",
                onFailure = { failure = it },
                onData = { dataCallbacks += 1 }
            )
            .toList()

        assertEquals(listOf(1, 2, 3), values)
        assertEquals(null, failure)
        assertEquals(3, dataCallbacks)
    }

    @Test
    fun databaseFailureIsReportedWithoutThrowing() = runTest {
        var failure: DataReadFailure? = null

        val values = flow<Int> { throw IllegalStateException("database closed") }
            .guardDatabaseRead(source = "search", onFailure = { failure = it })
            .toList()

        assertTrue("Nach einem Lesefehler duerfen keine Daten geliefert werden", values.isEmpty())
        assertNotNull("Der Lesefehler muss gemeldet werden", failure)
        assertEquals("search", failure!!.source)
        assertEquals("database closed", failure!!.cause.message)
    }

    @Test
    fun cancellationIsRethrownInsteadOfSwallowed() = runTest {
        var failure: DataReadFailure? = null
        var cancellationRethrown = false

        try {
            flow<Int> { throw CancellationException("collector cancelled") }
                .guardDatabaseRead(source = "article_navigation", onFailure = { failure = it })
                .toList()
            fail("CancellationException muss weitergereicht werden")
        } catch (_: CancellationException) {
            cancellationRethrown = true
        }

        assertTrue(cancellationRethrown)
        assertEquals("Abbruch ist kein Lesefehler und darf nicht gemeldet werden", null, failure)
    }
}
