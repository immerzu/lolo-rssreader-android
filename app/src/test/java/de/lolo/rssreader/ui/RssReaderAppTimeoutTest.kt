package de.lolo.rssreader.ui

import de.lolo.rssreader.data.settings.AppPreferences
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class RssReaderAppTimeoutTest {

    @Test
    fun shouldResetReaderAfterInactivityReturnsFalseWithoutBackgroundTimestamp() {
        assertFalse(
            shouldResetReaderAfterInactivity(
                lastBackgroundedAtElapsedMs = 0L,
                nowElapsedMs = READER_INACTIVITY_RESET_TIMEOUT_MS
            )
        )
    }

    @Test
    fun shouldResetReaderAfterInactivityReturnsFalseBeforeTimeout() {
        assertFalse(
            shouldResetReaderAfterInactivity(
                lastBackgroundedAtElapsedMs = 1_000L,
                nowElapsedMs = 1_000L + READER_INACTIVITY_RESET_TIMEOUT_MS - 1L
            )
        )
    }

    @Test
    fun shouldResetReaderAfterInactivityReturnsTrueAtTimeout() {
        assertTrue(
            shouldResetReaderAfterInactivity(
                lastBackgroundedAtElapsedMs = 1_000L,
                nowElapsedMs = 1_000L + READER_INACTIVITY_RESET_TIMEOUT_MS
            )
        )
    }

    @Test
    fun shouldRefreshOnForegroundAfterInactivityReturnsFalseWhenAutomaticRefreshIsDisabled() {
        assertFalse(
            shouldRefreshOnForegroundAfterInactivity(
                settings = AppPreferences(
                    refreshOnStart = false,
                    refreshIntervalMinutes = 0
                ),
                lastBackgroundedAtElapsedMs = 1_000L,
                nowElapsedMs = 1_000L + FOREGROUND_REFRESH_AFTER_INACTIVITY_TIMEOUT_MS
            )
        )
    }

    @Test
    fun shouldRefreshOnForegroundAfterInactivityUsesDefaultTimeoutForRefreshOnStart() {
        assertFalse(
            shouldRefreshOnForegroundAfterInactivity(
                settings = AppPreferences(refreshOnStart = true),
                lastBackgroundedAtElapsedMs = 1_000L,
                nowElapsedMs = 1_000L + FOREGROUND_REFRESH_AFTER_INACTIVITY_TIMEOUT_MS - 1L
            )
        )
        assertTrue(
            shouldRefreshOnForegroundAfterInactivity(
                settings = AppPreferences(refreshOnStart = true),
                lastBackgroundedAtElapsedMs = 1_000L,
                nowElapsedMs = 1_000L + FOREGROUND_REFRESH_AFTER_INACTIVITY_TIMEOUT_MS
            )
        )
    }

    @Test
    fun shouldRefreshOnForegroundAfterInactivityUsesConfiguredBackgroundIntervalWhenPresent() {
        val settings = AppPreferences(refreshIntervalMinutes = 360)

        assertEquals(
            TimeUnit.HOURS.toMillis(6),
            foregroundRefreshInactivityTimeoutMs(settings)
        )
        assertFalse(
            shouldRefreshOnForegroundAfterInactivity(
                settings = settings,
                lastBackgroundedAtElapsedMs = 1_000L,
                nowElapsedMs = 1_000L + TimeUnit.HOURS.toMillis(6) - 1L
            )
        )
        assertTrue(
            shouldRefreshOnForegroundAfterInactivity(
                settings = settings,
                lastBackgroundedAtElapsedMs = 1_000L,
                nowElapsedMs = 1_000L + TimeUnit.HOURS.toMillis(6)
            )
        )
    }

    @Test
    fun shouldSkipForegroundRefreshForWifiOnlySettingRequiresWifiConnection() {
        assertTrue(
            shouldSkipForegroundRefreshForWifiOnlySetting(
                settings = AppPreferences(refreshOnlyOnWifi = true),
                hasWifiConnection = false
            )
        )
        assertFalse(
            shouldSkipForegroundRefreshForWifiOnlySetting(
                settings = AppPreferences(refreshOnlyOnWifi = true),
                hasWifiConnection = true
            )
        )
        assertFalse(
            shouldSkipForegroundRefreshForWifiOnlySetting(
                settings = AppPreferences(refreshOnlyOnWifi = false),
                hasWifiConnection = false
            )
        )
    }
}
