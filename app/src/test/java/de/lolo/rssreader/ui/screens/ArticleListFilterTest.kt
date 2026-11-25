package de.lolo.rssreader.ui.screens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T10/T11: Zustandsauflösung des Ungelesen-Filters (Override vs. persistierter Feed-Wert).
 *
 * Die eigentliche Filterung liegt seit Audit RSS-A16 in SQL und wird in
 * `ArticleListProjectionTest` gegen eine echte Room-Datenbank geprueft.
 */
class ArticleListFilterTest {

    @Test
    fun overrideWinsOverPersistedPreference() {
        // Mehrfaches Umschalten: der unmittelbare Override bestimmt den angezeigten Zustand.
        assertTrue(resolveShowUnreadOnly(override = true, preference = false))
        assertFalse(resolveShowUnreadOnly(override = false, preference = true))
    }

    @Test
    fun persistedFeedPreferenceIsUsedWithoutOverride() {
        // Nach Feed-Wechsel/Neustart (kein Override) gilt der persistierte Wert des Feeds.
        assertTrue(resolveShowUnreadOnly(override = null, preference = true))
        assertFalse(resolveShowUnreadOnly(override = null, preference = false))
    }

    @Test
    fun unknownPreferenceFallsBackToAllArticles() {
        // DataStore noch nicht geladen: keine Filterung und kein Speichern eines Defaults.
        assertFalse(resolveShowUnreadOnly(override = null, preference = null))
        // Ein Toggle hat auch dann Vorrang, wenn der Feed-Wert noch unbekannt ist.
        assertTrue(resolveShowUnreadOnly(override = true, preference = null))
    }
}
