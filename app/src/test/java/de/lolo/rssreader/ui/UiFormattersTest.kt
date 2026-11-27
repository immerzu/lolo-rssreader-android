package de.lolo.rssreader.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * RSS-DUP-001-014: gemeinsame relative Zeitformatierung mit optionalem Label.
 * Nur der Null-Fall ist ohne Android-Laufzeit pruefbar (kein DateUtils-Aufruf).
 */
class UiFormattersTest {

    @Test
    fun nullTimestampUsesDefaultNeverLabel() {
        assertEquals("nie", formatRelativeTime(null))
    }

    @Test
    fun nullTimestampUsesCustomNeverLabel() {
        assertEquals("noch nie", formatRelativeTime(null, neverLabel = "noch nie"))
    }
}
