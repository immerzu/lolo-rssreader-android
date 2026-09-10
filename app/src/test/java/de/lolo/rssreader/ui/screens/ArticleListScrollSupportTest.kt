package de.lolo.rssreader.ui.screens

import de.lolo.rssreader.data.db.ArticleEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleListScrollSupportTest {

    private fun article(id: Long, isRead: Boolean = false) = ArticleEntity(
        id = id,
        feedId = 1L,
        uniqueKey = "key-$id",
        title = "Artikel $id",
        link = "https://example.com/$id",
        publishedAt = id,
        plainText = "Text $id",
        contentHtml = "<p>Text $id</p>",
        imageUrls = "",
        isRead = isRead,
        isFavorite = false
    )

    @Test
    fun marksNothingWhenListIsNotScrolled() {
        val articles = listOf(article(1), article(2), article(3))

        val result = articlesToMarkReadOnScroll(articles, firstVisibleArticleIndex = 0, alreadyMarkedIds = emptySet())

        assertTrue(result.isEmpty())
    }

    @Test
    fun marksOnlyArticlesScrolledPast() {
        val articles = listOf(article(1), article(2), article(3), article(4))

        val result = articlesToMarkReadOnScroll(articles, firstVisibleArticleIndex = 2, alreadyMarkedIds = emptySet())

        assertEquals(listOf(1L, 2L), result.map { it.id })
    }

    @Test
    fun skipsArticlesThatAreAlreadyRead() {
        val articles = listOf(article(1, isRead = true), article(2), article(3))

        val result = articlesToMarkReadOnScroll(articles, firstVisibleArticleIndex = 2, alreadyMarkedIds = emptySet())

        assertEquals(listOf(2L), result.map { it.id })
    }

    @Test
    fun skipsArticlesMarkedInThisScreenVisit() {
        val articles = listOf(article(1), article(2), article(3))

        val result = articlesToMarkReadOnScroll(
            articles = articles,
            firstVisibleArticleIndex = 3,
            alreadyMarkedIds = setOf(1L)
        )

        assertEquals(listOf(2L, 3L), result.map { it.id })
    }

    @Test
    fun handlesIndexBeyondListSize() {
        val articles = listOf(article(1), article(2))

        val result = articlesToMarkReadOnScroll(articles, firstVisibleArticleIndex = 99, alreadyMarkedIds = emptySet())

        assertEquals(listOf(1L, 2L), result.map { it.id })
    }

    @Test
    fun marksOnlyTheFirstArticleWhenItHasJustBeenScrolledPast() {
        val articles = listOf(article(1), article(2), article(3))

        val result = articlesToMarkReadOnScroll(articles, firstVisibleArticleIndex = 1, alreadyMarkedIds = emptySet())

        assertEquals(listOf(1L), result.map { it.id })
    }

    @Test
    fun returnsEmptyForAnEmptyArticleList() {
        val result = articlesToMarkReadOnScroll(
            articles = emptyList(),
            firstVisibleArticleIndex = 3,
            alreadyMarkedIds = emptySet()
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun neverMarksTheArticleThatIsCurrentlyVisible() {
        val articles = listOf(article(1), article(2), article(3), article(4))

        val result = articlesToMarkReadOnScroll(articles, firstVisibleArticleIndex = 3, alreadyMarkedIds = emptySet())

        // Artikel 4 (Index 3) ist sichtbar und bleibt ungelesen.
        assertEquals(listOf(1L, 2L, 3L), result.map { it.id })
        assertTrue(result.none { it.id == 4L })
    }

    @Test
    fun convertsListIndexToArticleIndexWithAndWithoutHeader() {
        assertEquals(0, firstVisibleArticleIndexFor(listIndex = 0, hasHeaderItem = true))
        assertEquals(0, firstVisibleArticleIndexFor(listIndex = 1, hasHeaderItem = true))
        assertEquals(1, firstVisibleArticleIndexFor(listIndex = 2, hasHeaderItem = true))
        assertEquals(1, firstVisibleArticleIndexFor(listIndex = 1, hasHeaderItem = false))
        assertEquals(0, firstVisibleArticleIndexFor(listIndex = -3, hasHeaderItem = true))
    }
}
