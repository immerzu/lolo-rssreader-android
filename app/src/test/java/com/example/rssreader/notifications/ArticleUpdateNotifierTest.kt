package com.example.rssreader.notifications

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.example.rssreader.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class ArticleUpdateNotifierTest {

    @Test
    fun notificationTextResIdsUsesSingularOnlyForSingleArticle() {
        assertEquals(
            R.string.notification_new_message_title to R.string.notification_new_message_text,
            notificationTextResIds(1)
        )
        assertEquals(
            R.string.notification_new_messages_title to R.string.notification_new_messages_text,
            notificationTextResIds(2)
        )
    }

    @Test
    fun isNotificationOpenIntentRecognizesIntentMarker() {
        val intent = Intent()
            .putExtra(EXTRA_OPENED_FROM_NOTIFICATION, true)
            .putExtra(EXTRA_NOTIFICATION_CHANNEL_ID, "rss_updates")
            .putExtra(EXTRA_NOTIFICATION_ARTICLE_COUNT, 3)

        assertTrue(isNotificationOpenIntent(intent))
        assertFalse(isNotificationOpenIntent(Intent()))
        assertFalse(isNotificationOpenIntent(null))
    }

    @Test
    fun showNewArticlesNotificationPostsSingularNotificationWithMarkerIntent() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)

        ArticleUpdateNotifier(context).showNewArticlesNotification(
            newArticles = 1,
            refreshedFeeds = 5
        )

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val notification = shadowOf(notificationManager).allNotifications.single()

        assertEquals(
            context.getString(R.string.notification_new_message_title),
            notification.extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString()
        )
        assertEquals(
            context.getString(R.string.notification_new_message_text),
            notification.extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString()
        )

        val launchIntent = shadowOf(notification.contentIntent).savedIntent
        assertTrue(launchIntent.getBooleanExtra(EXTRA_OPENED_FROM_NOTIFICATION, false))
        assertEquals("rss_updates", launchIntent.getStringExtra(EXTRA_NOTIFICATION_CHANNEL_ID))
        assertEquals(1, launchIntent.getIntExtra(EXTRA_NOTIFICATION_ARTICLE_COUNT, -1))
    }

    @Test
    fun showNewArticlesNotificationPostsPluralNotificationWithCorrectArticleCount() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)

        ArticleUpdateNotifier(context).showNewArticlesNotification(
            newArticles = 2,
            refreshedFeeds = 5
        )

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val notification = shadowOf(notificationManager).allNotifications.single()

        assertEquals(
            context.getString(R.string.notification_new_messages_title),
            notification.extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString()
        )
        assertEquals(
            context.getString(R.string.notification_new_messages_text),
            notification.extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString()
        )

        val launchIntent = shadowOf(notification.contentIntent).savedIntent
        assertTrue(launchIntent.getBooleanExtra(EXTRA_OPENED_FROM_NOTIFICATION, false))
        assertEquals("rss_updates", launchIntent.getStringExtra(EXTRA_NOTIFICATION_CHANNEL_ID))
        assertEquals(2, launchIntent.getIntExtra(EXTRA_NOTIFICATION_ARTICLE_COUNT, -1))
    }

    @Test
    @Config(sdk = [33])
    fun showNewArticlesNotificationSkipsWhenPermissionMissing() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()

        ArticleUpdateNotifier(context).showNewArticlesNotification(
            newArticles = 2,
            refreshedFeeds = 5
        )

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        assertTrue(shadowOf(notificationManager).allNotifications.isEmpty())
    }
}
