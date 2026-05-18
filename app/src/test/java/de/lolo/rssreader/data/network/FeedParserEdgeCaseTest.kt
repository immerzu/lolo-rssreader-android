package de.lolo.rssreader.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FeedParserEdgeCaseTest {

    private val parser = FeedParser()

    @Test
    fun itemWithoutValidDateGetsNullPublishedAt() {
        val parsed = parser.parse(
            xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0">
                  <channel>
                    <title>Datumsloser Feed</title>
                    <item>
                      <title>Artikel ohne Datum</title>
                      <link>https://example.com/ohne-datum</link>
                      <description>Beschreibung ohne Zeitstempel</description>
                    </item>
                  </channel>
                </rss>
            """.trimIndent(),
            sourceUrl = "https://example.com/feed.xml"
        )

        val article = parsed.items.single()
        assertEquals("Artikel ohne Datum", article.title)
        assertNull(article.publishedAt)
    }

    @Test
    fun itemWithEmptyTitleUsesFallback() {
        val parsed = parser.parse(
            xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0">
                  <channel>
                    <title>Leerer-Titel Feed</title>
                    <item>
                      <title></title>
                      <link>https://example.com/leer</link>
                      <description>Inhalt ohne Titel</description>
                    </item>
                  </channel>
                </rss>
            """.trimIndent(),
            sourceUrl = "https://example.com/feed.xml"
        )

        val article = parsed.items.single()
        assertEquals("Ohne Titel", article.title)
    }

    @Test
    fun itemWithMissingTitleTagUsesFallback() {
        val parsed = parser.parse(
            xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0">
                  <channel>
                    <title>Kein-Titel Feed</title>
                    <item>
                      <link>https://example.com/kein-titel</link>
                      <description>Beschreibung ohne Titel-Tag</description>
                    </item>
                  </channel>
                </rss>
            """.trimIndent(),
            sourceUrl = "https://example.com/feed.xml"
        )

        val article = parsed.items.single()
        assertEquals("Ohne Titel", article.title)
    }

    @Test
    fun itemWithMissingLinkUsesEmptyString() {
        val parsed = parser.parse(
            xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0">
                  <channel>
                    <title>Linkloser Feed</title>
                    <item>
                      <title>Artikel ohne Link</title>
                      <description>Beschreibung ohne Link-Tag</description>
                    </item>
                  </channel>
                </rss>
            """.trimIndent(),
            sourceUrl = "https://example.com/feed.xml"
        )

        val article = parsed.items.single()
        assertEquals("Artikel ohne Link", article.title)
        assertEquals("", article.link)
        assertTrue(article.uniqueKey.isNotBlank())
    }

    @Test
    fun rssImageResolvesRelativeIconUrlAgainstSourceUrl() {
        val parsed = parser.parse(
            xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0">
                  <channel>
                    <title>Relativer-Icon Feed</title>
                    <link>https://example.com/blog/</link>
                    <image>
                      <url>/images/feed-icon.png</url>
                      <title>Feed-Icon</title>
                      <link>https://example.com/</link>
                    </image>
                    <item>
                      <title>Artikel</title>
                      <link>https://example.com/artikel</link>
                      <description>Text</description>
                    </item>
                  </channel>
                </rss>
            """.trimIndent(),
            sourceUrl = "https://example.com/feed.xml"
        )

        assertNotNull(parsed.iconUrl)
        assertTrue(parsed.iconUrl!!.contains("example.com"))
        assertTrue(parsed.iconUrl!!.contains("feed-icon.png"))
    }

    @Test
    fun feedWithManyItemsIsNotCappedInNormalMode() {
        val xml = buildString {
            appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
            appendLine("<rss version=\"2.0\">")
            appendLine("<channel>")
            appendLine("<title>Viele-Items Feed</title>")
            repeat(15) { i ->
                appendLine("<item>")
                appendLine("<title>Artikel $i</title>")
                appendLine("<link>https://example.com/artikel-$i</link>")
                appendLine("<description>Beschreibung $i</description>")
                appendLine("</item>")
            }
            appendLine("</channel>")
            appendLine("</rss>")
        }

        val parsed = parser.parse(
            xml = xml,
            sourceUrl = "https://example.com/feed.xml"
        )

        assertEquals(15, parsed.items.size)
    }

    @Test
    fun brokenHtmlInContentDoesNotCrashParser() {
        val parsed = parser.parse(
            xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0" xmlns:content="http://purl.org/rss/1.0/modules/content/">
                  <channel>
                    <title>Kaputter-HTML Feed</title>
                    <item>
                      <title>Kaputter Artikel</title>
                      <link>https://example.com/kaputt</link>
                      <content:encoded><![CDATA[
                        <div>Offener Div
                        <p>Fehlendes Ende
                        <a href="https://example.com">Link ohne Schluss-Tag
                        <script>alert('XSS')</script>
                        <img src="https://example.com/bild.jpg" onerror="steal()"
                      ]]></content:encoded>
                    </item>
                  </channel>
                </rss>
            """.trimIndent(),
            sourceUrl = "https://example.com/feed.xml"
        )

        val article = parsed.items.single()
        assertEquals("Kaputter Artikel", article.title)
        assertNotNull(article.plainText)
        assertFalse(article.plainText.contains("alert('XSS')"))
        assertFalse(article.plainText.contains("steal()"))
    }

    @Test
    fun atomFeedWithNonSelfLinkRelStillParsesEntryLinks() {
        val parsed = parser.parse(
            xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <feed xmlns="http://www.w3.org/2005/Atom"
                      xmlns:thr="http://purl.org/syndication/thread/1.0">
                  <title>Erweitertes Atom</title>
                  <entry>
                    <id>tag:example.com,2026:1</id>
                    <title>Eintrag mit Thr-Link</title>
                    <link rel="replies" type="application/atom+xml"
                          thr:count="3" href="https://example.com/replies.atom" />
                    <link rel="alternate" href="https://example.com/posts/1" />
                    <summary>Inhalt</summary>
                  </entry>
                </feed>
            """.trimIndent(),
            sourceUrl = "https://example.com/feed.atom"
        )

        val article = parsed.items.single()
        assertEquals("Eintrag mit Thr-Link", article.title)
        assertEquals("https://example.com/posts/1", article.link)
    }

    @Test
    fun feedWithOnlyWhitespaceTitleUsesFallback() {
        val parsed = parser.parse(
            xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0">
                  <channel>
                    <title>   </title>
                    <item>
                      <title>Ein Artikel</title>
                      <link>https://example.com/artikel</link>
                      <description>Text</description>
                    </item>
                  </channel>
                </rss>
            """.trimIndent(),
            sourceUrl = "https://example.com/feed.xml"
        )

        assertEquals("Unbekannter Feed", parsed.title)
    }

    @Test
    fun atomEntryWithNonArticleLinkRelStillGetsFallbackLink() {
        val parsed = parser.parse(
            xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <feed xmlns="http://www.w3.org/2005/Atom">
                  <title>Atom Feed</title>
                  <entry>
                    <id>tag:example.com,2026:1</id>
                    <title>Eintrag</title>
                    <link rel="edit" href="https://example.com/edit/1" />
                    <link rel="enclosure" href="https://example.com/media.mp3" />
                    <link rel="self" href="https://example.com/entry.atom" />
                    <summary>Inhalt</summary>
                  </entry>
                </feed>
            """.trimIndent(),
            sourceUrl = "https://example.com/feed.atom"
        )

        val article = parsed.items.single()
        assertEquals("Eintrag", article.title)
        // Alle rel-Werte sind Non-Article-Links; Link sollte leer sein
        assertEquals("", article.link)
    }
}
