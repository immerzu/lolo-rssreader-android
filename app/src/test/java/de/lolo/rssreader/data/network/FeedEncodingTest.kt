package de.lolo.rssreader.data.network

import java.nio.charset.Charset
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Regression RSS-A10: UTF-16 mit BOM wurde ohne passenden HTTP-Header nicht erkannt
 * (NUL-Zeichen nach der Ersatzdekodierung), die charset-Suche war
 * gross-/kleinschreibungssensitiv und der Import dekodierte immer als UTF-8.
 */
class FeedEncodingTest {

    @Test
    fun utf16LeMitBomWirdOhneHttpHeaderErkannt() {
        val xml = "<?xml version=\"1.0\"?><rss><channel><title>Test</title></channel></rss>"
        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + xml.toByteArray(Charsets.UTF_16LE)

        assertEquals(Charsets.UTF_16LE, FeedEncoding.resolveCharset(bytes, contentTypeHeader = null))
        assertEquals(xml, FeedEncoding.decode(bytes))
    }

    @Test
    fun utf16BeMitBomWirdOhneHttpHeaderErkannt() {
        val xml = "<?xml version=\"1.0\"?><rss/>"
        val bytes = byteArrayOf(0xFE.toByte(), 0xFF.toByte()) + xml.toByteArray(Charsets.UTF_16BE)

        assertEquals(Charsets.UTF_16BE, FeedEncoding.resolveCharset(bytes, contentTypeHeader = null))
        assertEquals(xml, FeedEncoding.decode(bytes))
    }

    @Test
    fun latin1MitPrologWirdKorrektDekodiert() {
        val xml = "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?><rss><channel><title>Köln</title></channel></rss>"
        val bytes = xml.toByteArray(Charsets.ISO_8859_1)

        assertEquals(Charsets.ISO_8859_1, FeedEncoding.resolveCharset(bytes, contentTypeHeader = null))
        assertEquals(xml, FeedEncoding.decode(bytes))
    }

    @Test
    fun charsetImHeaderIstGrossKleinUnabhaengig() {
        val bytes = "<?xml version=\"1.0\"?><rss/>".toByteArray(Charsets.UTF_8)

        assertEquals(
            Charsets.ISO_8859_1,
            FeedEncoding.resolveCharset(bytes, contentTypeHeader = "text/xml; CHARSET=ISO-8859-1")
        )
    }
}
