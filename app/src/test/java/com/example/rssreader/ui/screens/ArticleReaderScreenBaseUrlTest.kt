package com.example.rssreader.ui.screens

import com.example.rssreader.data.db.ArticleEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class ArticleReaderScreenBaseUrlTest {

    @Test
    fun resolveReaderWebViewBaseUrlUsesHttpArticleUrl() {
        assertEquals(
            "https://dasgelbeforum.net/index.php?id=683167",
            resolveReaderWebViewBaseUrl("https://dasgelbeforum.net/index.php?id=683167")
        )
    }

    @Test
    fun resolveReaderWebViewBaseUrlTrimsArticleUrl() {
        assertEquals(
            "https://dasgelbeforum.net/index.php?id=683167",
            resolveReaderWebViewBaseUrl("  https://dasgelbeforum.net/index.php?id=683167  ")
        )
    }

    @Test
    fun resolveReaderWebViewBaseUrlFallsBackForBlankOrNonHttpUrl() {
        assertEquals(
            "https://localhost/",
            resolveReaderWebViewBaseUrl(null)
        )
        assertEquals(
            "https://localhost/",
            resolveReaderWebViewBaseUrl("   ")
        )
        assertEquals(
            "https://localhost/",
            resolveReaderWebViewBaseUrl("content://example/article")
        )
    }

    @Test
    fun resolveReaderFallbackBodyTextUsesStoredPlainTextWhenPresent() {
        assertEquals(
            "Originaltext ohne Uebersetzung",
            resolveReaderFallbackBodyText(
                article(plainText = "Originaltext ohne Uebersetzung")
            )
        )
    }

    @Test
    fun resolveReaderFallbackBodyTextUsesPlaceholderWhenStoredTextIsBlank() {
        assertEquals(
            "Kein Textinhalt vorhanden.",
            resolveReaderFallbackBodyText(
                article(plainText = "   ")
            )
        )
    }

    private fun article(plainText: String): ArticleEntity {
        return ArticleEntity(
            id = 1,
            feedId = 7,
            uniqueKey = "reader-1",
            title = "Reader Test",
            link = "https://example.com/reader",
            publishedAt = 1_700_000_000_000,
            plainText = plainText,
            contentHtml = "",
            imageUrls = ""
        )
    }
}
