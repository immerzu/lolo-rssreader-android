package com.example.rssreader.data.opml

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
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
}
