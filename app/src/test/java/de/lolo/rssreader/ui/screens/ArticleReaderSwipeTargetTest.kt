package de.lolo.rssreader.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
}
