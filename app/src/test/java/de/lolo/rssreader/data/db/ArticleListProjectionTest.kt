package de.lolo.rssreader.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Regressionstest fuer RSS-A16: Die Artikelliste nutzt eine schlanke Projektion ohne
 * HTML-Inhalt, sortiert und filtert in SQL und begrenzt die geladene Seite.
 */
@RunWith(RobolectricTestRunner::class)
class ArticleListProjectionTest {

    private lateinit var database: AppDatabase
    private lateinit var articleDao: ArticleDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        articleDao = database.articleDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun article(
        id: Long,
        publishedAt: Long?,
        isRead: Boolean = false,
        isFavorite: Boolean = false,
        feedId: Long = 1L
    ) = ArticleEntity(
        id = id,
        feedId = feedId,
        uniqueKey = "key-$id",
        title = "Titel $id",
        link = "https://example.com/$id",
        publishedAt = publishedAt,
        plainText = "Text $id",
        contentHtml = "<p>HTML-Inhalt $id</p>",
        imageUrls = "",
        isRead = isRead,
        isFavorite = isFavorite
    )

    private suspend fun seed() {
        database.feedDao().insert(
            FeedEntity(title = "Testfeed", url = "https://example.com/feed")
        )
        articleDao.insertAll(
            listOf(
                article(1L, publishedAt = 100L, isRead = true),
                article(2L, publishedAt = 300L),
                article(3L, publishedAt = 200L, isFavorite = true),
                article(4L, publishedAt = 400L, isRead = true, isFavorite = true),
                article(5L, publishedAt = null)
            )
        )
    }

    @Test
    fun newestFirstSortsByPublishedAtAndId() = runBlocking {
        seed()

        val items = articleDao.observeArticleListItems(
            feedId = 1L,
            unreadOnly = false,
            newestFirst = true,
            limit = 100
        ).first()

        assertEquals(listOf(4L, 2L, 3L, 1L, 5L), items.map { it.id })
    }

    @Test
    fun oldestFirstReversesTheOrder() = runBlocking {
        seed()

        val items = articleDao.observeArticleListItems(
            feedId = 1L,
            unreadOnly = false,
            newestFirst = false,
            limit = 100
        ).first()

        assertEquals(listOf(5L, 1L, 3L, 2L, 4L), items.map { it.id })
    }

    @Test
    fun unreadOnlyFiltersInSql() = runBlocking {
        seed()

        val items = articleDao.observeArticleListItems(
            feedId = 1L,
            unreadOnly = true,
            newestFirst = true,
            limit = 100
        ).first()

        assertEquals(listOf(2L, 3L, 5L), items.map { it.id })
        assertTrue("Nur ungelesene Artikel", items.none { it.isRead })
    }

    @Test
    fun limitCapsTheLoadedPage() = runBlocking {
        seed()

        val items = articleDao.observeArticleListItems(
            feedId = 1L,
            unreadOnly = false,
            newestFirst = true,
            limit = 2
        ).first()

        assertEquals(2, items.size)
        assertEquals(listOf(4L, 2L), items.map { it.id })
    }

    @Test
    fun projectionCarriesOnlyListFieldsAndKeepsContentInTheDatabase() = runBlocking {
        seed()

        val item = articleDao.observeArticleListItems(
            feedId = 1L,
            unreadOnly = false,
            newestFirst = true,
            limit = 1
        ).first().single()

        assertEquals("Titel 4", item.title)
        assertEquals("Text 4", item.plainText)
        assertEquals(true, item.isRead)
        assertEquals(true, item.isFavorite)
        // Der vollstaendige Artikel bleibt per ID abrufbar (Reader-Pfad unveraendert).
        assertEquals("<p>HTML-Inhalt 4</p>", articleDao.getById(item.id)?.contentHtml)
    }

    @Test
    fun favoriteListAndCountsStayConsistent() = runBlocking {
        seed()

        val favorites = articleDao.observeFavoriteListItems(
            unreadOnly = false,
            newestFirst = true,
            limit = 100
        ).first()

        assertEquals(listOf(4L, 3L), favorites.map { it.id })
        assertEquals(2, articleDao.observeFavoriteArticleCount().first())
        assertEquals(1, articleDao.observeFavoriteUnreadCount().first())
        assertEquals(3, articleDao.observeUnreadCountByFeed(1L).first())
        assertEquals(5, articleDao.observeArticleCountByFeed(1L).first())
    }

    // --- Randfaelle, die vor RSS-AUDIT-002 in der In-Memory-Filterung lagen ---

    @Test
    fun emptyFeedYieldsEmptyListForBothFilters() = runBlocking {
        database.feedDao().insert(FeedEntity(title = "Leer", url = "https://example.com/empty"))

        val all = articleDao.observeArticleListItems(1L, unreadOnly = false, newestFirst = true, limit = 100).first()
        val unread = articleDao.observeArticleListItems(1L, unreadOnly = true, newestFirst = true, limit = 100).first()

        assertTrue(all.isEmpty())
        assertTrue(unread.isEmpty())
    }

    @Test
    fun unreadFilterWithAllArticlesReadYieldsEmptyList() = runBlocking {
        database.feedDao().insert(FeedEntity(title = "Testfeed", url = "https://example.com/feed"))
        articleDao.insertAll(
            listOf(
                article(1L, publishedAt = 100L, isRead = true),
                article(2L, publishedAt = 200L, isRead = true)
            )
        )

        val unread = articleDao.observeArticleListItems(1L, unreadOnly = true, newestFirst = true, limit = 100).first()
        val all = articleDao.observeArticleListItems(1L, unreadOnly = false, newestFirst = true, limit = 100).first()

        assertTrue("Filter an + alles gelesen = leer", unread.isEmpty())
        assertEquals("Filter aus zeigt weiterhin beide", 2, all.size)
    }

    @Test
    fun markingTheLastUnreadArticleReadEmptiesTheFilteredList() = runBlocking {
        database.feedDao().insert(FeedEntity(title = "Testfeed", url = "https://example.com/feed"))
        articleDao.insertAll(listOf(article(1L, publishedAt = 100L, isRead = false)))

        assertEquals(
            1,
            articleDao.observeArticleListItems(1L, unreadOnly = true, newestFirst = true, limit = 100)
                .first().size
        )

        articleDao.markRead(1L)

        assertTrue(
            articleDao.observeArticleListItems(1L, unreadOnly = true, newestFirst = true, limit = 100)
                .first().isEmpty()
        )
        assertEquals(
            1,
            articleDao.observeArticleListItems(1L, unreadOnly = false, newestFirst = true, limit = 100)
                .first().size
        )
    }

    @Test
    fun pageBoundaryDeliversExactlyTheRequestedWindow() = runBlocking {
        database.feedDao().insert(FeedEntity(title = "Testfeed", url = "https://example.com/feed"))
        articleDao.insertAll(
            (1L..101L).map { id -> article(id, publishedAt = id * 10L) }
        )

        val firstPage = articleDao.observeArticleListItems(1L, false, newestFirst = true, limit = 100).first()
        val completeList = articleDao.observeArticleListItems(1L, false, newestFirst = true, limit = 200).first()

        assertEquals(100, firstPage.size)
        assertEquals(101, completeList.size)
        assertEquals("Die erste Seite beginnt beim neuesten Artikel", 101L, firstPage.first().id)
        assertEquals("Seite 1 enthaelt den aeltesten Artikel noch nicht", 2L, firstPage.last().id)
        assertEquals(1L, completeList.last().id)
    }

    @Test
    fun newerArticleDuringRefreshShiftsTheWindowWithoutDuplicates() = runBlocking {
        database.feedDao().insert(FeedEntity(title = "Testfeed", url = "https://example.com/feed"))
        articleDao.insertAll((1L..100L).map { id -> article(id, publishedAt = id * 10L) })

        assertEquals(
            100,
            articleDao.observeArticleListItems(1L, false, newestFirst = true, limit = 100).first().size
        )

        // Waehrend eines Refresh kommt ein neuerer Artikel hinzu.
        articleDao.insertAll(listOf(article(101L, publishedAt = 2_000L)))

        val page = articleDao.observeArticleListItems(1L, false, newestFirst = true, limit = 100).first()
        assertEquals(100, page.size)
        assertEquals(101L, page.first().id)
        assertEquals("Keine Duplikate in der Seite", 100, page.map { it.id }.distinct().size)
        assertEquals("Der aelteste Artikel faellt aus der geladenen Seite", 2L, page.last().id)

        val nextPage = articleDao.observeArticleListItems(1L, false, newestFirst = true, limit = 200).first()
        assertEquals("Nachladen bringt den verdraengten Artikel zurueck", 101, nextPage.size)
        assertEquals(1L, nextPage.last().id)
    }

    // --- Wisch-Navigation und Statusaenderungen jenseits der geladenen Seite (AUDIT-004) ---

    @Test
    fun swipeNavigationCoversArticlesBeyondTheLoadedPage() = runBlocking {
        database.feedDao().insert(FeedEntity(title = "Testfeed", url = "https://example.com/feed"))
        articleDao.insertAll((1L..150L).map { id -> article(id, publishedAt = id * 10L) })

        val page = articleDao.observeArticleListItems(1L, unreadOnly = false, newestFirst = true, limit = 100)
            .first()
        val navigation = articleDao.observeNavigationByFeed(1L).first()

        assertEquals("Die Seite bleibt auf 100 Artikel begrenzt", 100, page.size)
        assertEquals("Die Wisch-Navigation kennt alle 150 Artikel", 150, navigation.size)

        // Der Nachbar hinter dem letzten Artikel der Seite (150..51) ist 50 und liegt
        // damit ausserhalb der geladenen Seite - er bleibt per Wischen erreichbar.
        val lastOnPage = page.last()
        assertEquals(51L, lastOnPage.id)
        val navPosition = navigation.indexOfFirst { it.id == lastOnPage.id }
        assertEquals(99, navPosition)
        assertEquals(50L, navigation[navPosition + 1].id)
        assertEquals("Der aelteste Artikel bleibt in der Navigation", 1L, navigation.last().id)
    }

    @Test
    fun readAndFavoriteChangesAffectArticlesOutsideTheLoadedPage() = runBlocking {
        database.feedDao().insert(FeedEntity(title = "Testfeed", url = "https://example.com/feed"))
        articleDao.insertAll((1L..150L).map { id -> article(id, publishedAt = id * 10L) })

        // Artikel 5 liegt nicht in der ersten Seite (150..51).
        val pageIds = articleDao.observeArticleListItems(1L, false, newestFirst = true, limit = 100)
            .first().map { it.id }
        assertFalse("Artikel 5 darf nicht in der ersten Seite liegen", pageIds.contains(5L))

        articleDao.setFavorite(5L, true)
        articleDao.markRead(4L)

        val favorites = articleDao.observeFavoriteListItems(false, newestFirst = true, limit = 100).first()
        assertEquals(listOf(5L), favorites.map { it.id })
        assertTrue("Der nachgeladene Favorit traegt seinen Status", favorites.single().isFavorite)
        assertEquals(1, articleDao.observeFavoriteUnreadCount().first())
        assertEquals(149, articleDao.observeUnreadCountByFeed(1L).first())

        val readArticle = articleDao.getById(4L)
        assertEquals(true, readArticle?.isRead)
        assertEquals(false, readArticle?.isFavorite)

        // Mit "nur ungelesen" verschwindet der gelesene Artikel 4 auch jenseits der Seite.
        val unreadPage = articleDao.observeArticleListItems(1L, unreadOnly = true, newestFirst = true, limit = 100)
            .first()
        assertFalse(unreadPage.any { it.id == 4L })
    }
}
