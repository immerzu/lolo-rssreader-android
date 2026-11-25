package de.lolo.rssreader.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Regressionstest fuer RSS-A32: Die harte Obergrenze von 1.000.000 Zeichen pro
 * Artikelinhalt gilt auch ohne Defensivmodus. Ueberlange Inhalte werden gekuerzt,
 * ohne Exception und ohne den Artikel zu verlieren.
 */
@RunWith(RobolectricTestRunner::class)
class FeedParserContentLimitTest {

    private val parser = FeedParser()
    private val absoluteLimit = 1_000_000

    private fun feedWithDescription(description: String): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8"?>""")
        append("<rss version=\"2.0\"><channel>")
        append("<title>Test</title><link>https://example.com/</link><description>Test</description>")
        append("<item><title>Artikel</title><link>https://example.com/a</link>")
        append("<guid>a</guid><description>")
        append(description)
        append("</description></item></channel></rss>")
    }

    @Test
    fun contentAboveAbsoluteLimitIsTruncatedWithoutLosingTheItem() {
        val oversized = "x".repeat(absoluteLimit + 500)

        val parsed = parser.parse(feedWithDescription(oversized), "https://example.com/feed")

        assertEquals(1, parsed.items.size)
        val item = parsed.items.single()
        assertTrue(
            "contentHtml must not exceed the absolute limit (was ${item.contentHtml.length})",
            item.contentHtml.length <= absoluteLimit
        )
        assertTrue(
            "plainText must not exceed the absolute limit (was ${item.plainText.length})",
            item.plainText.length <= absoluteLimit
        )
        assertTrue("Der Artikel darf nicht leer werden", item.plainText.isNotBlank())
    }

    @Test
    fun contentBelowAbsoluteLimitIsKeptInFull() {
        val belowLimit = "y".repeat(absoluteLimit - 1_000)

        val parsed = parser.parse(feedWithDescription(belowLimit), "https://example.com/feed")

        val item = parsed.items.single()
        assertEquals("Inhalt unterhalb der Grenze bleibt unveraendert", belowLimit, item.contentHtml)
        assertEquals(belowLimit.length, item.plainText.length)
    }
}
