package de.lolo.rssreader.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Regressionstests fuer die Schreibpfade aus dem Audit:
 *
 * - RSS-A02: Ein Abrufresultat darf nur Abrufspalten schreiben; URL, Name,
 *   WLAN-Regel und Sortierung bleiben erhalten.
 * - RSS-A03: Beim Quellenwechsel bleiben Favoriten mit ihrem Inhalt erhalten und
 *   bekommen einen eindeutigen Schluessel, damit die neue Quelle sie nicht ueberschreibt.
 * - RSS-A14: Lokale Metadaten (Name, WLAN-Regel) sind ohne Abruf aenderbar.
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class FeedWriteSafetyTest {

    private lateinit var database: AppDatabase
    private lateinit var feedDao: FeedDao
    private lateinit var articleDao: ArticleDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        feedDao = database.feedDao()
        articleDao = database.articleDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun abrufresultatLaesstNutzerdatenUnangetastet() = runTest {
        val feedId = feedDao.insert(
            FeedEntity(
                title = "Alter Titel",
                customTitle = "Mein Name",
                url = "https://example.com/feed.xml",
                displayOrder = 7,
                wifiOnly = false
            )
        )

        feedDao.updateFetchResult(
            feedId = feedId,
            fetchedAt = 1_000L,
            etag = "etag-1",
            lastModified = "mod-1",
            heavy = true,
            siteUrl = "https://example.com",
            iconUrl = "https://example.com/icon.png"
        )

        val stored = feedDao.getById(feedId)!!
        // Nutzerdaten unveraendert
        assertEquals("https://example.com/feed.xml", stored.url)
        assertEquals("Mein Name", stored.customTitle)
        assertEquals(7, stored.displayOrder)
        assertFalse(stored.wifiOnly)
        // Abrufspalten geschrieben
        assertEquals(1_000L, stored.lastFetchedAt)
        assertEquals("etag-1", stored.etag)
        assertEquals("mod-1", stored.lastModified)
        assertTrue(stored.heavy)
        assertEquals("https://example.com", stored.siteUrl)
        assertEquals("https://example.com/icon.png", stored.iconUrl)
        assertFalse(stored.lastRefreshFailed)
    }

    @Test
    fun quellenwechselBehaeltFavoritenMitEigenemSchluessel() = runTest {
        val feedId = feedDao.insert(
            FeedEntity(title = "Quelle A", url = "https://a.example/feed.xml")
        )
        val favoriteId = articleDao.insertAll(
            listOf(
                article(feedId = feedId, uniqueKey = "guid-x", isFavorite = true),
                article(feedId = feedId, uniqueKey = "guid-y", isFavorite = false)
            )
        ).first()

        articleDao.rekeyFavoritesForSourceChange(feedId)
        articleDao.deleteByFeedId(feedId)

        val remaining = articleDao.getByFeedAndUniqueKeys(
            feedId = feedId,
            uniqueKeys = listOf("legacy-$favoriteId")
        )
        assertEquals(1, remaining.size)
        assertEquals(0, articleDao.getByFeedAndUniqueKeys(feedId, listOf("guid-y")).size)

        // Die neue Quelle kann denselben GUID gefahrlos verwenden.
        articleDao.insertAll(listOf(article(feedId = feedId, uniqueKey = "guid-x", isFavorite = false)))
        val all = articleDao.getByFeedAndUniqueKeys(feedId, listOf("guid-x", "legacy-$favoriteId"))
        assertEquals(2, all.size)
    }

    @Test
    fun lokaleMetadatenAendernNurNameUndWlan() = runTest {
        val feedId = feedDao.insert(
            FeedEntity(
                title = "Titel",
                customTitle = null,
                url = "https://example.com/feed.xml",
                displayOrder = 3,
                etag = "etag-alt"
            )
        )

        feedDao.updateLocalMetadata(feedId = feedId, customTitle = "Neu", wifiOnly = true)

        val stored = feedDao.getById(feedId)!!
        assertEquals("Neu", stored.customTitle)
        assertTrue(stored.wifiOnly)
        assertEquals("https://example.com/feed.xml", stored.url)
        assertEquals(3, stored.displayOrder)
        assertEquals("etag-alt", stored.etag)
        assertNull(stored.lastFetchedAt)
        assertNotNull(stored.title)
    }

    private fun article(feedId: Long, uniqueKey: String, isFavorite: Boolean) = ArticleEntity(
        feedId = feedId,
        uniqueKey = uniqueKey,
        title = "Titel $uniqueKey",
        link = "https://example.com/$uniqueKey",
        plainText = "Text",
        contentHtml = "<p>Text</p>",
        imageUrls = "",
        isFavorite = isFavorite
    )
}
