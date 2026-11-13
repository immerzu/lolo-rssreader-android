package de.lolo.rssreader.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Regression RSS-A11: `published` und `updated` schrieben in dieselbe Variable.
 * Dadurch haengte das Datum von der Elementreihenfolge ab, und ein ungueltiger
 * Wert konnte einen gueltigen ueberschreiben.
 */
@RunWith(RobolectricTestRunner::class)
class FeedParserAtomDateTest {

    private val parser = FeedParser()

    @Test
    fun reihenfolgeVonPublishedUndUpdatedAendertDasDatumNicht() {
        val publishedFirst = parser.parse(
            xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <feed xmlns="http://www.w3.org/2005/Atom">
                  <title>Atom Beispiel</title>
                  <entry>
                    <title>Artikel</title>
                    <author><name>Autor</name></author>
                    <published>2026-04-04T10:00:00Z</published>
                    <updated>2026-05-05T10:00:00Z</updated>
                    <summary type="html">&lt;p&gt;Text&lt;/p&gt;</summary>
                  </entry>
                </feed>
            """.trimIndent(),
            sourceUrl = "https://example.com/feed.atom"
        ).items.single()

        val updatedFirst = parser.parse(
            xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <feed xmlns="http://www.w3.org/2005/Atom">
                  <title>Atom Beispiel</title>
                  <entry>
                    <title>Artikel</title>
                    <author><name>Autor</name></author>
                    <updated>2026-05-05T10:00:00Z</updated>
                    <published>2026-04-04T10:00:00Z</published>
                    <summary type="html">&lt;p&gt;Text&lt;/p&gt;</summary>
                  </entry>
                </feed>
            """.trimIndent(),
            sourceUrl = "https://example.com/feed.atom"
        ).items.single()

        assertNotNull(publishedFirst.publishedAt)
        assertEquals(publishedFirst.publishedAt, updatedFirst.publishedAt)
    }

    @Test
    fun ungueltigesPublishedUeberschreibtGueltigesUpdatedNicht() {
        val article = parser.parse(
            xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <feed xmlns="http://www.w3.org/2005/Atom">
                  <title>Atom Beispiel</title>
                  <entry>
                    <title>Artikel</title>
                    <author><name>Autor</name></author>
                    <published>kein datum</published>
                    <updated>2026-05-05T10:00:00Z</updated>
                    <summary type="html">&lt;p&gt;Text&lt;/p&gt;</summary>
                  </entry>
                </feed>
            """.trimIndent(),
            sourceUrl = "https://example.com/feed.atom"
        ).items.single()

        assertNotNull(article.publishedAt)
    }
}
