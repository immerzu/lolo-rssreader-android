package com.example.rssreader

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.rssreader.ui.RssReaderApp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var notificationPermissionRequested = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as RssReaderApplication
        val permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) {
            notificationPermissionRequested = true
        }

        lifecycleScope.launch {
            app.settingsRepository.settings
                .map { settings -> settings.notificationsEnabled }
                .distinctUntilChanged()
                .collect { notificationsEnabled ->
                if (
                    notificationsEnabled &&
                    !notificationPermissionRequested &&
                    ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    notificationPermissionRequested = true
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }

        setContent {
            RssReaderApp(
                repository = app.repository,
                articleTranslationManager = app.articleTranslationManager,
                settingsRepository = app.settingsRepository,
                refreshScheduler = app.refreshScheduler
            )
        }
    }
}


