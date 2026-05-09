package de.lolo.rssreader.data.network

import java.io.File
import org.junit.Assume.assumeTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FeedParserTest {

    private val parser = FeedParser()

    @Test
    fun wordpressItemKeepsRealTitleAndPrefersContentEncoded() {
        val parsed = parser.parse(
            xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0"
                    xmlns:content="http://purl.org/rss/1.0/modules/content/"
                    xmlns:dc="http://purl.org/dc/elements/1.1/"
                    xmlns:media="http://search.yahoo.com/mrss/">
                  <channel>
                    <title>Ossiblock</title>
                    <link>https://ossiblock.wordpress.com</link>
                    <item>
                      <title>Beitragsüberschrift mit Straße und Grüße</title>
                      <link>https://ossiblock.wordpress.com/2026/03/25/beitrag/</link>
                      <dc:creator><![CDATA[Ossiblock]]></dc:creator>
                      <pubDate>Wed, 25 Mar 2026 13:56:53 +0000</pubDate>
                      <description><![CDATA[Teaser mit Beschreibung<p><a href="https://ossiblock.wordpress.com/2026/03/25/beitrag/">Weiterlesen <span class="meta-nav">&#8594;</span></a></p>]]></description>
                      <content:encoded><![CDATA[
                        <p>Volltext mit ÄÖÜ äöü ß und Straße.</p>
                        <p><img src="https://ossiblock.wordpress.com/wp-content/uploads/2026/03/beispiel.jpg" /></p>
                      ]]></content:encoded>
                      <media:content url="https://1.gravatar.com/avatar/example?s=96&amp;d=retro&amp;r=G" medium="image">
                        <media:title type="html">bgob2013</media:title>
                      </media:content>
                    </item>
                  </channel>
                </rss>
            """.trimIndent(),
            sourceUrl = "https://ossiblock.wordpress.com/feed/"
        )

        val article = parsed.items.single()
        assertEquals("Beitragsüberschrift mit Straße und Grüße", article.title)
        assertEquals("Ossiblock", article.author)
        assertEquals(ParsedContentSource.CONTENT_ENCODED, article.contentSource)
        assertTrue(article.plainText.contains("Volltext mit ÄÖÜ äöü ß und Straße."))
        assertFalse(article.plainText.contains("Teaser mit Beschreibung"))
        assertFalse(article.plainText.contains("Weiterlesen"))
        assertTrue(article.imageUrls.contains("https://ossiblock.wordpress.com/wp-content/uploads/2026/03/beispiel.jpg"))
        assertNotNull(article.publishedAt)
    }

    @Test
    fun payloadReaderPreservesLatin1UmlautsOnParserPath() {
        val xml = """
            <?xml version="1.0" encoding="ISO-8859-1"?>
            <rss version="2.0" xmlns:content="http://purl.org/rss/1.0/modules/content/">
              <channel>
                <title>Beispiel Feed</title>
                <link>https://example.com/</link>
                <item>
                  <title>Beitragsüberschrift mit Straße und Grüße</title>
                  <link>https://example.com/post-1</link>
                  <description><![CDATA[Teaser mit Köln]]></description>
                  <content:encoded><![CDATA[<p>Schöne Grüße aus München.</p>]]></content:encoded>
                </item>
              </channel>
            </rss>
        """.trimIndent()

        val payload = FetchedFeedPayload(
            responseBytes = xml.toByteArray(Charsets.ISO_8859_1),
            charset = Charsets.ISO_8859_1,
            byteSize = xml.toByteArray(Charsets.ISO_8859_1).size,
            defensiveMode = false
        )

        val parsed = parser.parse(
            payload = payload,
            sourceUrl = "https://example.com/feed.xml"
        )

        val article = parsed.items.single()
        assertEquals("Beitragsüberschrift mit Straße und Grüße", article.title)
        assertTrue(article.plainText.contains("Schöne Grüße aus München."))
        assertFalse(article.plainText.contains("Teaser mit Köln"))
    }

    @Test
    fun wordpressDescriptionFallbackDoesNotUseMediaTitleAsArticleTitle() {
        val parsed = parser.parse(
            xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0"
                    xmlns:dc="http://purl.org/dc/elements/1.1/"
                    xmlns:media="http://search.yahoo.com/mrss/">
                  <channel>
                    <title>Ossiblock</title>
                    <link>https://ossiblock.wordpress.com</link>
                    <item>
                      <title>(XIV) Schmidt findet Ruhe in Mangalia</title>
                      <link>https://ossiblock.wordpress.com/2026/03/25/xiv-schmidt-findet-ruhe-in-mangalia/</link>
                      <dc:creator><![CDATA[Ossiblock]]></dc:creator>
                      <pubDate>Wed, 25 Mar 2026 13:56:53 +0000</pubDate>
                      <description><![CDATA[Schmidt sagte Elena nach dem Einkauf, daß er morgen für ein paar Tage verschwinden würde. <p><a href="https://ossiblock.wordpress.com/2026/03/25/xiv-schmidt-findet-ruhe-in-mangalia/">Weiterlesen <span class="meta-nav">&#8594;</span></a></p>]]></description>
                      <media:content url="https://1.gravatar.com/avatar/example?s=96&amp;d=retro&amp;r=G" medium="image">
                        <media:title type="html">bgob2013</media:title>
                      </media:content>
                    </item>
                  </channel>
                </rss>
            """.trimIndent(),
            sourceUrl = "https://ossiblock.wordpress.com/feed/"
        )

        val article = parsed.items.single()
        assertEquals("(XIV) Schmidt findet Ruhe in Mangalia", article.title)
        assertEquals("Ossiblock", article.author)
        assertEquals(ParsedContentSource.DESCRIPTION, article.contentSource)
        assertTrue(article.plainText.startsWith("Schmidt sagte Elena nach dem Einkauf"))
        assertFalse(article.plainText.contains("Weiterlesen"))
        assertFalse(article.title.contains("bgob2013"))
        assertTrue(article.imageUrls.isEmpty())
    }

    @Test
    fun inspectRealOssiblockFeedIfAvailable() {
        val configuredLiveFeedPath = System.getProperty("liveFeedPath")
            ?: System.getenv("LIVE_FEED_PATH")
        val liveFeedFile = configuredLiveFeedPath
            ?.takeIf { it.isNotBlank() }
            ?.let(::File)
            ?: File(System.getProperty("user.dir").orEmpty()).resolve("../ossiblock_feed_live.xml")
        assumeTrue("Live-Feed-Datei nicht vorhanden.", liveFeedFile.exists())

        val parsed = parser.parse(
            xml = liveFeedFile.readText(Charsets.UTF_8),
            sourceUrl = "https://ossiblock.wordpress.com/feed/"
        )

        val article = parsed.items.first()
        assertEquals("(XIV) Schmidt findet Ruhe in Mangalia", article.title)
        assertEquals("Ossiblock", article.author)
        assertEquals(ParsedContentSource.DESCRIPTION, article.contentSource)
        assertFalse(article.title.contains("bgob2013"))
        assertFalse(article.plainText.contains("Weiterlesen"))
        assertTrue(article.imageUrls.isEmpty())
    }

    @Test
    fun atomFeedMetadataStaysOnFeedLevelAndPrefersAlternateEntryLink() {
        val parsed = parser.parse(
            xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <feed xmlns="http://www.w3.org/2005/Atom">
                  <title>Atom Beispiel</title>
                  <link rel="self" href="https://example.com/feed.atom" />
                  <link rel="alternate" href="https://example.com/" />
                  <entry>
                    <title>Eintragstitel</title>
                    <link rel="self" href="https://example.com/posts/1.atom" />
                    <link rel="alternate" href="https://example.com/posts/1" />
                    <summary type="html">&lt;p&gt;Kurzinhalt&lt;/p&gt;</summary>
                  </entry>
                </feed>
            """.trimIndent(),
            sourceUrl = "https://example.com/feed.atom"
        )

        assertEquals("Atom Beispiel", parsed.title)
        assertEquals("https://example.com/", parsed.siteUrl)
        assertEquals(1, parsed.items.size)
        assertEquals("https://example.com/posts/1", parsed.items.single().link)
    }

    @Test
    fun rssFallbackUniqueKeyDoesNotDependOnTitleAlone() {
        val first = parser.parse(
            xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0">
                  <channel>
                    <title>RSS Beispiel</title>
                    <item>
                      <title>Alter Titel</title>
                      <dc:creator xmlns:dc="http://purl.org/dc/elements/1.1/">Autor</dc:creator>
                      <pubDate>Fri, 04 Apr 2026 10:00:00 +0000</pubDate>
                      <description><![CDATA[<p>Gleicher Inhalt</p>]]></description>
                    </item>
                  </channel>
                </rss>
            """.trimIndent(),
            sourceUrl = "https://example.com/rss.xml"
        ).items.single()

        val second = parser.parse(
            xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0">
                  <channel>
                    <title>RSS Beispiel</title>
                    <item>
                      <title>Neuer Titel</title>
                      <dc:creator xmlns:dc="http://purl.org/dc/elements/1.1/">Autor</dc:creator>
                      <pubDate>Fri, 04 Apr 2026 10:00:00 +0000</pubDate>
                      <description><![CDATA[<p>Gleicher Inhalt</p>]]></description>
                    </item>
                  </channel>
                </rss>
            """.trimIndent(),
            sourceUrl = "https://example.com/rss.xml"
        ).items.single()

        assertEquals(first.uniqueKey, second.uniqueKey)
    }

    @Test
    fun atomFallbackUniqueKeyDoesNotDependOnTitleAlone() {
        val first = parser.parse(
            xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <feed xmlns="http://www.w3.org/2005/Atom">
                  <title>Atom Beispiel</title>
                  <entry>
                    <title>Erster Titel</title>
                    <author><name>Autor</name></author>
                    <updated>2026-04-04T10:00:00Z</updated>
                    <summary type="html">&lt;p&gt;Gleicher Inhalt&lt;/p&gt;</summary>
                  </entry>
                </feed>
            """.trimIndent(),
            sourceUrl = "https://example.com/feed.atom"
        ).items.single()

        val second = parser.parse(
            xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <feed xmlns="http://www.w3.org/2005/Atom">
                  <title>Atom Beispiel</title>
                  <entry>
                    <title>Zweiter Titel</title>
                    <author><name>Autor</name></author>
                    <updated>2026-04-04T10:00:00Z</updated>
                    <summary type="html">&lt;p&gt;Gleicher Inhalt&lt;/p&gt;</summary>
                  </entry>
                </feed>
            """.trimIndent(),
            sourceUrl = "https://example.com/feed.atom"
        ).items.single()

        assertEquals(first.uniqueKey, second.uniqueKey)
    }

    @Test
    fun atomXhtmlContentIsParsedWithoutCrashing() {
        val parsed = parser.parse(
            xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <feed xmlns="http://www.w3.org/2005/Atom">
                  <title>XHTML Feed</title>
                  <entry>
                    <id>tag:example.com,2026:1</id>
                    <title>XHTML Beitrag</title>
                    <link href="https://example.com/xhtml" />
                    <content type="xhtml">
                      <div xmlns="http://www.w3.org/1999/xhtml">
                        <p>Ein <strong>verschachtelter</strong> Inhalt.</p>
                      </div>
                    </content>
                  </entry>
                </feed>
            """.trimIndent(),
            sourceUrl = "https://example.com/feed.atom"
        )

        val article = parsed.items.single()
        assertEquals("XHTML Beitrag", article.title)
        assertEquals(ParsedContentSource.CONTENT, article.contentSource)
        assertTrue(article.contentHtml.contains("<div"))
        assertTrue(article.plainText.contains("Ein verschachtelter Inhalt."))
    }

    @Test
    fun defensivePayloadEarlyBoundsVeryLargeContentWithoutCrashing() {
        val largeHtml = buildString {
            append("<div>")
            repeat(220_000) { append('A') }
            append("</div>")
        }
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0" xmlns:content="http://purl.org/rss/1.0/modules/content/">
              <channel>
                <title>Large Feed</title>
                <item>
                  <title>Large Item</title>
                  <link>https://example.com/large</link>
                  <content:encoded><![CDATA[$largeHtml]]></content:encoded>
                </item>
              </channel>
            </rss>
        """.trimIndent()

        val parsed = parser.parse(
            payload = FetchedFeedPayload(
                responseBytes = xml.toByteArray(Charsets.UTF_8),
                charset = Charsets.UTF_8,
                byteSize = xml.toByteArray(Charsets.UTF_8).size,
                defensiveMode = true
            ),
            sourceUrl = "https://example.com/feed.xml"
        )

        val article = parsed.items.single()
        assertEquals("Large Item", article.title)
        assertTrue(article.contentHtml.length <= 180_000)
        assertTrue(article.plainText.isNotBlank())
    }

    @Test
    fun defensivePayloadSkipsSupplementalImageScanForExtremeBoundedContent() {
        val largeHtml = buildString {
            append("<section>")
            repeat(210_000) { append('B') }
            append("</section>")
        }
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0" xmlns:content="http://purl.org/rss/1.0/modules/content/">
              <channel>
                <title>Large Feed</title>
                <item>
                  <title>Extreme Item</title>
                  <link>https://example.com/extreme</link>
                  <content:encoded><![CDATA[$largeHtml]]></content:encoded>
                </item>
              </channel>
            </rss>
        """.trimIndent()

        val parsed = parser.parse(
            payload = FetchedFeedPayload(
                responseBytes = xml.toByteArray(Charsets.UTF_8),
                charset = Charsets.UTF_8,
                byteSize = xml.toByteArray(Charsets.UTF_8).size,
                defensiveMode = true
            ),
            sourceUrl = "https://example.com/feed.xml"
        )

        val article = parsed.items.single()
        assertEquals("Extreme Item", article.title)
        assertTrue(article.contentHtml.length <= 180_000)
        assertTrue(article.imageUrls.isEmpty())
    }

    @Test
    fun ordinaryPayloadBehaviorStaysUnchangedOnReaderPath() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0" xmlns:content="http://purl.org/rss/1.0/modules/content/">
              <channel>
                <title>Normal Feed</title>
                <item>
                  <title>Normal Item</title>
                  <link>https://example.com/normal</link>
                  <description><![CDATA[Teaser]]></description>
                  <content:encoded><![CDATA[<p>Normaler Inhalt</p><img src="https://example.com/image.jpg" />]]></content:encoded>
                </item>
              </channel>
            </rss>
        """.trimIndent()

        val parsed = parser.parse(
            payload = FetchedFeedPayload(
                responseBytes = xml.toByteArray(Charsets.UTF_8),
                charset = Charsets.UTF_8,
                byteSize = xml.toByteArray(Charsets.UTF_8).size,
                defensiveMode = false
            ),
            sourceUrl = "https://example.com/feed.xml"
        )

        val article = parsed.items.single()
        assertEquals("Normal Item", article.title)
        assertEquals(ParsedContentSource.CONTENT_ENCODED, article.contentSource)
        assertTrue(article.plainText.contains("Normaler Inhalt"))
        assertTrue(article.imageUrls.contains("https://example.com/image.jpg"))
    }

    @Test
    fun ordinaryPayloadParsingMatchesStringParsing() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0" xmlns:content="http://purl.org/rss/1.0/modules/content/">
              <channel>
                <title>Normal Feed</title>
                <link>https://example.com/</link>
                <item>
                  <title>Normal Item</title>
                  <link>https://example.com/normal</link>
                  <description><![CDATA[Teaser]]></description>
                  <content:encoded><![CDATA[<p>Normaler Inhalt</p><img src="https://example.com/image.jpg" />]]></content:encoded>
                </item>
              </channel>
            </rss>
        """.trimIndent()

        val parsedFromString = parser.parse(
            xml = xml,
            sourceUrl = "https://example.com/feed.xml"
        )
        val parsedFromPayload = parser.parse(
            payload = FetchedFeedPayload(
                responseBytes = xml.toByteArray(Charsets.UTF_8),
                charset = Charsets.UTF_8,
                byteSize = xml.toByteArray(Charsets.UTF_8).size,
                defensiveMode = false
            ),
            sourceUrl = "https://example.com/feed.xml"
        )

        assertEquals(parsedFromString.title, parsedFromPayload.title)
        assertEquals(parsedFromString.siteUrl, parsedFromPayload.siteUrl)
        assertEquals(parsedFromString.iconUrl, parsedFromPayload.iconUrl)
        assertEquals(parsedFromString.items.size, parsedFromPayload.items.size)

        val stringArticle = parsedFromString.items.single()
        val payloadArticle = parsedFromPayload.items.single()
        assertEquals(stringArticle.title, payloadArticle.title)
        assertEquals(stringArticle.link, payloadArticle.link)
        assertEquals(stringArticle.plainText, payloadArticle.plainText)
        assertEquals(stringArticle.contentHtml, payloadArticle.contentHtml)
        assertEquals(stringArticle.imageUrls, payloadArticle.imageUrls)
        assertEquals(stringArticle.contentSource, payloadArticle.contentSource)
    }

    @Test
    fun atomPayloadParsingMatchesStringParsing() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
              <title>Atom Beispiel</title>
              <link rel="self" href="https://example.com/feed.atom" />
              <link rel="alternate" href="https://example.com/" />
              <entry>
                <id>tag:example.com,2026:1</id>
                <title>Atom Beitrag</title>
                <link rel="alternate" href="https://example.com/posts/1" />
                <summary type="html">&lt;p&gt;Kurzinhalt&lt;/p&gt;</summary>
              </entry>
            </feed>
        """.trimIndent()

        val parsedFromString = parser.parse(
            xml = xml,
            sourceUrl = "https://example.com/feed.atom"
        )
        val parsedFromPayload = parser.parse(
            payload = FetchedFeedPayload(
                responseBytes = xml.toByteArray(Charsets.UTF_8),
                charset = Charsets.UTF_8,
                byteSize = xml.toByteArray(Charsets.UTF_8).size,
                defensiveMode = false
            ),
            sourceUrl = "https://example.com/feed.atom"
        )

        assertEquals(parsedFromString.title, parsedFromPayload.title)
        assertEquals(parsedFromString.siteUrl, parsedFromPayload.siteUrl)
        assertEquals(parsedFromString.iconUrl, parsedFromPayload.iconUrl)
        assertEquals(parsedFromString.items.size, parsedFromPayload.items.size)

        val stringArticle = parsedFromString.items.single()
        val payloadArticle = parsedFromPayload.items.single()
        assertEquals(stringArticle.title, payloadArticle.title)
        assertEquals(stringArticle.link, payloadArticle.link)
        assertEquals(stringArticle.plainText, payloadArticle.plainText)
        assertEquals(stringArticle.contentHtml, payloadArticle.contentHtml)
        assertEquals(stringArticle.imageUrls, payloadArticle.imageUrls)
        assertEquals(stringArticle.contentSource, payloadArticle.contentSource)
    }

}


