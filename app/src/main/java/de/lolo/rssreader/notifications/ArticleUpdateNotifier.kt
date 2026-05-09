package de.lolo.rssreader.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import de.lolo.rssreader.R
import de.lolo.rssreader.MainActivity
import de.lolo.rssreader.debug.DebugLogger

internal const val EXTRA_OPENED_FROM_NOTIFICATION =
    "de.lolo.rssreader.extra.OPENED_FROM_NOTIFICATION"
internal const val EXTRA_NOTIFICATION_CHANNEL_ID =
    "de.lolo.rssreader.extra.NOTIFICATION_CHANNEL_ID"
internal const val EXTRA_NOTIFICATION_ARTICLE_COUNT =
    "de.lolo.rssreader.extra.NOTIFICATION_ARTICLE_COUNT"

class ArticleUpdateNotifier(
    private val context: Context
) {
    fun ensureChannelAvailable() {
        ensureChannel()
    }

    fun showNewArticlesNotification(newArticles: Int, refreshedFeeds: Int) {
        if (newArticles <= 0) {
            DebugLogger.i(
                "ArticleUpdateNotifier",
                "Benachrichtigung uebersprungen: newArticles=$newArticles"
            )
            return
        }
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            DebugLogger.i(
                "ArticleUpdateNotifier",
                "Benachrichtigung uebersprungen: newArticles=$newArticles, canPost=false"
            )
            return
        }

        ensureChannel()

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(EXTRA_OPENED_FROM_NOTIFICATION, true)
            putExtra(EXTRA_NOTIFICATION_CHANNEL_ID, CHANNEL_ID)
            putExtra(EXTRA_NOTIFICATION_ARTICLE_COUNT, newArticles)
        }

        val contentIntent = PendingIntent.getActivity(
            context,
            1001,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val (titleResId, textResId) = notificationTextResIds(newArticles)
        val title = context.getString(titleResId)
        val text = context.getString(textResId)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        DebugLogger.i(
            "ArticleUpdateNotifier",
            "Benachrichtigung erstellt: newArticles=$newArticles, refreshedFeeds=$refreshedFeeds, requestCode=1001, intentFlags=0x${launchIntent.flags.toString(16)}"
        )
        DebugLogger.i(
            "ArticleUpdateNotifier",
            "Benachrichtigung wird an Android uebergeben: channelId=$CHANNEL_ID, notificationId=$NOTIFICATION_ID, newArticles=$newArticles, refreshedFeeds=$refreshedFeeds"
        )
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        DebugLogger.i(
            "ArticleUpdateNotifier",
            "Benachrichtigung an Android uebergeben: channelId=$CHANNEL_ID, notificationId=$NOTIFICATION_ID"
        )
    }

    private fun ensureChannel() {
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.notification_channel_description)
        }
        manager.createNotificationChannel(channel)
    }

    private companion object {
        const val CHANNEL_ID = "rss_updates"
        const val NOTIFICATION_ID = 1001
    }
}

internal fun notificationTextResIds(newArticles: Int): Pair<Int, Int> {
    return if (newArticles == 1) {
        R.string.notification_new_message_title to R.string.notification_new_message_text
    } else {
        R.string.notification_new_messages_title to R.string.notification_new_messages_text
    }
}

internal fun isNotificationOpenIntent(intent: Intent?): Boolean {
    return intent?.getBooleanExtra(EXTRA_OPENED_FROM_NOTIFICATION, false) == true
}



