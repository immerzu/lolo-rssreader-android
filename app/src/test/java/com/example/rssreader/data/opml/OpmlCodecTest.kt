package com.example.rssreader.data.opml

import com.example.rssreader.data.errors.RssReaderException
import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpmlCodecTest {

    @Test
    fun parseAcceptsOpmlWithDoctype() {
        val opml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE opml SYSTEM "http://opml.org/spec2.opml">
            <opml version="1.0">
              <head>
                <title>Feeds</title>
              </head>
              <body>
                <outline text="Beispiel" title="Beispiel" type="rss" xmlUrl="https://example.com/feed.xml" />
              </body>
            </opml>
        """.trimIndent()

        val entries = OpmlCodec.parse(ByteArrayInputStream(opml.toByteArray(Charsets.UTF_8)))

        assertEquals(1, entries.size)
        assertEquals("https://example.com/feed.xml", entries.single().url)
        assertEquals("Beispiel", entries.single().title)
    }

    @Test
    fun parseRejectsExternalEntityReferences() {
        val opml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE opml [
              <!ENTITY ext SYSTEM "https://example.com/external.ent">
            ]>
            <opml version="1.0">
              <body>
                <outline text="&ext;" xmlUrl="https://example.com/feed.xml" />
              </body>
            </opml>
        """.trimIndent()

        val result = runCatching {
            OpmlCodec.parse(ByteArrayInputStream(opml.toByteArray(Charsets.UTF_8)))
        }

        val entries = result.getOrNull()
        if (entries != null) {
            assertEquals(1, entries.size)
            assertEquals("https://example.com/feed.xml", entries.single().url)
            assertTrue(entries.single().title.isNullOrBlank())
        } else {
            assertTrue(result.exceptionOrNull() != null)
        }
    }

    @Test
    fun parseDeduplicatesTrimmedDuplicateUrlsWhileKeepingFirstTitle() {
        val opml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <opml version="1.0">
              <body>
                <outline text="Erster Titel" xmlUrl=" https://example.com/feed.xml " />
                <outline text="Zweiter Titel" xmlUrl="https://example.com/feed.xml" />
              </body>
            </opml>
        """.trimIndent()

        val entries = OpmlCodec.parse(ByteArrayInputStream(opml.toByteArray(Charsets.UTF_8)))

        assertEquals(
            listOf(OpmlFeedEntry(url = "https://example.com/feed.xml", title = "Erster Titel")),
            entries
        )
    }

    @Test
    fun parseSkipsOutlinesWithBlankXmlUrl() {
        val opml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <opml version="1.0">
              <body>
                <outline text="Leer" xmlUrl="   " />
                <outline text="Mit URL" xmlUrl="https://example.com/feed.xml" />
              </body>
            </opml>
        """.trimIndent()

        val entries = OpmlCodec.parse(ByteArrayInputStream(opml.toByteArray(Charsets.UTF_8)))

        assertEquals(
            listOf(OpmlFeedEntry(url = "https://example.com/feed.xml", title = "Mit URL")),
            entries
        )
    }

    @Test
    fun parseThrowsInvalidXmlForMalformedDocument() {
        val failure = runCatching {
            OpmlCodec.parse(ByteArrayInputStream("<opml>".toByteArray(Charsets.UTF_8)))
        }.exceptionOrNull()

        assertTrue(failure is RssReaderException.InvalidXml)
    }

    @Test
    fun parseKeepsNestedFeedOutlinesWhileIgnoringFolderOutlinesWithoutXmlUrl() {
        val opml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <opml version="1.0">
              <body>
                <outline text="Ordner">
                  <outline text="Erster Feed" xmlUrl="https://example.com/first.xml" />
                </outline>
                <outline text="Zweiter Feed" xmlUrl="https://example.com/second.xml" />
              </body>
            </opml>
        """.trimIndent()

        val entries = OpmlCodec.parse(ByteArrayInputStream(opml.toByteArray(Charsets.UTF_8)))

        assertEquals(
            listOf(
                OpmlFeedEntry(url = "https://example.com/first.xml", title = "Erster Feed"),
                OpmlFeedEntry(url = "https://example.com/second.xml", title = "Zweiter Feed")
            ),
            entries
        )
    }

    @Test
    fun buildAndParseRoundTripPreservesUrlsOrderAndTitles() {
        val entries = listOf(
            OpmlFeedEntry(url = "https://example.com/first.xml", title = "Erster Feed"),
            OpmlFeedEntry(url = "https://example.com/second.xml", title = null),
            OpmlFeedEntry(url = "https://example.com/third.xml", title = "Dritter Feed")
        )

        val xml = OpmlCodec.build(entries)
        val reparsed = OpmlCodec.parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))

        assertEquals(
            listOf(
                OpmlFeedEntry(url = "https://example.com/first.xml", title = "Erster Feed"),
                OpmlFeedEntry(url = "https://example.com/second.xml", title = "https://example.com/second.xml"),
                OpmlFeedEntry(url = "https://example.com/third.xml", title = "Dritter Feed")
            ),
            reparsed
        )
    }

    @Test
    fun buildEscapesXmlSensitiveCharactersInTitleAndUrl() {
        val xml = OpmlCodec.build(
            listOf(
                OpmlFeedEntry(
                    url = "https://example.com/feed?x=1&y=2",
                    title = """A & B <C> "D" 'E'"""
                )
            )
        )

        assertTrue(xml.contains("""text="A &amp; B &lt;C&gt; &quot;D&quot; &apos;E&apos;""""))
        assertTrue(xml.contains("""title="A &amp; B &lt;C&gt; &quot;D&quot; &apos;E&apos;""""))
        assertTrue(xml.contains("""xmlUrl="https://example.com/feed?x=1&amp;y=2""""))
    }

    @Test
    fun buildAndParseRoundTripPreservesEmptyFeedList() {
        val xml = OpmlCodec.build(emptyList())
        val reparsed = OpmlCodec.parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))

        assertTrue(reparsed.isEmpty())
    }

    @Test
    fun buildUsesEscapedUrlAsTextAndTitleFallbackWhenTitleIsNull() {
        val xml = OpmlCodec.build(
            listOf(
                OpmlFeedEntry(
                    url = "https://example.com/feed?x=1&y=2",
                    title = null
                )
            )
        )

        assertTrue(xml.contains("""text="https://example.com/feed?x=1&amp;y=2""""))
        assertTrue(xml.contains("""title="https://example.com/feed?x=1&amp;y=2""""))
    }

    @Test
    fun parsePrefersNonBlankTitleOverText() {
        val opml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <opml version="1.0">
              <body>
                <outline text="Text Titel" title="  Vorrang Titel  " xmlUrl="https://example.com/feed.xml" />
              </body>
            </opml>
        """.trimIndent()

        val entries = OpmlCodec.parse(ByteArrayInputStream(opml.toByteArray(Charsets.UTF_8)))

        assertEquals(
            listOf(OpmlFeedEntry(url = "https://example.com/feed.xml", title = "Vorrang Titel")),
            entries
        )
    }

    @Test
    fun parseFallsBackToTextWhenTitleIsBlank() {
        val opml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <opml version="1.0">
              <body>
                <outline text="Text Titel" title="   " xmlUrl="https://example.com/feed.xml" />
              </body>
            </opml>
        """.trimIndent()

        val entries = OpmlCodec.parse(ByteArrayInputStream(opml.toByteArray(Charsets.UTF_8)))

        assertEquals(
            listOf(OpmlFeedEntry(url = "https://example.com/feed.xml", title = "Text Titel")),
            entries
        )
    }

    @Test
    fun parseKeepsNullTitleWhenTitleAndTextAreBlank() {
        val opml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <opml version="1.0">
              <body>
                <outline text="   " title="" xmlUrl="https://example.com/feed.xml" />
              </body>
            </opml>
        """.trimIndent()

        val entries = OpmlCodec.parse(ByteArrayInputStream(opml.toByteArray(Charsets.UTF_8)))

        assertEquals(
            listOf(OpmlFeedEntry(url = "https://example.com/feed.xml", title = null)),
            entries
        )
    }
}
