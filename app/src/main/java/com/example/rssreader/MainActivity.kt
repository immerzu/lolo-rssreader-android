package com.example.rssreader

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.rssreader.debug.DebugLogger
import com.example.rssreader.notifications.ArticleUpdateNotifier
import com.example.rssreader.notifications.EXTRA_NOTIFICATION_ARTICLE_COUNT
import com.example.rssreader.notifications.EXTRA_NOTIFICATION_CHANNEL_ID
import com.example.rssreader.notifications.isNotificationOpenIntent
import com.example.rssreader.ui.RssReaderApp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var notificationPermissionRequested = false

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(DebugLocaleManager.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DebugLogger.i("MainActivity", "onCreate: savedInstanceState=${savedInstanceState != null}")
        logIntent("onCreate", intent)

        val app = application as RssReaderApplication
        val articleUpdateNotifier = ArticleUpdateNotifier(this)
        val permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) {
            notificationPermissionRequested = true
        }

        lifecycleScope.launch {
            app.settingsRepository.settings
                .map { settings ->
                    settings.notificationsEnabled to settings.notificationPermissionPromptShown
                }
                .distinctUntilChanged()
                .collect { (notificationsEnabled, notificationPermissionPromptShown) ->
                    if (notificationsEnabled) {
                        articleUpdateNotifier.ensureChannelAvailable()
                    }
                    val hasNotificationPermission =
                        ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) == PackageManager.PERMISSION_GRANTED

                if (
                    !notificationPermissionRequested &&
                    shouldRequestNotificationPermission(
                        notificationsEnabled = notificationsEnabled,
                        notificationPermissionPromptShown = notificationPermissionPromptShown,
                        hasNotificationPermission = hasNotificationPermission
                    )
                ) {
                    notificationPermissionRequested = true
                    app.settingsRepository.setNotificationPermissionPromptShown(true)
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }

        setContent {
            RssReaderApp(
                repository = app.repository,
                settingsRepository = app.settingsRepository,
                refreshScheduler = app.refreshScheduler
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        DebugLogger.i("MainActivity", "onNewIntent")
        logIntent("onNewIntent", intent)
    }

    override fun onStart() {
        super.onStart()
        DebugLogger.i("MainActivity", "onStart")
    }

    override fun onResume() {
        super.onResume()
        DebugLogger.i("MainActivity", "onResume")
    }

    override fun onPause() {
        DebugLogger.i("MainActivity", "onPause")
        super.onPause()
    }

    override fun onStop() {
        DebugLogger.i("MainActivity", "onStop")
        super.onStop()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        DebugLogger.d("MainActivity", "onWindowFocusChanged: hasFocus=$hasFocus")
    }

    private fun logIntent(source: String, intent: Intent?) {
        if (intent == null) {
            DebugLogger.i("MainActivity", "$source: intent=null")
            return
        }
        val extrasSummary = intent.extras
            ?.keySet()
            ?.sorted()
            ?.joinToString(prefix = "[", postfix = "]")
            ?: "[]"
        DebugLogger.i(
            "MainActivity",
            "$source: action=${intent.action}, data=${intent.dataString}, flags=0x${intent.flags.toString(16)}, extras=$extrasSummary"
        )
        if (isNotificationOpenIntent(intent)) {
            DebugLogger.i(
                "MainActivity",
                "$source: von RSS-Benachrichtigung geoeffnet: channelId=${intent.getStringExtra(EXTRA_NOTIFICATION_CHANNEL_ID)}, articleCount=${intent.getIntExtra(EXTRA_NOTIFICATION_ARTICLE_COUNT, -1)}"
            )
        }
    }
}

internal fun shouldRequestNotificationPermission(
    notificationsEnabled: Boolean,
    notificationPermissionPromptShown: Boolean,
    hasNotificationPermission: Boolean
): Boolean {
    return notificationsEnabled &&
        !notificationPermissionPromptShown &&
        !hasNotificationPermission
}


