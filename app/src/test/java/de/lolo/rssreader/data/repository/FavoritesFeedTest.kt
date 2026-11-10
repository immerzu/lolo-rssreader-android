package de.lolo.rssreader.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import de.lolo.rssreader.data.db.AppDatabase
import de.lolo.rssreader.data.db.ArticleDao
import de.lolo.rssreader.data.db.ArticleEntity
import de.lolo.rssreader.data.db.FeedDao
import de.lolo.rssreader.data.db.FeedEntity
import de.lolo.rssreader.data.network.FeedFetcher
import de.lolo.rssreader.data.network.FeedParser
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests fuer den virtuellen Feed "Favoriten" (FAVORITES_FEED_ID).
 * Der Eintrag ist keine Zeile in `feeds`; alle Aufrufe arbeiten auf `isFavorite = 1`.
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class FavoritesFeedTest {

    private lateinit var database: AppDatabase
    private lateinit var feedDao: FeedDao
    private lateinit var articleDao: ArticleDao
    private lateinit var repository: FeedRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        feedDao = database.feedDao()
        articleDao = database.articleDao()
        repository = FeedRepository(
            database = database,
            feedDao = feedDao,
            articleDao = articleDao,
            fetcher = FeedFetcher(OkHttpClient()),
            parser = FeedParser(),
            ioDispatcher = UnconfinedTestDispatcher()
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun favoriteSummaryIsHiddenWithoutFavorites() = runTest {
        val feedId = insertFeed("https://example.com/feed.xml")
        insertArticle(feedId, uniqueKey = "a1", title = "Ohne Stern", isFavorite = false)

        assertNull(repository.observeFavoriteSummary().first())
    }

    @Test
    fun favoriteSummaryAppearsWithCountsAndDisappearsAfterUnstarring() = runTest {
        val feedId = insertFeed("https://example.com/feed.xml")
        val favoriteId = insertArticle(
            feedId,
            uniqueKey = "fav-1",
            title = "Mit Stern",
            isFavorite = true,
            isRead = false
        )
        insertArticle(feedId, uniqueKey = "fav-2", title = "Gelesen", isFavorite = true, isRead = true)
        insertArticle(feedId, uniqueKey = "plain", title = "Ohne Stern", isFavorite = false)

        val summary = repository.observeFavoriteSummary().first()
        assertNotNull(summary)
        assertEquals(FAVORITES_FEED_ID, summary!!.id)
        assertEquals(2L, summary.totalArticles)
        assertEquals(1L, summary.unreadArticles)

        // Letzten beiden Favoriten entfernen -> Eintrag verschwindet.
        repository.setFavorite(favoriteId, false)
        val remainingFavorite = articleDao.getByFeedAndUniqueKeys(feedId, listOf("fav-2")).single()
        repository.setFavorite(remainingFavorite.id, false)

        assertNull(repository.observeFavoriteSummary().first())
    }

    @Test
    fun favoritesListContainsOnlyStarredArticles() = runTest {
        val feedId = insertFeed("https://example.com/feed.xml")
        insertArticle(feedId, uniqueKey = "plain", title = "Ohne Stern", isFavorite = false)
        insertArticle(
            feedId,
            uniqueKey = "fav-old",
            title = "Alt",
            isFavorite = true,
            publishedAt = 1_000L
        )
        insertArticle(
            feedId,
            uniqueKey = "fav-new",
            title = "Neu",
            isFavorite = true,
            publishedAt = 9_000L
        )

        val favorites = repository.observeArticles(FAVORITES_FEED_ID).first()

        assertEquals(listOf("Neu", "Alt"), favorites.map { it.title })
        assertEquals(listOf("Neu", "Alt"), repository.observeArticleNavigation(FAVORITES_FEED_ID)
            .first()
            .map { entry -> favorites.first { it.id == entry.id }.title })
    }

    @Test
    fun markAllReadOnFavoritesLeavesOtherArticlesUnread() = runTest {
        val feedId = insertFeed("https://example.com/feed.xml")
        insertArticle(feedId, uniqueKey = "fav", title = "Favorit", isFavorite = true, isRead = false)
        insertArticle(feedId, uniqueKey = "plain", title = "Normal", isFavorite = false, isRead = false)

        val affected = repository.markAllRead(FAVORITES_FEED_ID)

        assertEquals(1, affected)
        val favorites = repository.observeArticles(FAVORITES_FEED_ID).first()
        assertEquals(true, favorites.single().isRead)
        val feedArticles = repository.observeArticles(feedId).first()
        assertEquals(false, feedArticles.single { it.uniqueKey == "plain" }.isRead)
    }

    @Test
    fun refreshFeedOnFavoritesDoesNothing() = runTest {
        // Kein HTTP-Client mit Antworten: Ein Netzversuch wuerde fehlschlagen.
        assertEquals(0, repository.refreshFeed(FAVORITES_FEED_ID))
    }

    @Test
    fun markFeedOpenedOnFavoritesDoesNotThrow() = runTest {
        repository.markFeedOpened(FAVORITES_FEED_ID)
        assertEquals(0, feedDao.getAll().size)
    }

    private suspend fun insertFeed(url: String): Long {
        return feedDao.insert(FeedEntity(title = "Testfeed", url = url))
    }

    private suspend fun insertArticle(
        feedId: Long,
        uniqueKey: String,
        title: String,
        isFavorite: Boolean = false,
        isRead: Boolean = false,
        publishedAt: Long? = null
    ): Long {
        val ids = articleDao.insertAll(
            listOf(
                ArticleEntity(
                    feedId = feedId,
                    uniqueKey = uniqueKey,
                    title = title,
                    link = "https://example.com/$uniqueKey",
                    publishedAt = publishedAt,
                    plainText = title,
                    contentHtml = "<p>$title</p>",
                    imageUrls = "",
                    isRead = isRead,
                    isFavorite = isFavorite
                )
            )
        )
        return ids.single()
    }
}
