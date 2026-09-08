package de.lolo.rssreader.ui

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ArticleSharingTest {

    @Test
    fun buildsSendIntentWithTitleAndLink() {
        val intent = buildArticleShareIntent("Mein Titel", "https://example.com/artikel")

        assertNotNull(intent)
        assertEquals(Intent.ACTION_SEND, intent!!.action)
        assertEquals("text/plain", intent.type)
        assertEquals("Mein Titel", intent.getStringExtra(Intent.EXTRA_SUBJECT))
        assertEquals("Mein Titel\n\nhttps://example.com/artikel", intent.getStringExtra(Intent.EXTRA_TEXT))
    }

    @Test
    fun buildsSendIntentWithLinkOnly() {
        val intent = buildArticleShareIntent(null, "https://example.com/artikel")

        assertNotNull(intent)
        assertEquals(Intent.ACTION_SEND, intent!!.action)
        assertNull(intent.getStringExtra(Intent.EXTRA_SUBJECT))
        assertEquals("https://example.com/artikel", intent.getStringExtra(Intent.EXTRA_TEXT))
    }

    @Test
    fun buildsSendIntentWithTitleOnly() {
        val intent = buildArticleShareIntent("Nur Titel", "   ")

        assertNotNull(intent)
        assertEquals("Nur Titel", intent!!.getStringExtra(Intent.EXTRA_TEXT))
        assertEquals("Nur Titel", intent.getStringExtra(Intent.EXTRA_SUBJECT))
    }

    @Test
    fun returnsNullWhenNothingToShare() {
        assertNull(buildArticleShareIntent(null, null))
        assertNull(buildArticleShareIntent("   ", ""))
    }

    @Test
    fun shareArticleStartsChooser() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        val started = shareArticle(context, "Titel", "https://example.com/a")

        assertTrue(started)
        val nextIntent = org.robolectric.Shadows.shadowOf(context as android.app.Application).nextStartedActivity
        assertNotNull(nextIntent)
        assertEquals(Intent.ACTION_CHOOSER, nextIntent!!.action)
        val innerIntent = nextIntent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        assertNotNull(innerIntent)
        assertEquals(Intent.ACTION_SEND, innerIntent!!.action)
        assertEquals("Titel\n\nhttps://example.com/a", innerIntent.getStringExtra(Intent.EXTRA_TEXT))
    }

    @Test
    fun shareArticleReturnsFalseWithoutContent() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        assertFalse(shareArticle(context, null, null))
    }
}
