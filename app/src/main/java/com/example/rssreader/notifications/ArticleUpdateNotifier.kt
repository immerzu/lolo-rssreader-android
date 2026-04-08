package com.example.rssreader.notifications

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
import com.example.rssreader.R
import com.example.rssreader.MainActivity
import com.example.rssreader.debug.DebugLogger

class ArticleUpdateNotifier(
    private val context: Context
) {
    fun ensureChannelAvailable() {
        ensureChannel()
    }

    fun showNewArticlesNotification(newArticles: Int, refreshedFeeds: Int) {
        val canPost = canPostNotifications()
        if (newArticles <= 0 || !canPost) {
            DebugLogger.i(
                "ArticleUpdateNotifier",
                "Benachrichtigung uebersprungen: newArticles=$newArticles, canPost=$canPost"
            )
            return
        }

        ensureChannel()

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }

        val contentIntent = PendingIntent.getActivity(
            context,
            1001,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (newArticles == 1) {
            "1 neuer Artikel"
        } else {
            "$newArticles neue Artikel"
        }

        val text = if (refreshedFeeds == 1) {
            "Aus 1 Feed im Hintergrund aktualisiert."
        } else {
            "Aus $refreshedFeeds Feeds im Hintergrund aktualisiert."
        }

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
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    private fun canPostNotifications(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "RSS-Aktualisierungen",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Benachrichtigt ueber neue Artikel nach Hintergrund-Aktualisierungen."
        }
        manager.createNotificationChannel(channel)
    }

    private companion object {
        const val CHANNEL_ID = "rss_updates"
        const val NOTIFICATION_ID = 1001
    }
}



