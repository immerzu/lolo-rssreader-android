package de.lolo.rssreader.ui.screens

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Deckt die Markier-Logik der Leseransicht ab: Oeffnen markiert den Einstiegsartikel,
 * waagerechtes Weiterwischen markiert den verlassenen Artikel. Bei erstem/letztem Artikel
 * gibt es kein Ziel und damit keine Markierung.
 */
class ArticleReaderSwipeTargetTest {

    @Test
    fun swipeToNewerTargetsTheNewerArticle() {
        assertEquals(
            7L,
            resolveSwipeTargetArticleId(SwipeDirection.ToNewer, newerArticleId = 7L, olderArticleId = 9L)
        )
    }

    @Test
    fun swipeToOlderTargetsTheOlderArticle() {
        assertEquals(
            9L,
            resolveSwipeTargetArticleId(SwipeDirection.ToOlder, newerArticleId = 7L, olderArticleId = 9L)
        )
    }

    @Test
    fun swipeToNewerOnTheNewestArticleHasNoTarget() {
        assertNull(resolveSwipeTargetArticleId(SwipeDirection.ToNewer, newerArticleId = null, olderArticleId = 9L))
    }

    @Test
    fun swipeToOlderOnTheOldestArticleHasNoTarget() {
        assertNull(resolveSwipeTargetArticleId(SwipeDirection.ToOlder, newerArticleId = 7L, olderArticleId = null))
    }

    @Test
    fun noSwipeDirectionHasNoTarget() {
        assertNull(resolveSwipeTargetArticleId(SwipeDirection.None, newerArticleId = 7L, olderArticleId = 9L))
    }

    @Test
    fun singleArticleHasNoTargetInEitherDirection() {
        assertNull(resolveSwipeTargetArticleId(SwipeDirection.ToNewer, newerArticleId = null, olderArticleId = null))
        assertNull(resolveSwipeTargetArticleId(SwipeDirection.ToOlder, newerArticleId = null, olderArticleId = null))
    }

    @Test
    fun entryArticleIsMarkedReadOnLoad() {
        assertTrue(shouldMarkReadOnArticleLoad(currentArticleId = 5L, entryArticleId = 5L))
    }

    @Test
    fun switchedArticleIsNotMarkedOnLoadAgain() {
        // Beim Weiterwischen wird der verlassene Artikel markiert; der neu angezeigte
        // wird erst beim Verlassen markiert - daher hier keine Markierung beim Laden.
        assertFalse(shouldMarkReadOnArticleLoad(currentArticleId = 8L, entryArticleId = 5L))
    }

    @Test
    fun marksAnArticleThatWasNotMarkedInThisReaderSessionYet() {
        assertTrue(shouldMarkArticleRead(articleId = 5L, alreadyMarkedIds = emptySet()))
        assertTrue(shouldMarkArticleRead(articleId = 5L, alreadyMarkedIds = setOf(4L, 6L)))
    }

    @Test
    fun doesNotMarkTheSameArticleTwiceInOneReaderSession() {
        // Deckt den Fall ab, dass ein Artikel bereits durch Oeffnen oder Wischen markiert
        // wurde und die Leseransicht danach verlassen wird.
        assertFalse(shouldMarkArticleRead(articleId = 5L, alreadyMarkedIds = setOf(5L)))
    }

    @Test
    fun doesNotMarkInvalidArticleIds() {
        assertFalse(shouldMarkArticleRead(articleId = 0L, alreadyMarkedIds = emptySet()))
        assertFalse(shouldMarkArticleRead(articleId = -1L, alreadyMarkedIds = emptySet()))
    }

    // ── markArticleReadOnce: Guard erst nach Erfolg, Cancellation bleibt wirksam ──

    @Test
    fun markArticleReadOnceMarksOnceAndRecordsTheGuardOnlyAfterSuccess() = runTest {
        val marked = mutableSetOf<Long>()
        val writes = mutableListOf<Long>()

        val first = markArticleReadOnce(
            articleId = 5L,
            alreadyMarkedIds = marked,
            markRead = { id -> writes += id },
            onFailure = { _, _ -> fail("kein Fehler erwartet") }
        )
        assertTrue(first)
        assertEquals(listOf(5L), writes)
        assertEquals(setOf(5L), marked)

        // Zweiter Aufruf in derselben Sitzung: kein zweiter Schreibvorgang.
        val second = markArticleReadOnce(
            articleId = 5L,
            alreadyMarkedIds = marked,
            markRead = { id -> writes += id },
            onFailure = { _, _ -> fail("kein Fehler erwartet") }
        )
        assertFalse(second)
        assertEquals(listOf(5L), writes)
    }

    @Test
    fun markArticleReadOnceKeepsRetryPossibleAfterAnOrdinaryWriteFailure() = runTest {
        val marked = mutableSetOf<Long>()
        val failures = mutableListOf<Throwable>()
        var attempts = 0

        val failed = markArticleReadOnce(
            articleId = 5L,
            alreadyMarkedIds = marked,
            markRead = {
                attempts += 1
                throw IllegalStateException("db down")
            },
            onFailure = { _, throwable -> failures += throwable }
        )
        assertFalse("Fehlgeschlagenes Schreiben darf nicht als Erfolg gelten", failed)
        assertTrue("Ein Fehlschlag darf den Artikel nicht als erledigt vormerken", marked.isEmpty())
        assertEquals(1, failures.size)

        // Erneuter Versuch ist nicht durch einen falschen Guard-Eintrag blockiert.
        val retried = markArticleReadOnce(
            articleId = 5L,
            alreadyMarkedIds = marked,
            markRead = { attempts += 1 },
            onFailure = { _, _ -> fail("kein Fehler erwartet") }
        )
        assertTrue(retried)
        assertEquals(2, attempts)
        assertEquals(setOf(5L), marked)
    }

    @Test
    fun markArticleReadOncePropagatesCancellation() = runTest {
        val marked = mutableSetOf<Long>()
        var failures = 0

        val result = runCatching {
            markArticleReadOnce(
                articleId = 5L,
                alreadyMarkedIds = marked,
                markRead = { throw CancellationException("cancelled") },
                onFailure = { _, _ -> failures += 1 }
            )
        }

        assertTrue(
            "CancellationException muss unveraendert weitergereicht werden",
            result.exceptionOrNull() is CancellationException
        )
        assertTrue(marked.isEmpty())
        assertEquals("Cancellation darf nicht als Schreibfehler gemeldet werden", 0, failures)
    }

    @Test
    fun markArticleReadOnceSkipsInvalidArticleIdsWithoutWriting() = runTest {
        val marked = mutableSetOf<Long>()
        var attempts = 0

        val result = markArticleReadOnce(
            articleId = 0L,
            alreadyMarkedIds = marked,
            markRead = { attempts += 1 },
            onFailure = { _, _ -> fail("kein Fehler erwartet") }
        )

        assertFalse(result)
        assertEquals(0, attempts)
        assertTrue(marked.isEmpty())
    }
}
