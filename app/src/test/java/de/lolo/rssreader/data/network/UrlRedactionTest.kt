package de.lolo.rssreader.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlRedactionTest {

    @Test
    fun maskiertUserinfoUndSensibleQueryParameter() {
        val entry = UrlRedaction.redactForLog(
            "https://user:secret@example.com/feed.xml?token=abc123&lang=de&api_key=xyz"
        )

        assertFalse(entry.contains("user"))
        assertFalse(entry.contains("secret"))
        assertFalse(entry.contains("abc123"))
        assertFalse(entry.contains("xyz"))
        assertTrue(entry.startsWith("https://example.com/feed.xml"))
        assertTrue(entry.contains("token=***"))
        assertTrue(entry.contains("api_key=***"))
        assertTrue(entry.contains("lang=de"))
    }

    @Test
    fun entferntFragmentMitToken() {
        val entry = UrlRedaction.redactForLog("https://example.com/feed.xml#access_token=leak")

        assertEquals("https://example.com/feed.xml", entry)
    }

    @Test
    fun liefertLeerenStringFuerFehlendeUrl() {
        assertEquals("", UrlRedaction.redactForLog(null))
        assertEquals("", UrlRedaction.redactForLog("   "))
    }

    @Test
    fun unlesbareUrlWirdNichtRohAusgegeben() {
        assertEquals("<url>", UrlRedaction.redactForLog("nicht mal eine url"))
    }
}
