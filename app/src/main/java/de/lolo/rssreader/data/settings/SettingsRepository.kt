package de.lolo.rssreader.data.settings

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

// `internal` statt `private`, damit Tests den (jetzt ungenutzten) Alt-Key `show_unread_only`
// simulieren und belegen koennen, dass er keinen Feed-Zustand mehr beeinflusst.
internal val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "rss_reader_settings")

class SettingsRepository(context: Context) {
    private val appContext = context.applicationContext

    val settings: Flow<AppPreferences> = appContext.dataStore.data.map { preferences ->
        AppPreferences(
            refreshOnStart = preferences[REFRESH_ON_START] ?: false,
            refreshOnlyOnWifi = preferences[REFRESH_ONLY_ON_WIFI] ?: false,
            notificationsEnabled = preferences[NOTIFICATIONS_ENABLED] ?: false,
            notificationPermissionPromptShown = preferences[NOTIFICATION_PERMISSION_PROMPT_SHOWN] ?: false,
            showImages = preferences[SHOW_IMAGES] ?: true,
            refreshIntervalMinutes =
                preferences[REFRESH_INTERVAL_MINUTES]
                    ?: ((preferences[REFRESH_INTERVAL_HOURS] ?: 0) * 60),
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

    /**
     * Feedbezogener Filterzustand „Nur ungelesene Artikel".
     *
     * Der Wert ist ausschliesslich an die uebergebene [feedId] gebunden; ohne gespeicherten
     * Wert (neuer Feed oder Bestandsinstallation) gilt `false`. Der frueher globale Key
     * `show_unread_only` wird bewusst nicht mehr gelesen.
     */
    fun unreadOnlyFilterForFeed(feedId: Long): Flow<Boolean> =
        appContext.dataStore.data.map { preferences ->
            preferences[unreadOnlyFilterKey(feedId)] ?: false
        }

    /** Speichert den Filterzustand ausschliesslich fuer [feedId]; andere Feeds bleiben unberuehrt. */
    suspend fun setUnreadOnlyFilterForFeed(feedId: Long, value: Boolean) {
        updatePreference(unreadOnlyFilterKey(feedId), value)
    }

    suspend fun setRefreshOnStart(value: Boolean) {
        updatePreference(REFRESH_ON_START, value)
    }

    suspend fun setRefreshOnlyOnWifi(value: Boolean) {
        updatePreference(REFRESH_ONLY_ON_WIFI, value)
    }

    suspend fun setNotificationsEnabled(value: Boolean) {
        appContext.dataStore.edit { preferences ->
            preferences[NOTIFICATIONS_ENABLED] = value
            if (!value) {
                preferences[NOTIFICATION_PERMISSION_PROMPT_SHOWN] = false
            }
        }
    }

    suspend fun setNotificationPermissionPromptShown(value: Boolean) {
        updatePreference(NOTIFICATION_PERMISSION_PROMPT_SHOWN, value)
    }

    suspend fun setShowImages(value: Boolean) {
        updatePreference(SHOW_IMAGES, value)
    }

    suspend fun setRefreshIntervalMinutes(value: Int) {
        appContext.dataStore.edit { preferences ->
            preferences[REFRESH_INTERVAL_MINUTES] = value
            preferences.remove(REFRESH_INTERVAL_HOURS)
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
        val REFRESH_ONLY_ON_WIFI = booleanPreferencesKey("refresh_only_on_wifi")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val NOTIFICATION_PERMISSION_PROMPT_SHOWN =
            booleanPreferencesKey("notification_permission_prompt_shown")
        val SHOW_IMAGES = booleanPreferencesKey("show_images")
        val REFRESH_INTERVAL_MINUTES = intPreferencesKey("refresh_interval_minutes")
        val REFRESH_INTERVAL_HOURS = intPreferencesKey("refresh_interval_hours")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ENTRY_SORT_ORDER = stringPreferencesKey("entry_sort_order")
        val ARTICLE_BODY_TEXT_SIZE_OFFSET = intPreferencesKey("article_body_text_size_offset")

        /**
         * Praefix des feedbezogenen Filter-Keys. Der alte globale Key `show_unread_only`
         * bleibt bewusst ungenutzt im DataStore liegen (kein destruktiver Reset) und
         * bestimmt keinen Feed-Zustand mehr.
         */
        const val UNREAD_ONLY_FEED_KEY_PREFIX = "show_unread_only_feed_"

        fun unreadOnlyFilterKey(feedId: Long): Preferences.Key<Boolean> =
            booleanPreferencesKey("$UNREAD_ONLY_FEED_KEY_PREFIX$feedId")
    }
}


