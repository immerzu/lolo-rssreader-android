package com.example.rssreader.ui.screens

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
}
