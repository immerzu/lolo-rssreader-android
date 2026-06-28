package de.lolo.rssreader.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import de.lolo.rssreader.data.db.AppDatabase
import de.lolo.rssreader.data.db.FeedDao
import de.lolo.rssreader.data.db.FeedEntity
import de.lolo.rssreader.data.network.FeedFetcher
import de.lolo.rssreader.data.network.FeedParser
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Regression test für GitHub-Issue #5:
 * moveFeedUp kann bei benachbarten Feeds mit identischem displayOrder
 * wirkungslos bleiben, während moveFeedDown gegen einen Feed mit
 * abweichendem displayOrder weiterhin funktioniert.
 *
 * Ursache: moveFeedUp und moveFeedDown tauschen lediglich die
 * displayOrder-Werte zweier benachbarter Feeds. Sind beide Werte
 * identisch, hat der Tausch keine Auswirkung auf die Sortierreihenfolge
 * (displayOrder ASC, id ASC).
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class FeedRepositoryMoveTest {

    private lateinit var database: AppDatabase
    private lateinit var feedDao: FeedDao
    private lateinit var repository: FeedRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        feedDao = database.feedDao()
        repository = FeedRepository(
            database = database,
            feedDao = feedDao,
            articleDao = database.articleDao(),
            fetcher = FeedFetcher(OkHttpClient()),
            parser = FeedParser(),
            ioDispatcher = UnconfinedTestDispatcher()
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    /**
     * Belegt die duplicate-displayOrder-Kollision:
     * Zwei benachbarte Feeds mit identischem displayOrder (5).
     * moveFeedUp auf den zweiten Feed (index 1 → tausche mit index 0)
     * bleibt wirkungslos, weil der Tausch 5↔5 keine Änderung ergibt.
     *
     * Erwartung: Feed B sollte nach dem moveFeedUp über Feed A stehen.
     * Tatsächlich: Reihenfolge bleibt A, B (Tausch war No-op wegen identischer Werte).
     * -> TEST SCHLÄGT FEHL -> belegt den Bug
     */
    @Test
    fun moveFeedUpIsIneffectiveWhenAdjacentFeedsHaveSameDisplayOrder() = runTest {
        val feedAId = feedDao.insert(
            FeedEntity(title = "Feed A", url = "https://a.example/feed", displayOrder = 5)
        )
        val feedBId = feedDao.insert(
            FeedEntity(title = "Feed B", url = "https://b.example/feed", displayOrder = 5)
        )
        val feedCId = feedDao.insert(
            FeedEntity(title = "Feed C", url = "https://c.example/feed", displayOrder = 10)
        )

        // Sortierung vor dem Move: A(5, id1), B(5, id2), C(10, id3)
        val before = feedDao.getAll()
        assertEquals(feedAId, before[0].id)
        assertEquals(feedBId, before[1].id)
        assertEquals(feedCId, before[2].id)

        // Act: B nach oben verschieben
        repository.moveFeedUp(feedBId)

        // Assert: B sollte jetzt über A stehen (Tausch hätte sichtbar sein müssen)
        val after = feedDao.getAll()
        assertEquals(
            "BUG: B sollte nach moveFeedUp auf Index 0 stehen, " +
                "bleibt aber auf Index 1, weil beide Feeds displayOrder=5 haben " +
                "und der Tausch No-op ist",
            feedBId,
            after[0].id
        )
    }

    /**
     * Zeigt, dass moveFeedDown gegen einen Feed mit abweichendem displayOrder
     * trotzdem funktioniert – die Asymmetrie aus dem Issue.
     *
     * Feed-Konstellation: A(5, id1), B(5, id2), C(10, id3)
     * moveFeedDown(B) tauscht displayOrder von B(5) mit C(10):
     *   B → 10, C → 5
     * Neue Sortierung: A(5, id1), C(5, id3), B(10, id2)
     * -> FUNKTIONIERT (Test besteht)
     */
    @Test
    fun moveFeedDownWorksWhenTargetHasDifferentDisplayOrder() = runTest {
        val feedAId = feedDao.insert(
            FeedEntity(title = "Feed A", url = "https://a2.example/feed", displayOrder = 5)
        )
        val feedBId = feedDao.insert(
            FeedEntity(title = "Feed B", url = "https://b2.example/feed", displayOrder = 5)
        )
        val feedCId = feedDao.insert(
            FeedEntity(title = "Feed C", url = "https://c2.example/feed", displayOrder = 10)
        )

        repository.moveFeedDown(feedBId)

        val after = feedDao.getAll()
        // B moved down past C because C has a different displayOrder
        assertEquals(feedAId, after[0].id)
        assertEquals(
            "C sollte nach runter-Verschieben von B auf Index 1 sein",
            feedCId, after[1].id
        )
        assertEquals(
            "B sollte nach runter-Verschieben auf Index 2 sein",
            feedBId, after[2].id
        )
    }

    /**
     * Referenztest: moveFeedUp funktioniert korrekt bei Feeds mit
     * unterschiedlichen displayOrder-Werten.
     */
    @Test
    fun moveFeedUpWorksWithDistinctDisplayOrders() = runTest {
        val feedAId = feedDao.insert(
            FeedEntity(title = "A", url = "https://a3.example/feed", displayOrder = 1)
        )
        val feedBId = feedDao.insert(
            FeedEntity(title = "B", url = "https://b3.example/feed", displayOrder = 2)
        )
        val feedCId = feedDao.insert(
            FeedEntity(title = "C", url = "https://c3.example/feed", displayOrder = 3)
        )

        repository.moveFeedUp(feedCId)

        val after = feedDao.getAll()
        assertEquals(feedAId, after[0].id)
        assertEquals("C sollte auf Index 1 sein", feedCId, after[1].id)
        assertEquals("B sollte auf Index 2 sein", feedBId, after[2].id)
    }
}
