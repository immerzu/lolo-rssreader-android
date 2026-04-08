package com.example.rssreader.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
}
