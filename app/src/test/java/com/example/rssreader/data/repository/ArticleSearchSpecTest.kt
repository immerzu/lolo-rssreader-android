package com.example.rssreader.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArticleSearchSpecTest {

    @Test
    fun buildArticleSearchSpecCreatesPrefixMatchQueryFromWords() {
        val spec = buildArticleSearchSpec("  Reise Thailand 2026 ")

        assertEquals("Reise Thailand 2026", spec.query)
        assertEquals("reise* AND thailand* AND 2026*", spec.matchQuery)
    }

    @Test
    fun buildArticleSearchSpecDeduplicatesTokens() {
        val spec = buildArticleSearchSpec("Feed feed FEED")

        assertEquals("feed*", spec.matchQuery)
    }

    @Test
    fun buildArticleSearchSpecFallsBackWhenNoUsefulTokenExists() {
        val spec = buildArticleSearchSpec(" - / : ")

        assertEquals("- / :", spec.query)
        assertNull(spec.matchQuery)
    }
}

