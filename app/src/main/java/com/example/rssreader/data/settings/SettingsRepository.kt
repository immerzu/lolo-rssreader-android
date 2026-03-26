package com.example.rssreader.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "rss_reader_settings")

class SettingsRepository(context: Context) {
    private val appContext = context.applicationContext

    val settings: Flow<AppPreferences> = appContext.dataStore.data.map { preferences ->
        AppPreferences(
            refreshOnStart = preferences[REFRESH_ON_START] ?: false,
            notificationsEnabled = preferences[NOTIFICATIONS_ENABLED] ?: true,
            showImages = preferences[SHOW_IMAGES] ?: true,
            refreshIntervalHours = preferences[REFRESH_INTERVAL_HOURS] ?: 0,
            themeMode = preferences[THEME_MODE]
                ?.let { storedValue -> ThemeMode.entries.firstOrNull { it.name == storedValue } }
                ?: ThemeMode.DARK,
            entrySortOrder = preferences[ENTRY_SORT_ORDER]
                ?.let { storedValue -> EntrySortOrder.entries.firstOrNull { it.name == storedValue } }
                ?: EntrySortOrder.NEWEST_FIRST,
            articleBodyTextSizeOffset = preferences[ARTICLE_BODY_TEXT_SIZE_OFFSET]
                ?.coerceIn(-2, 2)
                ?: 0
        )
    }

    suspend fun setRefreshOnStart(value: Boolean) {
        updatePreference(REFRESH_ON_START, value)
    }

    suspend fun setNotificationsEnabled(value: Boolean) {
        updatePreference(NOTIFICATIONS_ENABLED, value)
    }

    suspend fun setShowImages(value: Boolean) {
        updatePreference(SHOW_IMAGES, value)
    }

    suspend fun setRefreshIntervalHours(value: Int) {
        appContext.dataStore.edit { preferences ->
            preferences[REFRESH_INTERVAL_HOURS] = value
        }
    }

    suspend fun setThemeMode(value: ThemeMode) {
        appContext.dataStore.edit { preferences ->
            preferences[THEME_MODE] = value.name
        }
    }

    suspend fun setEntrySortOrder(value: EntrySortOrder) {
        appContext.dataStore.edit { preferences ->
            preferences[ENTRY_SORT_ORDER] = value.name
        }
    }

    suspend fun setArticleBodyTextSizeOffset(value: Int) {
        appContext.dataStore.edit { preferences ->
            preferences[ARTICLE_BODY_TEXT_SIZE_OFFSET] = value.coerceIn(-2, 2)
        }
    }

    suspend fun getCurrentSettings(): AppPreferences = settings.first()

    private suspend fun updatePreference(key: Preferences.Key<Boolean>, value: Boolean) {
        appContext.dataStore.edit { preferences ->
            preferences[key] = value
        }
    }

    private companion object {
        val REFRESH_ON_START = booleanPreferencesKey("refresh_on_start")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val SHOW_IMAGES = booleanPreferencesKey("show_images")
        val REFRESH_INTERVAL_HOURS = intPreferencesKey("refresh_interval_hours")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ENTRY_SORT_ORDER = stringPreferencesKey("entry_sort_order")
        val ARTICLE_BODY_TEXT_SIZE_OFFSET = intPreferencesKey("article_body_text_size_offset")
    }
}


========================================================================================================================