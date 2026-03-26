package com.example.rssreader.data.settings

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

enum class EntrySortOrder {
    NEWEST_FIRST,
    OLDEST_FIRST
}

data class AppPreferences(
    val refreshOnStart: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val showImages: Boolean = true,
    val refreshIntervalHours: Int = 0,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val entrySortOrder: EntrySortOrder = EntrySortOrder.NEWEST_FIRST,
    val articleBodyTextSizeOffset: Int = 0
)
