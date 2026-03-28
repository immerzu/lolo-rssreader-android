package com.example.rssreader.data.repository

import com.example.rssreader.data.db.ArticleEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedRepositoryArticleContentTest {

    @Test
    fun hasSameStoredArticleContentReturnsTrueForIdenticalStoredFields() {
        val existingArticle = article(
            title = "Bangkok Tipps",
            link = "https://example.com/bangkok",
            plainText = "Tempel und Street Food",
            contentHtml = "<p>Tempel und Street Food</p>",
            imageUrls = "https://example.com/a.jpg"
        )
        val incomingArticle = existingArticle.copy(
            id = 99,
            isRead = true,
            isFavorite = true
        )

        assertTrue(hasSameStoredArticleContent(existingArticle, incomingArticle))
    }

    @Test
    fun hasSameStoredArticleContentReturnsFalseWhenStoredTextChanges() {
        val existingArticle = article(plainText = "Tempel und Street Food")
        val incomingArticle = article(plainText = "Tempel, Street Food und Nachtmarkt")

        assertFalse(hasSameStoredArticleContent(existingArticle, incomingArticle))
    }

    @Test
    fun hasSameStoredArticleContentReturnsFalseWhenStoredTitleChanges() {
        val existingArticle = article(title = "Bangkok Tipps")
        val incomingArticle = article(title = "Bangkok Nachtmarkt")

        assertFalse(hasSameStoredArticleContent(existingArticle, incomingArticle))
    }

    @Test
    fun hasSameStoredArticleContentReturnsFalseWhenPublishedAtChanges() {
        val existingArticle = article()
        val incomingArticle = article().copy(publishedAt = 1_800_000_000_000)

        assertFalse(hasSameStoredArticleContent(existingArticle, incomingArticle))
    }

    @Test
    fun hasSameStoredArticleContentReturnsFalseWhenImageUrlsChange() {
        val existingArticle = article(imageUrls = "https://example.com/a.jpg")
        val incomingArticle = article(imageUrls = "https://example.com/b.jpg")

        assertFalse(hasSameStoredArticleContent(existingArticle, incomingArticle))
    }

    @Test
    fun shouldSyncSearchIndexByFeedSkipsUnchangedRefreshWrite() {
        assertFalse(
            shouldSyncSearchIndexByFeed(
                insertedArticles = 0,
                updatedArticles = 0
            )
        )
    }

    @Test
    fun shouldSyncSearchIndexByFeedRunsForChangedArticles() {
        assertTrue(
            shouldSyncSearchIndexByFeed(
                insertedArticles = 1,
                updatedArticles = 0
            )
        )
        assertTrue(
            shouldSyncSearchIndexByFeed(
                insertedArticles = 0,
                updatedArticles = 1
            )
        )
    }

    @Test
    fun shouldDeleteStaleSearchIndexEntriesAfterDeletionOnlyRunsWhenRowsWereDeleted() {
        assertFalse(shouldDeleteStaleSearchIndexEntriesAfterDeletion(0))
        assertTrue(shouldDeleteStaleSearchIndexEntriesAfterDeletion(3))
    }

    @Test
    fun buildRefreshRunStatsKeepsRefreshAndFailureCountsStable() {
        val stats = buildRefreshRunStats(
            outcomes = listOf(
                RefreshFeedOutcome(insertedArticles = 3, retryableFailure = false),
                RefreshFeedOutcome(insertedArticles = 0, retryableFailure = false),
                RefreshFeedOutcome(insertedArticles = null, retryableFailure = true),
                RefreshFeedOutcome(insertedArticles = null, retryableFailure = false)
            ),
            skippedFeeds = 2
        )

        assertEquals(2, stats.refreshedFeeds)
        assertEquals(2, stats.skippedFeeds)
        assertEquals(2, stats.failedFeeds)
        assertEquals(1, stats.retryableFeeds)
        assertEquals(3, stats.newArticles)
    }

    @Test
    fun collectConflictingArticlesKeepsOnlyFailedInserts() {
        val first = article(title = "Erster")
        val second = article(title = "Zweiter").copy(uniqueKey = "bangkok-2")
        val third = article(title = "Dritter").copy(uniqueKey = "bangkok-3")

        val conflicting = collectConflictingArticles(
            articles = listOf(first, second, third),
            insertedIds = listOf(11L, -1L, -1L)
        )

        assertEquals(listOf(second, third), conflicting)
    }

    @Test
    fun buildUpdatedArticleEntityPreservesReadAndFavoriteFlags() {
        val existing = article(title = "Alt").copy(
            id = 15,
            isRead = true,
            isFavorite = true
        )
        val incoming = article(title = "Neu").copy(
            id = 99,
            plainText = "Neuer Text",
            contentHtml = "<p>Neu</p>"
        )

        val updated = buildUpdatedArticleEntity(existing, incoming)

        assertEquals(15, updated.id)
        assertTrue(updated.isRead)
        assertTrue(updated.isFavorite)
        assertEquals("Neu", updated.title)
        assertEquals("Neuer Text", updated.plainText)
        assertEquals("<p>Neu</p>", updated.contentHtml)
    }

    @Test
    fun buildUpdatedArticleEntityPreservesIdentityFields() {
        val existing = article(
            title = "Alt",
            link = "https://example.com/alt"
        ).copy(
            id = 15,
            feedId = 42,
            uniqueKey = "stable-key",
            isRead = true,
            isFavorite = true
        )
        val incoming = article(
            title = "Neu",
            link = "https://example.com/neu",
            plainText = "Neuer Text",
            imageUrls = "https://example.com/neu.jpg"
        ).copy(
            id = 99,
            feedId = 7,
            uniqueKey = "incoming-key"
        )

        val updated = buildUpdatedArticleEntity(existing, incoming)

        assertEquals(15, updated.id)
        assertEquals(42, updated.feedId)
        assertEquals("stable-key", updated.uniqueKey)
        assertTrue(updated.isRead)
        assertTrue(updated.isFavorite)
        assertEquals("Neu", updated.title)
        assertEquals("https://example.com/neu", updated.link)
        assertEquals("Neuer Text", updated.plainText)
        assertEquals("https://example.com/neu.jpg", updated.imageUrls)
    }

    @Test
    fun collectConflictingArticlesIgnoresTrailingArticlesWithoutInsertResults() {
        val first = article(title = "Erster")
        val second = article(title = "Zweiter").copy(uniqueKey = "bangkok-2")
        val third = article(title = "Dritter").copy(uniqueKey = "bangkok-3")

        val conflicting = collectConflictingArticles(
            articles = listOf(first, second, third),
            insertedIds = listOf(11L, -1L)
        )

        assertEquals(listOf(second), conflicting)
    }

    @Test
    fun collapseConflictingArticlesByUniqueKeyKeepsLatestArticlePerKey() {
        val first = article(title = "Erster").copy(uniqueKey = "bangkok-1")
        val second = article(title = "Zweiter").copy(uniqueKey = "bangkok-2")
        val third = article(title = "Dritter").copy(uniqueKey = "bangkok-1")
        val fourth = article(title = "Vierter").copy(uniqueKey = "bangkok-3")

        val collapsed = collapseConflictingArticlesByUniqueKey(
            listOf(first, second, third, fourth)
        )

        assertEquals(
            listOf(
                third,
                second,
                fourth
            ),
            collapsed
        )
    }

    private fun article(
        title: String = "Bangkok Tipps",
        link: String = "https://example.com/bangkok",
        plainText: String = "Tempel und Street Food",
        contentHtml: String = "<p>Tempel und Street Food</p>",
        imageUrls: String = "https://example.com/a.jpg"
    ): ArticleEntity {
        return ArticleEntity(
            id = 1,
            feedId = 7,
            uniqueKey = "bangkok-1",
            title = title,
            link = link,
            publishedAt = 1_700_000_000_000,
            plainText = plainText,
            contentHtml = contentHtml,
            imageUrls = imageUrls
        )
    }
}
