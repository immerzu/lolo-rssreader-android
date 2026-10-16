package de.lolo.rssreader.data.settings

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
    val refreshOnlyOnWifi: Boolean = false,
    val notificationsEnabled: Boolean = false,
    val notificationPermissionPromptShown: Boolean = false,
    val showImages: Boolean = true,
    val refreshIntervalMinutes: Int = 0,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val entrySortOrder: EntrySortOrder = EntrySortOrder.NEWEST_FIRST,
    val articleBodyTextSizeOffset: Int = 0
)

// Hinweis: Der Filter „Nur ungelesene Artikel" ist seit Issue #12 feedbezogen und liegt
// deshalb nicht in AppPreferences, sondern in einem eigenen DataStore-Key je feedId
// (siehe SettingsRepository.unreadOnlyFilterForFeed / setUnreadOnlyFilterForFeed).


