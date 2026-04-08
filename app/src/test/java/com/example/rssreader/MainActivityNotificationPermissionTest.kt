package com.example.rssreader

import com.example.rssreader.data.settings.AppPreferences
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainActivityNotificationPermissionTest {

    @Test
    fun shouldRequestNotificationPermissionReturnsTrueOnlyForEnabledNotYetPromptedUsers() {
        assertTrue(
            shouldRequestNotificationPermission(
                notificationsEnabled = true,
                notificationPermissionPromptShown = false,
                hasNotificationPermission = false
            )
        )
        assertFalse(
            shouldRequestNotificationPermission(
                notificationsEnabled = true,
                notificationPermissionPromptShown = true,
                hasNotificationPermission = false
            )
        )
        assertFalse(
            shouldRequestNotificationPermission(
                notificationsEnabled = false,
                notificationPermissionPromptShown = false,
                hasNotificationPermission = false
            )
        )
        assertFalse(
            shouldRequestNotificationPermission(
                notificationsEnabled = true,
                notificationPermissionPromptShown = false,
                hasNotificationPermission = true
            )
        )
    }

    @Test
    fun appPreferencesDefaultNotificationsAreDisabled() {
        assertFalse(AppPreferences().notificationsEnabled)
    }

}
