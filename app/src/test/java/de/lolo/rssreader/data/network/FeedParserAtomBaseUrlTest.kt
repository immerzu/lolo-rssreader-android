package de.lolo.rssreader.data.network

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Regression RSS-A09: Der Atom-Parser uebernahm `href` unveraendert und ignorierte
 * ein geerbtes `xml:base`. Relative Links wurden dadurch als "item" gespeichert.
 */
@RunWith(RobolectricTestRunner::class)
class FeedParserAtomBaseUrlTest {

    private val parser = FeedParser()

    @Test
    fun xmlBaseAmFeedWirdFuerRelativeLinksVerwendet() {
        val parsed = parser.parse(
            xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <feed xmlns="http://www.w3.org/2005/Atom" xml:base="https://example.com/news/">
                  <title>Beispiel</title>
                  <entry>
                    <id>urn:example:1</id>
                    <title>Artikel</title>
                    <link href="item"/>
                    <summary type="html">&lt;p&gt;Text&lt;/p&gt;</summary>
                  </entry>
                </feed>
            """.trimIndent(),
            sourceUrl = "https://example.com/feed.atom"
        )

        assertEquals("https://example.com/news/item", parsed.items.single().link)
    }

    @Test
    fun xmlBaseAmLinkHatVorrang() {
        val parsed = parser.parse(
            xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <feed xmlns="http://www.w3.org/2005/Atom">
                  <title>Beispiel</title>
                  <entry>
                    <id>urn:example:2</id>
                    <title>Artikel</title>
                    <link xml:base="https://other.example/pfad/" href="seite"/>
                    <summary type="html">&lt;p&gt;Text&lt;/p&gt;</summary>
                  </entry>
                </feed>
            """.trimIndent(),
            sourceUrl = "https://example.com/feed.atom"
        )

        assertEquals("https://other.example/pfad/seite", parsed.items.single().link)
    }
}
