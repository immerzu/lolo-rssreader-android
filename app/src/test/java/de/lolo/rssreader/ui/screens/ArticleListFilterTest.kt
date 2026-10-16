package de.lolo.rssreader.ui.screens

import de.lolo.rssreader.data.db.ArticleEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T6 – Filterlogik der Artikelliste: Bei aktiviertem Filter erscheinen ausschliesslich
 * ungelesene Artikel, bei deaktiviertem Filter alle. Deckt auch den im Auftrag genannten
 * Sonderfall ab, dass nach dem Lesen des letzten ungelesenen Artikels eine leere Liste
 * entsteht (Grundlage der bestehenden Leerzustandsanzeige „Keine ungelesenen Artikel").
 */
class ArticleListFilterTest {

    private fun article(id: Long, isRead: Boolean): ArticleEntity {
        return ArticleEntity(
            id = id,
            feedId = 1L,
            uniqueKey = "key-$id",
            title = "Titel $id",
            link = "https://example.com/$id",
            plainText = "Text $id",
            contentHtml = "<p>Text $id</p>",
            imageUrls = ""
        ).copy(isRead = isRead)
    }

    @Test
    fun filterDisabledShowsAllArticles() {
        val articles = listOf(article(1L, isRead = false), article(2L, isRead = true))

        val visible = filterArticlesByUnreadOnly(articles, showUnreadOnly = false)

        assertEquals(2, visible.size)
        assertEquals(listOf(1L, 2L), visible.map { it.id })
    }

    @Test
    fun filterEnabledShowsOnlyUnreadArticles() {
        val articles = listOf(
            article(1L, isRead = false),
            article(2L, isRead = true),
            article(3L, isRead = false)
        )

        val visible = filterArticlesByUnreadOnly(articles, showUnreadOnly = true)

        assertEquals(2, visible.size)
        assertEquals(listOf(1L, 3L), visible.map { it.id })
    }

    @Test
    fun filterEnabledWithAllArticlesReadYieldsEmptyList() {
        val articles = listOf(article(1L, isRead = true), article(2L, isRead = true))

        val visible = filterArticlesByUnreadOnly(articles, showUnreadOnly = true)

        assertTrue(visible.isEmpty())
    }

    @Test
    fun filterWithEmptyInputStaysEmpty() {
        assertTrue(filterArticlesByUnreadOnly(emptyList(), showUnreadOnly = true).isEmpty())
        assertTrue(filterArticlesByUnreadOnly(emptyList(), showUnreadOnly = false).isEmpty())
    }

    @Test
    fun filterEnabledAfterLastUnreadArticleWasReadShowsEmptyList() {
        val unread = listOf(article(1L, isRead = false))

        assertEquals(1, filterArticlesByUnreadOnly(unread, showUnreadOnly = true).size)

        val nowRead = listOf(article(1L, isRead = true))

        assertTrue(filterArticlesByUnreadOnly(nowRead, showUnreadOnly = true).isEmpty())
        assertEquals(1, filterArticlesByUnreadOnly(nowRead, showUnreadOnly = false).size)
    }

    // --- T10/T11: Zustandsauflösung des Buttons (Override vs. persistierter Feed-Wert) ---

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
