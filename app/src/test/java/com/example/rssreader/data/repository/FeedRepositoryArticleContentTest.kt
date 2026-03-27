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
    fun shouldDeleteStaleSearchIndexEntriesOnlyRunsWhenStaleRowsArePossible() {
        assertFalse(
            shouldDeleteStaleSearchIndexEntries(
                searchIndexMayContainStaleRows = false
            )
        )
        assertTrue(
            shouldDeleteStaleSearchIndexEntries(
                searchIndexMayContainStaleRows = true
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
