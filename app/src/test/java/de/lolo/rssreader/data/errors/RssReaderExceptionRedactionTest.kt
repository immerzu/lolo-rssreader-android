package de.lolo.rssreader.data.errors

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * RSS-DUP-004: Fehlermeldungen duerfen keine Feed-URL (und damit keine Zugangsdaten)
 * enthalten, weil DebugLogger die Message der obersten Exception schreibt.
 */
class RssReaderExceptionRedactionTest {

    private val secretUrl = "https://user:pass@example.com/feed.xml?token=abc"

    @Test
    fun invalidUrlKeepsTheUrlOutOfTheMessage() {
        val exception = RssReaderException.InvalidUrl(secretUrl)

        assertFalse(exception.message.orEmpty().contains("user"))
        assertFalse(exception.message.orEmpty().contains("pass"))
        assertFalse(exception.message.orEmpty().contains("abc"))
        assertEquals(secretUrl, exception.originalUrl)
    }

    @Test
    fun httpNotAllowedKeepsTheUrlOutOfTheMessage() {
        val exception = RssReaderException.HttpNotAllowed("http://user:pass@example.com/feed")

        assertFalse(exception.message.orEmpty().contains("pass"))
        assertFalse(exception.message.orEmpty().contains("example.com"))
    }

    @Test
    fun wrappedCauseIsNotPartOfTheTopLevelMessage() {
        val cause = IllegalArgumentException("unexpected url: $secretUrl")
        val exception = RssReaderException.InvalidUrl(secretUrl, cause)

        assertFalse("Die Cause mit URL darf nicht als Message erscheinen", exception.message.orEmpty().contains("token"))
    }
}
