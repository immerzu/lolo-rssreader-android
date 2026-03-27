package com.example.rssreader.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FeedRepositoryImportTest {

    @Test
    fun detectImportableFeedUrlPrefersHttpSelfLinkFromFeedXml() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0" xmlns:atom="http://www.w3.org/2005/Atom">
              <channel>
                <title>Beispiel Feed</title>
                <atom:link href="https://example.com/feed.xml" rel="self" type="application/rss+xml" />
                <link>https://example.com/</link>
              </channel>
            </rss>
        """.trimIndent()

        assertEquals("https://example.com/feed.xml", detectImportableFeedUrl(xml))
    }

    @Test
    fun detectImportableFeedUrlIgnoresNonHttpSelfLinks() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
              <title>Lokaler Feed</title>
              <link rel="self" href="content://example/feed.xml" />
            </feed>
        """.trimIndent()

        assertNull(detectImportableFeedUrl(xml))
    }

    @Test
    fun detectImportableFeedUrlFallsBackToTypedAtomLink() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0" xmlns:atom="http://www.w3.org/2005/Atom">
              <channel>
                <title>Kommentare</title>
                <atom:link href="https://example.com/comments/feed/" type="application/rss+xml" />
              </channel>
            </rss>
        """.trimIndent()

        assertEquals("https://example.com/comments/feed/", detectImportableFeedUrl(xml))
    }

    @Test
    fun detectImportableFeedUrlStillPrefersSelfLinkWhenPlainChannelLinkExists() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0" xmlns:atom="http://www.w3.org/2005/Atom">
              <channel>
                <title>Kommentare</title>
                <atom:link rel="self" href="https://example.com/comments/feed/" type="application/rss+xml" />
                <link>https://example.com/landing-page</link>
              </channel>
            </rss>
        """.trimIndent()

        assertEquals("https://example.com/comments/feed/", detectImportableFeedUrl(xml))
    }

    @Test
    fun detectImportableFeedUrlAcceptsSelfLinkWhenHrefAndRelOrderIsReversed() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
              <title>Kommentare</title>
              <link href="https://example.com/comments/feed/" rel="self" type="application/atom+xml" />
            </feed>
        """.trimIndent()

        assertEquals("https://example.com/comments/feed/", detectImportableFeedUrl(xml))
    }

    @Test
    fun detectImportableFeedUrlAcceptsPlainFeedLikeChannelLink() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0">
              <channel>
                <title>Kommentare</title>
                <link>https://example.com/comments/feed/</link>
              </channel>
            </rss>
        """.trimIndent()

        assertEquals("https://example.com/comments/feed/", detectImportableFeedUrl(xml))
    }

    @Test
    fun detectImportableFeedUrlRejectsPlainChannelLinkWithoutFeedHint() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0">
              <channel>
                <title>Normale Website</title>
                <link>https://example.com/articles/latest</link>
              </channel>
            </rss>
        """.trimIndent()

        assertNull(detectImportableFeedUrl(xml))
    }

    @Test
    fun detectImportableFeedUrlAcceptsPlainChannelLinkWithXmlHint() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0">
              <channel>
                <title>Exportierter Feed</title>
                <link>https://example.com/export/comments.xml</link>
              </channel>
            </rss>
        """.trimIndent()

        assertEquals("https://example.com/export/comments.xml", detectImportableFeedUrl(xml))
    }
}
