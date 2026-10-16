package de.lolo.rssreader.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.robolectric.RobolectricTestRunner

/**
 * Issue #12 (feedbezogen): Jeder Feed hat einen eigenen, dauerhaft in DataStore gespeicherten
 * Filterzustand „Nur ungelesene Artikel". Der frueher globale Key `show_unread_only` darf
 * keinen Feed-Zustand mehr bestimmen.
 *
 * Die Reihenfolge ist fixiert (Default-Test vor allen Schreibvorgaengen), falls sich
 * Testmethoden einer Klasse denselben DataStore teilen; umgekehrt sind die Tests auch
 * bei isoliertem Store pro Methode gueltig.
 */
@RunWith(RobolectricTestRunner::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class SettingsFilterPreferenceTest {

    private fun context(): Context = ApplicationProvider.getApplicationContext()

    /** T1 – Neuer Feed ohne gespeicherten Wert: Filter deaktiviert (alle Artikel sichtbar). */
    @Test
    fun a_newFeedDefaultsToDisabledFilter() = runBlocking {
        val repository = SettingsRepository(context())

        assertFalse(repository.unreadOnlyFilterForFeed(FEED_A).first())
    }

    /**
     * T2 (Speicherung Feed A), T3 (Unabhaengigkeit Feed B), T4 (mehrere Feeds),
     * T5 (Deaktivierung ohne Wirkung auf andere Feeds), T6 (Wiederherstellung ueber
     * eine neue Repository-Instanz).
     */
    @Test
    fun b_filterStateIsStoredPerFeedAndSurvivesNewRepositoryInstance() = runBlocking {
        val appContext = context()
        val repository = SettingsRepository(appContext)

        // T2 – Aktivierung wirkt nur fuer Feed A.
        repository.setUnreadOnlyFilterForFeed(FEED_A, true)
        assertTrue(repository.unreadOnlyFilterForFeed(FEED_A).first())

        // T3 – Feed B bleibt deaktiviert.
        assertFalse(repository.unreadOnlyFilterForFeed(FEED_B).first())

        // T4 – Feed C separat aktivierbar; A und C aktiv, B deaktiviert.
        repository.setUnreadOnlyFilterForFeed(FEED_C, true)
        assertTrue(repository.unreadOnlyFilterForFeed(FEED_A).first())
        assertTrue(repository.unreadOnlyFilterForFeed(FEED_C).first())
        assertFalse(repository.unreadOnlyFilterForFeed(FEED_B).first())

        // T6 – eine neue Repository-Instanz liest dieselben feedbezogenen Werte.
        val freshRepository = SettingsRepository(appContext)
        assertTrue(freshRepository.unreadOnlyFilterForFeed(FEED_A).first())
        assertFalse(freshRepository.unreadOnlyFilterForFeed(FEED_B).first())
        assertTrue(freshRepository.unreadOnlyFilterForFeed(FEED_C).first())

        // T5 – Deaktivierung in Feed A laesst Feed C unveraendert.
        repository.setUnreadOnlyFilterForFeed(FEED_A, false)
        assertFalse(repository.unreadOnlyFilterForFeed(FEED_A).first())
        assertTrue(repository.unreadOnlyFilterForFeed(FEED_C).first())
    }

    /** T7 – Ein bestehender globaler Alt-Wert (`show_unread_only`) beeinflusst keinen Feed. */
    @Test
    fun c_legacyGlobalValueDoesNotAffectPerFeedState() = runBlocking {
        val appContext = context()
        appContext.dataStore.edit { preferences ->
            preferences[booleanPreferencesKey("show_unread_only")] = true
        }
        val repository = SettingsRepository(appContext)

        assertFalse(repository.unreadOnlyFilterForFeed(FEED_A).first())
        assertFalse(repository.unreadOnlyFilterForFeed(FEED_B).first())

        // Feedbezogene Aktivierung bleibt unabhaengig vom Alt-Wert.
        repository.setUnreadOnlyFilterForFeed(FEED_A, true)
        assertTrue(repository.unreadOnlyFilterForFeed(FEED_A).first())
        assertFalse(repository.unreadOnlyFilterForFeed(FEED_B).first())
    }

    /** Andere Einstellungen bleiben beim Schreiben eines Feed-Filterzustands unveraendert. */
    @Test
    fun d_filterWriteKeepsOtherSettingsIntact() = runBlocking {
        val appContext = context()
        val repository = SettingsRepository(appContext)

        repository.setThemeMode(ThemeMode.LIGHT)
        repository.setEntrySortOrder(EntrySortOrder.OLDEST_FIRST)
        repository.setUnreadOnlyFilterForFeed(FEED_A, true)

        val settings = SettingsRepository(appContext).getCurrentSettings()

        assertEquals(ThemeMode.LIGHT, settings.themeMode)
        assertEquals(EntrySortOrder.OLDEST_FIRST, settings.entrySortOrder)
        assertTrue(repository.unreadOnlyFilterForFeed(FEED_A).first())
    }

    private companion object {
        const val FEED_A = 1L
        const val FEED_B = 2L
        const val FEED_C = 3L
    }
}
