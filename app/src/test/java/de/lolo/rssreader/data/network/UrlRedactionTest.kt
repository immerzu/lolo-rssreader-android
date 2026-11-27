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

    @Test
    fun kodierteParameternamenWerdenEbenfallsMaskiert() {
        val entry = UrlRedaction.redactForLog(
            "https://example.com/feed?to%6Ben=leak1&%61pi_key=leak2&lang=de"
        )

        assertFalse("kodierter token-Wert darf nicht im Log stehen", entry.contains("leak1"))
        assertFalse("kodierter api_key-Wert darf nicht im Log stehen", entry.contains("leak2"))
        assertTrue(entry.contains("lang=de"))
    }

    @Test
    fun grossschreibungSensiblerNamenWirdErkannt() {
        val entry = UrlRedaction.redactForLog(
            "https://example.com/feed?TOKEN=leak1&Access_Token=leak2&Password=leak3"
        )

        assertFalse(entry.contains("leak1"))
        assertFalse(entry.contains("leak2"))
        assertFalse(entry.contains("leak3"))
    }

    @Test
    fun ungueltigeProzentfolgeWirdNichtRohAusgegeben() {
        val entry = UrlRedaction.redactForLog("https://example.com/feed?to%ZZken=leak&lang=de")

        // URI lehnt die ungueltige Prozentfolge ab; die Roh-URL darf nicht ins Log.
        assertEquals("<url>", entry)
    }

    @Test
    fun parameterOhneWertWirdMaskiert() {
        val entry = UrlRedaction.redactForLog("https://example.com/feed?token&lang=de")

        assertEquals("https://example.com/feed?token=***&lang=de", entry)
    }
}
