package de.lolo.rssreader.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Regression RSS-A04: Ohne GUID und Link bildete der Ersatzschluessel zwei
 * verschiedene Artikel auf denselben Wert ab, sobald Autor und Teaser gleich waren -
 * der Titel fehlte im Schluessel.
 */
@RunWith(RobolectricTestRunner::class)
class FeedParserUniqueKeyTest {

    private val parser = FeedParser()

    @Test
    fun gleicherInhaltUnterschiedlicherTitelErgibtAnderenSchluessel() {
        val first = parser.parse(
            xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0">
                  <channel>
                    <title>Testfeed</title>
                    <item>
                      <title>Erster Artikel</title>
                      <author>redaktion@example.com (Redaktion)</author>
                      <description>Gleicher Teaser fuer beide Artikel.</description>
                    </item>
                  </channel>
                </rss>
            """.trimIndent(),
            sourceUrl = "https://example.com/feed.xml"
        ).items.single()

        val second = parser.parse(
            xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0">
                  <channel>
                    <title>Testfeed</title>
                    <item>
                      <title>Zweiter Artikel</title>
                      <author>redaktion@example.com (Redaktion)</author>
                      <description>Gleicher Teaser fuer beide Artikel.</description>
                    </item>
                  </channel>
                </rss>
            """.trimIndent(),
            sourceUrl = "https://example.com/feed.xml"
        ).items.single()

        assertNotEquals(first.uniqueKey, second.uniqueKey)
    }

    @Test
    fun gleicherArtikelErgibtStabilenSchluessel() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0">
              <channel>
                <title>Testfeed</title>
                <item>
                  <title>Gleicher Artikel</title>
                  <author>redaktion@example.com (Redaktion)</author>
                  <description>Gleicher Teaser.</description>
                </item>
              </channel>
            </rss>
        """.trimIndent()

        val first = parser.parse(xml, "https://example.com/feed.xml").items.single().uniqueKey
        val second = parser.parse(xml, "https://example.com/feed.xml").items.single().uniqueKey

        assertEquals(first, second)
    }
}
