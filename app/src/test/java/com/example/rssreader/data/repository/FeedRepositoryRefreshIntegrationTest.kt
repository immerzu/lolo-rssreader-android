package com.example.rssreader.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.rssreader.data.db.AppDatabase
import com.example.rssreader.data.db.ArticleDao
import com.example.rssreader.data.db.ArticleEntity
import com.example.rssreader.data.db.FeedDao
import com.example.rssreader.data.db.FeedEntity
import com.example.rssreader.data.errors.RssReaderException
import com.example.rssreader.data.network.FeedFetcher
import com.example.rssreader.data.network.FeedParser
import com.example.rssreader.data.opml.OpmlCodec
import com.example.rssreader.data.opml.OpmlFeedEntry
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class FeedRepositoryRefreshIntegrationTest {

    private lateinit var database: AppDatabase
    private lateinit var feedDao: FeedDao
    private lateinit var articleDao: ArticleDao
    private lateinit var repository: FeedRepository
    private lateinit var responseQueues: ConcurrentHashMap<String, ConcurrentLinkedQueue<QueuedHttpResponse>>
    private var nextDisplayOrder = 1

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        feedDao = database.feedDao()
        articleDao = database.articleDao()
        responseQueues = ConcurrentHashMap()

        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val url = chain.request().url.toString()
                val queuedResponse = responseQueues[url]?.poll()
                    ?: error("No queued response for $url")
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(queuedResponse.code)
                    .message(if (queuedResponse.code in 200..299) "OK" else "Error")
                    .body(
                        queuedResponse.body.toResponseBody(
                            queuedResponse.contentType?.toMediaType()
                        )
                    )
                    .build()
            }
            .build()

        repository = FeedRepository(
            database = database,
            feedDao = feedDao,
            articleDao = articleDao,
            fetcher = FeedFetcher(client),
            parser = FeedParser(),
            ioDispatcher = UnconfinedTestDispatcher()
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun refreshAllHandlesMultipleFeedsWithPartialFailureAndMaintainsFts() = runTest {
        val firstUrl = "https://example.com/feed-1.xml"
        val secondUrl = "https://example.com/feed-2.xml"
        val thirdUrl = "https://example.com/feed-3.xml"
        insertFeed(firstUrl)
        insertFeed(secondUrl)
        insertFeed(thirdUrl)

        enqueueSuccess(firstUrl, rssXml("f1-a", "Japan Reise", "Kyoto Tipps"))
        enqueueError(secondUrl, 503, times = 2)
        enqueueSuccess(thirdUrl, rssXml("f3-a", "Bali Reise", "Ubud Tipps"))

        val stats = repository.refreshAll()

        assertEquals(2, stats.refreshedFeeds)
        assertEquals(0, stats.skippedFeeds)
        assertEquals(1, stats.failedFeeds)
        assertEquals(2, stats.newArticles)
        assertEquals(2, articleDao.countArticles())
        assertEquals(2, articleDao.countSearchIndexRows())
        assertEquals(0, articleDao.countFtsMaintenanceTriggers())
        assertEquals(listOf("Japan Reise"), searchTitles("Kyoto", "kyoto*"))
        assertEquals(listOf("Bali Reise"), searchTitles("Ubud", "ubud*"))
    }

    @Test
    fun refreshAllLeavesDatabaseEmptyWhenFeedRefreshFails() = runTest {
        val failingUrl = "https://example.com/feed-failure.xml"
        insertFeed(failingUrl)

        enqueueError(failingUrl, 503, times = 2)

        val stats = repository.refreshAll()

        assertEquals(0, stats.refreshedFeeds)
        assertEquals(0, stats.skippedFeeds)
        assertEquals(1, stats.failedFeeds)
        assertEquals(0, stats.newArticles)
        assertEquals(0, articleDao.countArticles())
        assertEquals(0, articleDao.countSearchIndexRows())
        assertEquals(0, articleDao.countFtsMaintenanceTriggers())
        assertEquals(stats, repository.diagnosticsSnapshot().lastRefreshRunStats)
    }

    @Test
    fun refreshFeedUpdatesExistingArticleAndKeepsFtsInSync() = runTest {
        val feedUrl = "https://example.com/feed-update.xml"
        val feedId = insertFeed(feedUrl)

        enqueueSuccess(feedUrl, rssXml("article-1", "Alter Titel", "Bangkok Alt"))
        assertEquals(1, repository.refreshFeed(feedId))

        enqueueSuccess(feedUrl, rssXml("article-1", "Neuer Titel", "Bangkok Neu"))
        assertEquals(0, repository.refreshFeed(feedId))

        assertEquals(1, articleDao.countArticles())
        assertTrue(searchTitles("Alt", "alt*").isEmpty())
        assertEquals(listOf("Neuer Titel"), searchTitles("Neu", "neu*"))
        assertEquals(1, articleDao.countSearchIndexRows())
        assertEquals(0, articleDao.countFtsMaintenanceTriggers())
    }

    @Test
    fun refreshFeedRollsBackFeedMetadataWhenArticlePersistenceFails() = runTest {
        val feedUrl = "https://example.com/feed-rollback.xml"
        val feedId = feedDao.insert(
            FeedEntity(
                title = "Alter Feedtitel",
                url = feedUrl,
                siteUrl = "https://example.com/alt",
                iconUrl = "https://example.com/alt.ico",
                displayOrder = nextDisplayOrder++,
                lastFetchedAt = 1234L
            )
        )
        val failingRepository = FeedRepository(
            database = database,
            feedDao = feedDao,
            articleDao = FailingInsertArticleDao(articleDao),
            fetcher = FeedFetcher(buildQueuedClient()),
            parser = FeedParser(),
            ioDispatcher = UnconfinedTestDispatcher()
        )

        enqueueSuccess(feedUrl, rssXml("rollback-1", "Neuer Feedtitel", "Rollback Text"))

        val result = runCatching { failingRepository.refreshFeed(feedId) }
        val storedFeed = feedDao.getById(feedId)

        assertTrue(result.isFailure)
        assertNotNull(storedFeed)
        assertEquals("Alter Feedtitel", storedFeed?.title)
        assertEquals("https://example.com/alt", storedFeed?.siteUrl)
        assertEquals("https://example.com/alt.ico", storedFeed?.iconUrl)
        assertEquals(1234L, storedFeed?.lastFetchedAt)
        assertEquals(0, articleDao.countArticles())
        assertEquals(0, articleDao.countSearchIndexRows())
    }

    @Test
    fun refreshAllKeepsSearchStateStableWhenFeedContentIsUnchanged() = runTest {
        val feedUrl = "https://example.com/feed-unchanged.xml"
        insertFeed(feedUrl)

        enqueueSuccess(feedUrl, rssXml("article-same", "Konstanter Titel", "Osaka Tipps"))
        val firstStats = repository.refreshAll()

        enqueueSuccess(feedUrl, rssXml("article-same", "Konstanter Titel", "Osaka Tipps"))
        val secondStats = repository.refreshAll()

        assertEquals(1, firstStats.refreshedFeeds)
        assertEquals(1, firstStats.newArticles)
        assertEquals(1, secondStats.refreshedFeeds)
        assertEquals(0, secondStats.failedFeeds)
        assertEquals(0, secondStats.retryableFeeds)
        assertEquals(0, secondStats.newArticles)
        assertEquals(1, articleDao.countArticles())
        assertEquals(1, articleDao.countSearchIndexRows())
        assertEquals(listOf("Konstanter Titel"), searchTitles("Osaka", "osaka*"))
        assertEquals(secondStats, repository.diagnosticsSnapshot().lastRefreshRunStats)
    }

    @Test
    fun refreshFeedKeepsFlagsAndSearchStateWhenConflictingArticleIsUnchanged() = runTest {
        val feedUrl = "https://example.com/feed-flags-unchanged.xml"
        val feedId = insertFeed(feedUrl)

        enqueueSuccess(feedUrl, rssXml("article-same", "Konstanter Titel", "Osaka Tipps"))
        assertEquals(1, repository.refreshFeed(feedId))

        val initialArticle = articleDao.observeByFeed(feedId).first().single()
        repository.markRead(initialArticle.id)
        repository.setFavorite(initialArticle.id, true)

        enqueueSuccess(feedUrl, rssXml("article-same", "Konstanter Titel", "Osaka Tipps"))
        assertEquals(0, repository.refreshFeed(feedId))

        val storedArticle = articleDao.observeByFeed(feedId).first().single()
        assertEquals(initialArticle.id, storedArticle.id)
        assertTrue(storedArticle.isRead)
        assertTrue(storedArticle.isFavorite)
        assertEquals("Konstanter Titel", storedArticle.title)
        assertEquals("Osaka Tipps", storedArticle.plainText)
        assertEquals(1, articleDao.countArticles())
        assertEquals(1, articleDao.countSearchIndexRows())
        assertEquals(0, articleDao.countFtsMaintenanceTriggers())
        assertEquals(listOf("Konstanter Titel"), searchTitles("Osaka", "osaka*"))
        assertTrue(searchTitles("Neu", "neu*").isEmpty())
    }

    @Test
    fun refreshFeedUpdatesChangedConflictingArticleAndPreservesFlags() = runTest {
        val feedUrl = "https://example.com/feed-flags-changed.xml"
        val feedId = insertFeed(feedUrl)

        enqueueSuccess(feedUrl, rssXml("article-change", "Alter Titel", "Bangkok Alt"))
        assertEquals(1, repository.refreshFeed(feedId))

        val initialArticle = articleDao.observeByFeed(feedId).first().single()
        repository.markRead(initialArticle.id)
        repository.setFavorite(initialArticle.id, true)

        enqueueSuccess(feedUrl, rssXml("article-change", "Neuer Titel", "Bangkok Neu"))
        assertEquals(0, repository.refreshFeed(feedId))

        val storedArticle = articleDao.observeByFeed(feedId).first().single()
        assertEquals(initialArticle.id, storedArticle.id)
        assertTrue(storedArticle.isRead)
        assertTrue(storedArticle.isFavorite)
        assertEquals("Neuer Titel", storedArticle.title)
        assertEquals("Bangkok Neu", storedArticle.plainText)
        assertEquals(1, articleDao.countArticles())
        assertEquals(1, articleDao.countSearchIndexRows())
        assertEquals(0, articleDao.countFtsMaintenanceTriggers())
        assertTrue(searchTitles("Alt", "alt*").isEmpty())
        assertEquals(listOf("Neuer Titel"), searchTitles("Neu", "neu*"))
    }

    @Test
    fun refreshFeedDoesNotBatchUpdateUnchangedConflictingArticle() = runTest {
        val feedUrl = "https://example.com/feed-record-unchanged.xml"
        val recordingArticleDao = RecordingUpdateArticleDao(articleDao)
        repository = FeedRepository(
            database = database,
            feedDao = feedDao,
            articleDao = recordingArticleDao,
            fetcher = FeedFetcher(buildQueuedClient()),
            parser = FeedParser(),
            ioDispatcher = UnconfinedTestDispatcher()
        )
        val feedId = insertFeed(feedUrl)

        enqueueSuccess(feedUrl, rssXml("article-same", "Konstanter Titel", "Osaka Tipps"))
        assertEquals(1, repository.refreshFeed(feedId))

        val initialArticle = articleDao.observeByFeed(feedId).first().single()
        repository.markRead(initialArticle.id)
        repository.setFavorite(initialArticle.id, true)
        recordingArticleDao.reset()

        enqueueSuccess(feedUrl, rssXml("article-same", "Konstanter Titel", "Osaka Tipps"))
        assertEquals(0, repository.refreshFeed(feedId))

        val storedArticle = articleDao.observeByFeed(feedId).first().single()
        assertEquals(0, recordingArticleDao.updateAllCallCount)
        assertTrue(recordingArticleDao.updatedBatches.isEmpty())
        assertEquals(initialArticle.id, storedArticle.id)
        assertTrue(storedArticle.isRead)
        assertTrue(storedArticle.isFavorite)
        assertEquals(1, articleDao.countArticles())
        assertEquals(1, articleDao.countSearchIndexRows())
        assertEquals(0, articleDao.countFtsMaintenanceTriggers())
        assertEquals(listOf("Konstanter Titel"), searchTitles("Osaka", "osaka*"))
    }

    @Test
    fun refreshFeedBatchesOnlyChangedConflictsAndPreservesFlags() = runTest {
        val feedUrl = "https://example.com/feed-record-mixed.xml"
        val recordingArticleDao = RecordingUpdateArticleDao(articleDao)
        repository = FeedRepository(
            database = database,
            feedDao = feedDao,
            articleDao = recordingArticleDao,
            fetcher = FeedFetcher(buildQueuedClient()),
            parser = FeedParser(),
            ioDispatcher = UnconfinedTestDispatcher()
        )
        val feedId = insertFeed(feedUrl)

        enqueueSuccess(
            feedUrl,
            rssXmlWithTwoItems(
                firstUniqueKey = "article-stable",
                firstTitle = "Stabiler Titel",
                firstPlainText = "Kyoto Stabil",
                secondUniqueKey = "article-change",
                secondTitle = "Alter Titel",
                secondPlainText = "Bangkok Alt"
            )
        )
        assertEquals(2, repository.refreshFeed(feedId))

        val initialArticles = articleDao.observeByFeed(feedId).first().associateBy { it.uniqueKey }
        val stableArticle = checkNotNull(initialArticles["article-stable"])
        val changedArticle = checkNotNull(initialArticles["article-change"])
        repository.markRead(stableArticle.id)
        repository.setFavorite(stableArticle.id, true)
        repository.markRead(changedArticle.id)
        repository.setFavorite(changedArticle.id, true)
        recordingArticleDao.reset()

        enqueueSuccess(
            feedUrl,
            rssXmlWithTwoItems(
                firstUniqueKey = "article-stable",
                firstTitle = "Stabiler Titel",
                firstPlainText = "Kyoto Stabil",
                secondUniqueKey = "article-change",
                secondTitle = "Neuer Titel",
                secondPlainText = "Bangkok Neu"
            )
        )
        assertEquals(0, repository.refreshFeed(feedId))

        val storedArticles = articleDao.observeByFeed(feedId).first().associateBy { it.uniqueKey }
        val storedStableArticle = checkNotNull(storedArticles["article-stable"])
        val storedChangedArticle = checkNotNull(storedArticles["article-change"])

        assertEquals(1, recordingArticleDao.updateAllCallCount)
        assertEquals(listOf("article-change"), recordingArticleDao.updatedUniqueKeys())
        assertEquals(stableArticle.id, storedStableArticle.id)
        assertEquals(changedArticle.id, storedChangedArticle.id)
        assertTrue(storedStableArticle.isRead)
        assertTrue(storedStableArticle.isFavorite)
        assertTrue(storedChangedArticle.isRead)
        assertTrue(storedChangedArticle.isFavorite)
        assertEquals("Stabiler Titel", storedStableArticle.title)
        assertEquals("Kyoto Stabil", storedStableArticle.plainText)
        assertEquals("Neuer Titel", storedChangedArticle.title)
        assertEquals("Bangkok Neu", storedChangedArticle.plainText)
        assertEquals(2, articleDao.countArticles())
        assertEquals(2, articleDao.countSearchIndexRows())
        assertEquals(0, articleDao.countFtsMaintenanceTriggers())
        assertEquals(listOf("Stabiler Titel"), searchTitles("Kyoto", "kyoto*"))
        assertTrue(searchTitles("Alt", "alt*").isEmpty())
        assertEquals(listOf("Neuer Titel"), searchTitles("Neu", "neu*"))
    }

    @Test
    fun refreshAllHandlesChangedAndUnchangedFeedsInOneRun() = runTest {
        val changedUrl = "https://example.com/feed-changed.xml"
        val unchangedUrl = "https://example.com/feed-stable.xml"
        insertFeed(changedUrl)
        insertFeed(unchangedUrl)

        enqueueSuccess(changedUrl, rssXml("article-changed", "Alter Stand", "Hanoi Alt"))
        enqueueSuccess(unchangedUrl, rssXml("article-stable", "Stabiler Stand", "Kyoto Stabil"))
        val firstStats = repository.refreshAll()

        enqueueSuccess(changedUrl, rssXml("article-changed", "Neuer Stand", "Hanoi Neu"))
        enqueueSuccess(unchangedUrl, rssXml("article-stable", "Stabiler Stand", "Kyoto Stabil"))
        val secondStats = repository.refreshAll()

        assertEquals(2, firstStats.refreshedFeeds)
        assertEquals(2, firstStats.newArticles)
        assertEquals(2, secondStats.refreshedFeeds)
        assertEquals(0, secondStats.failedFeeds)
        assertEquals(0, secondStats.retryableFeeds)
        assertEquals(0, secondStats.newArticles)
        assertEquals(2, articleDao.countArticles())
        assertEquals(2, articleDao.countSearchIndexRows())
        assertTrue(searchTitles("Alt", "alt*").isEmpty())
        assertEquals(listOf("Neuer Stand"), searchTitles("Neu", "neu*"))
        assertEquals(listOf("Stabiler Stand"), searchTitles("Kyoto", "kyoto*"))
        assertEquals(secondStats, repository.diagnosticsSnapshot().lastRefreshRunStats)
    }

    @Test
    fun refreshAllHandlesChangedUnchangedAndFailedFeedsTogether() = runTest {
        val changedUrl = "https://example.com/feed-mixed-changed.xml"
        val unchangedUrl = "https://example.com/feed-mixed-unchanged.xml"
        val failingUrl = "https://example.com/feed-mixed-failing.xml"
        insertFeed(changedUrl)
        insertFeed(unchangedUrl)
        insertFeed(failingUrl)

        enqueueSuccess(changedUrl, rssXml("mixed-changed", "Vorher", "Prag Alt"))
        enqueueSuccess(unchangedUrl, rssXml("mixed-stable", "Bleibt gleich", "Bern Stabil"))
        enqueueSuccess(failingUrl, rssXml("mixed-fail", "Erster Stand", "Oslo Start"))
        val firstStats = repository.refreshAll()

        enqueueSuccess(changedUrl, rssXml("mixed-changed", "Nachher", "Prag Neu"))
        enqueueSuccess(unchangedUrl, rssXml("mixed-stable", "Bleibt gleich", "Bern Stabil"))
        enqueueError(failingUrl, 503, times = 2)
        val secondStats = repository.refreshAll()

        assertEquals(3, firstStats.refreshedFeeds)
        assertEquals(3, firstStats.newArticles)
        assertEquals(2, secondStats.refreshedFeeds)
        assertEquals(1, secondStats.failedFeeds)
        assertEquals(0, secondStats.newArticles)
        assertEquals(3, articleDao.countArticles())
        assertEquals(3, articleDao.countSearchIndexRows())
        assertTrue(searchTitles("Alt", "alt*").isEmpty())
        assertEquals(listOf("Nachher"), searchTitles("Neu", "neu*"))
        assertEquals(listOf("Bleibt gleich"), searchTitles("Bern", "bern*"))
        assertEquals(listOf("Erster Stand"), searchTitles("Oslo", "oslo*"))
        assertEquals(secondStats, repository.diagnosticsSnapshot().lastRefreshRunStats)
    }

    @Test
    fun refreshFeedKeepsLatestDuplicateUniqueKeyFromSamePayloadInSearchIndex() = runTest {
        val feedUrl = "https://example.com/feed-duplicate-key.xml"
        val feedId = insertFeed(feedUrl)

        enqueueSuccess(
            feedUrl,
            rssXmlWithTwoItems(
                firstUniqueKey = "duplicate-key",
                firstTitle = "Alter Eintrag",
                firstPlainText = "Hanoi Alt",
                secondUniqueKey = "duplicate-key",
                secondTitle = "Neuer Eintrag",
                secondPlainText = "Hanoi Neu"
            )
        )

        assertEquals(1, repository.refreshFeed(feedId))

        val storedArticle = articleDao.observeByFeed(feedId).first().single()
        assertEquals("duplicate-key", storedArticle.uniqueKey)
        assertEquals("Neuer Eintrag", storedArticle.title)
        assertEquals("Hanoi Neu", storedArticle.plainText)
        assertEquals(1, articleDao.countArticles())
        assertEquals(1, articleDao.countSearchIndexRows())
        assertEquals(0, articleDao.countFtsMaintenanceTriggers())
        assertTrue(searchTitles("Alt", "alt*").isEmpty())
        assertEquals(listOf("Neuer Eintrag"), searchTitles("Neu", "neu*"))
    }

    @Test
    fun refreshFeedCollapsesDuplicateChangedConflictsToSingleBatchUpdate() = runTest {
        val feedUrl = "https://example.com/feed-duplicate-conflict.xml"
        val recordingArticleDao = RecordingUpdateArticleDao(articleDao)
        repository = FeedRepository(
            database = database,
            feedDao = feedDao,
            articleDao = recordingArticleDao,
            fetcher = FeedFetcher(buildQueuedClient()),
            parser = FeedParser(),
            ioDispatcher = UnconfinedTestDispatcher()
        )
        val feedId = insertFeed(feedUrl)

        enqueueSuccess(feedUrl, rssXml("duplicate-key", "Alter Eintrag", "Hanoi Alt"))
        assertEquals(1, repository.refreshFeed(feedId))

        val initialArticle = articleDao.observeByFeed(feedId).first().single()
        repository.markRead(initialArticle.id)
        repository.setFavorite(initialArticle.id, true)
        recordingArticleDao.reset()

        enqueueSuccess(
            feedUrl,
            rssXmlWithTwoItems(
                firstUniqueKey = "duplicate-key",
                firstTitle = "Zwischenstand",
                firstPlainText = "Hanoi Mitte",
                secondUniqueKey = "duplicate-key",
                secondTitle = "Endstand",
                secondPlainText = "Hanoi Neu"
            )
        )

        assertEquals(0, repository.refreshFeed(feedId))

        val storedArticle = articleDao.observeByFeed(feedId).first().single()
        assertEquals(1, recordingArticleDao.updateAllCallCount)
        assertEquals(listOf("duplicate-key"), recordingArticleDao.updatedUniqueKeys())
        assertEquals(initialArticle.id, storedArticle.id)
        assertTrue(storedArticle.isRead)
        assertTrue(storedArticle.isFavorite)
        assertEquals("Endstand", storedArticle.title)
        assertEquals("Hanoi Neu", storedArticle.plainText)
        assertEquals(1, articleDao.countArticles())
        assertEquals(1, articleDao.countSearchIndexRows())
        assertEquals(0, articleDao.countFtsMaintenanceTriggers())
        assertTrue(searchTitles("Alt", "alt*").isEmpty())
        assertTrue(searchTitles("Mitte", "mitte*").isEmpty())
        assertEquals(listOf("Endstand"), searchTitles("Neu", "neu*"))
    }

    @Test
    fun importOpmlDeduplicatesDuplicateUrlsBeforeRepositoryImportLoop() = runTest {
        val feedUrl = "https://example.com/import-duplicate.xml"

        enqueueSuccess(feedUrl, rssXml("import-1", "Importierter Feed", "Singapur Tipps"))

        val result = repository.importOpml(
            OpmlCodec.build(
                listOf(
                    OpmlFeedEntry(url = feedUrl, title = "Importierter Feed"),
                    OpmlFeedEntry(url = feedUrl, title = "Importierter Feed Duplikat")
                )
            ).byteInputStream()
        )

        assertEquals(1, result.importedFeeds)
        assertEquals(0, result.skippedFeeds)
        assertEquals(0, result.failedFeeds)
        assertEquals(1, feedDao.countFeeds())
        assertEquals(1, articleDao.countArticles())
        assertEquals(1, articleDao.countSearchIndexRows())
        assertEquals(listOf("Importierter Feed"), searchTitles("Singapur", "singapur*"))
    }

    @Test
    fun importOpmlPreservesEntryOrderInDisplayOrder() = runTest {
        val firstUrl = "https://example.com/import-order-1.xml"
        val secondUrl = "https://example.com/import-order-2.xml"
        val thirdUrl = "https://example.com/import-order-3.xml"

        enqueueSuccess(firstUrl, rssXml("import-order-1", "Erster Feed", "Bern Tipps"))
        enqueueSuccess(secondUrl, rssXml("import-order-2", "Zweiter Feed", "Lyon Tipps"))
        enqueueSuccess(thirdUrl, rssXml("import-order-3", "Dritter Feed", "Turin Tipps"))

        val result = repository.importOpml(
            OpmlCodec.build(
                listOf(
                    OpmlFeedEntry(url = secondUrl, title = "Zweiter Feed"),
                    OpmlFeedEntry(url = thirdUrl, title = "Dritter Feed"),
                    OpmlFeedEntry(url = firstUrl, title = "Erster Feed")
                )
            ).byteInputStream()
        )

        assertEquals(3, result.importedFeeds)
        assertEquals(0, result.skippedFeeds)
        assertEquals(0, result.failedFeeds)
        assertEquals(
            listOf(secondUrl, thirdUrl, firstUrl),
            feedDao.getAll().map { it.url }
        )
    }

    @Test
    fun importOpmlCountsExistingDuplicateUrlOnlyOnceAfterOpmlDeduplication() = runTest {
        val feedUrl = "https://example.com/import-existing.xml"
        insertFeed(feedUrl)

        val result = repository.importOpml(
            OpmlCodec.build(
                listOf(
                    OpmlFeedEntry(url = feedUrl, title = "Schon vorhanden"),
                    OpmlFeedEntry(url = feedUrl, title = "Schon vorhanden Duplikat")
                )
            ).byteInputStream()
        )

        assertEquals(0, result.importedFeeds)
        assertEquals(1, result.skippedFeeds)
        assertEquals(0, result.failedFeeds)
        assertEquals(1, feedDao.countFeeds())
        assertEquals(0, articleDao.countArticles())
        assertEquals(0, articleDao.countSearchIndexRows())
    }

    @Test
    fun importOpmlKeepsSuccessfulFeedsWhenAnotherFeedFails() = runTest {
        val successfulUrl = "https://example.com/import-success.xml"
        val failingUrl = "https://example.com/import-failing.xml"

        enqueueSuccess(successfulUrl, rssXml("import-success", "Erfolgreicher Import", "Wien Tipps"))
        enqueueError(failingUrl, 503, times = 2)

        val result = repository.importOpml(
            OpmlCodec.build(
                listOf(
                    OpmlFeedEntry(url = successfulUrl, title = "Erfolgreicher Import"),
                    OpmlFeedEntry(url = failingUrl, title = "Fehlgeschlagener Import")
                )
            ).byteInputStream()
        )

        assertEquals(1, result.importedFeeds)
        assertEquals(0, result.skippedFeeds)
        assertEquals(1, result.failedFeeds)
        assertEquals(failingUrl, result.firstFailedFeedUrl)
        assertEquals(1, feedDao.countFeeds())
        assertEquals(1, articleDao.countArticles())
        assertEquals(1, articleDao.countSearchIndexRows())
        assertEquals(listOf("Erfolgreicher Import"), searchTitles("Wien", "wien*"))
        assertEquals(result, repository.diagnosticsSnapshot().lastImportResult)
    }

    @Test
    fun importOpmlPropagatesCancellationInsteadOfCountingItAsFeedFailure() = runTest {
        val successfulUrl = "https://example.com/import-cancel-success.xml"
        val cancellingUrl = "https://example.com/import-cancel.xml"
        repository = FeedRepository(
            database = database,
            feedDao = feedDao,
            articleDao = CancellingInsertArticleDao(articleDao, cancelledUniqueKey = "cancelled-import"),
            fetcher = FeedFetcher(buildQueuedClient()),
            parser = FeedParser(),
            ioDispatcher = UnconfinedTestDispatcher()
        )

        enqueueSuccess(successfulUrl, rssXml("successful-import", "Erfolgreicher Import", "Wien Tipps"))
        enqueueSuccess(cancellingUrl, rssXml("cancelled-import", "Abgebrochener Import", "Prag Tipps"))

        val failure = runCatching {
            repository.importOpml(
                OpmlCodec.build(
                    listOf(
                        OpmlFeedEntry(url = successfulUrl, title = "Erfolgreicher Import"),
                        OpmlFeedEntry(url = cancellingUrl, title = "Abgebrochener Import")
                    )
                ).byteInputStream()
            )
        }.exceptionOrNull()

        assertTrue(failure is CancellationException)
        assertTrue(feedDao.countFeeds() in 0..1)
        assertTrue(articleDao.countArticles() in 0..1)
        assertTrue(articleDao.countSearchIndexRows() in 0..1)
        assertNull(repository.diagnosticsSnapshot().lastImportResult)
    }

    @Test
    fun importSingleFeedXmlImportsFeedArticlesAndSearchIndex() = runTest {
        val feedUrl = "https://example.com/single-import.xml"

        val result = repository.importOpml(
            importableRssXml(
                feedUrl = feedUrl,
                uniqueKey = "single-import",
                title = "Einzelimport",
                plainText = "Tallinn Tipps"
            )
                .byteInputStream()
        )

        assertEquals(1, result.importedFeeds)
        assertEquals(0, result.skippedFeeds)
        assertEquals(0, result.failedFeeds)
        assertEquals(1, feedDao.countFeeds())
        assertEquals(1, articleDao.countArticles())
        assertEquals(1, articleDao.countSearchIndexRows())
        assertEquals(0, articleDao.countFtsMaintenanceTriggers())
        assertEquals(listOf("Einzelimport"), searchTitles("Tallinn", "tallinn*"))
        assertEquals(result, repository.diagnosticsSnapshot().lastImportResult)
        assertEquals(feedUrl, feedDao.getAll().single().url)
    }

    @Test
    fun importSingleFeedXmlPreservesUtf8UmlautsAndSearchIndex() = runTest {
        val feedUrl = "https://example.com/single-import-utf8.xml"
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0" xmlns:atom="http://www.w3.org/2005/Atom">
              <channel>
                <title>Beispiel Feed</title>
                <link>https://example.com/</link>
                <atom:link href="$feedUrl" rel="self" type="application/rss+xml" />
                <item>
                  <guid>single-import-utf8</guid>
                  <title>Beitragsüberschrift mit Straße und Grüße</title>
                  <link>https://example.com/single-import-utf8</link>
                  <description><![CDATA[<p>Schöne Grüße aus München</p>]]></description>
                </item>
              </channel>
            </rss>
        """.trimIndent()

        val result = repository.importOpml(
            xml.byteInputStream()
        )

        assertEquals(1, result.importedFeeds)
        assertEquals(0, result.skippedFeeds)
        assertEquals(0, result.failedFeeds)
        assertEquals(1, feedDao.countFeeds())
        assertEquals(1, articleDao.countArticles())
        assertEquals(1, articleDao.countSearchIndexRows())
        assertEquals(0, articleDao.countFtsMaintenanceTriggers())
        assertEquals(listOf("Beitragsüberschrift mit Straße und Grüße"), searchTitles("München", "münchen*"))
        assertEquals(result, repository.diagnosticsSnapshot().lastImportResult)
        assertEquals(feedUrl, feedDao.getAll().single().url)
    }

    @Test
    fun importSingleFeedXmlSkipsExistingFeedByDetectedUrl() = runTest {
        val feedUrl = "https://example.com/single-skip.xml"
        insertFeed(feedUrl)

        val result = repository.importOpml(
            importableRssXml(
                feedUrl = feedUrl,
                uniqueKey = "single-skip",
                title = "Schon da",
                plainText = "Vilnius Tipps"
            )
                .byteInputStream()
        )

        assertEquals(0, result.importedFeeds)
        assertEquals(1, result.skippedFeeds)
        assertEquals(0, result.failedFeeds)
        assertEquals(1, feedDao.countFeeds())
        assertEquals(0, articleDao.countArticles())
        assertEquals(0, articleDao.countSearchIndexRows())
        assertEquals(result, repository.diagnosticsSnapshot().lastImportResult)
    }

    @Test
    fun importSingleFeedXmlWithoutDetectableFeedUrlThrowsUnsupportedImportFile() = runTest {
        val failure = runCatching {
            repository.importOpml(
            rssXmlWithoutFeedHint(
                uniqueKey = "single-no-hint",
                title = "Ohne Feed-Hinweis",
                plainText = "Riga Tipps"
            ).byteInputStream()
            )
        }.exceptionOrNull()

        assertTrue(failure is RssReaderException.UnsupportedImportFile)
        assertEquals(0, feedDao.countFeeds())
        assertEquals(0, articleDao.countArticles())
        assertEquals(0, articleDao.countSearchIndexRows())
        assertNull(repository.diagnosticsSnapshot().lastImportResult)
    }

    @Test
    fun importUnsupportedFileThrowsUnsupportedImportFileAndKeepsDiagnosticsUnset() = runTest {
        val failure = runCatching {
            repository.importOpml("not-an-import-file".byteInputStream())
        }.exceptionOrNull()

        assertTrue(failure is RssReaderException.UnsupportedImportFile)
        assertEquals(0, feedDao.countFeeds())
        assertEquals(0, articleDao.countArticles())
        assertEquals(0, articleDao.countSearchIndexRows())
        assertNull(repository.diagnosticsSnapshot().lastImportResult)
    }

    @Test
    fun deleteAllFeedsRemovesFeedsArticlesAndStaleSearchRows() = runTest {
        val firstUrl = "https://example.com/delete-feed-1.xml"
        val secondUrl = "https://example.com/delete-feed-2.xml"
        insertFeed(firstUrl)
        insertFeed(secondUrl)

        enqueueSuccess(firstUrl, rssXml("delete-1", "Erster Feed", "Rom Tipps"))
        enqueueSuccess(secondUrl, rssXml("delete-2", "Zweiter Feed", "Paris Tipps"))

        assertEquals(2, repository.refreshAll().newArticles)
        assertEquals(2, feedDao.countFeeds())
        assertEquals(2, articleDao.countArticles())
        assertEquals(2, articleDao.countSearchIndexRows())

        val deletedFeeds = repository.deleteAllFeeds()

        assertEquals(2, deletedFeeds)
        assertEquals(0, feedDao.countFeeds())
        assertEquals(0, articleDao.countArticles())
        assertEquals(0, articleDao.countSearchIndexRows())
        assertTrue(searchTitles("Rom", "rom*").isEmpty())
        assertTrue(searchTitles("Paris", "paris*").isEmpty())
        assertEquals(0, articleDao.countFtsMaintenanceTriggers())
    }

    @Test
    fun updateFeedWithChangedUrlRemovesStaleFtsEntries() = runTest {
        val oldUrl = "https://example.com/feed-old.xml"
        val newUrl = "https://example.com/feed-new.xml"
        val feedId = insertFeed(oldUrl)

        enqueueSuccess(oldUrl, rssXml("article-old", "Alte Reise", "Saigon Alt"))
        repository.refreshFeed(feedId)

        enqueueSuccess(newUrl, rssXml("article-new", "Neue Reise", "Seoul Neu"))
        repository.updateFeed(feedId, newUrl, customTitle = null, wifiOnly = false)

        assertTrue(searchTitles("Saigon", "saigon*").isEmpty())
        assertEquals(listOf("Neue Reise"), searchTitles("Seoul", "seoul*"))
        assertEquals(1, articleDao.countArticles())
        assertEquals(1, articleDao.countSearchIndexRows())
        assertEquals(0, articleDao.countFtsMaintenanceTriggers())
    }

    @Test
    fun backgroundRefreshSkipsWifiOnlyFeedsOnMeteredNetwork() = runTest {
        val wifiOnlyUrl = "https://example.com/feed-wifi.xml"
        val normalUrl = "https://example.com/feed-normal.xml"
        insertFeed(wifiOnlyUrl, wifiOnly = true)
        insertFeed(normalUrl, wifiOnly = false)

        enqueueSuccess(normalUrl, rssXml("normal-1", "Normale Reise", "Taipei Tipps"))

        val stats = repository.refreshAllInBackground(
            isUnmeteredNetwork = false,
            hasWifiConnection = false
        )

        assertEquals(1, stats.refreshedFeeds)
        assertEquals(1, stats.skippedFeeds)
        assertEquals(0, stats.failedFeeds)
        assertEquals(1, stats.newArticles)
        assertEquals(listOf("Normale Reise"), searchTitles("Taipei", "taipei*"))
    }

    @Test
    fun refreshAllSkipsWifiOnlyFeedsWhenWifiConnectionIsMissing() = runTest {
        val wifiOnlyUrl = "https://example.com/feed-wifi-manual.xml"
        val normalUrl = "https://example.com/feed-manual.xml"
        insertFeed(wifiOnlyUrl, wifiOnly = true)
        insertFeed(normalUrl, wifiOnly = false)

        enqueueSuccess(normalUrl, rssXml("manual-1", "Manuelle Reise", "Lissabon Tipps"))

        val stats = repository.refreshAll(hasWifiConnection = false)

        assertEquals(1, stats.refreshedFeeds)
        assertEquals(1, stats.skippedFeeds)
        assertEquals(0, stats.failedFeeds)
        assertEquals(1, stats.newArticles)
        assertEquals(listOf("Manuelle Reise"), searchTitles("Lissabon", "lissabon*"))
    }

    @Test
    fun backgroundRefreshCombinesSkipSuccessAndFailureWithoutCorruptingState() = runTest {
        val wifiOnlyUrl = "https://example.com/feed-wifi-mix.xml"
        val successUrl = "https://example.com/feed-success-mix.xml"
        val failingUrl = "https://example.com/feed-failure-mix.xml"
        insertFeed(wifiOnlyUrl, wifiOnly = true)
        insertFeed(successUrl, wifiOnly = false)
        insertFeed(failingUrl, wifiOnly = false)

        enqueueSuccess(successUrl, rssXml("success-1", "Erfolgreiche Reise", "Riga Tipps"))
        enqueueError(failingUrl, 503, times = 2)

        val stats = repository.refreshAllInBackground(
            isUnmeteredNetwork = false,
            hasWifiConnection = false
        )

        assertEquals(1, stats.refreshedFeeds)
        assertEquals(1, stats.skippedFeeds)
        assertEquals(1, stats.failedFeeds)
        assertEquals(1, stats.newArticles)
        assertEquals(1, articleDao.countArticles())
        assertEquals(1, articleDao.countSearchIndexRows())
        assertEquals(0, articleDao.countFtsMaintenanceTriggers())
        assertEquals(listOf("Erfolgreiche Reise"), searchTitles("Riga", "riga*"))
        assertEquals(stats, repository.diagnosticsSnapshot().lastRefreshRunStats)
    }

    @Test
    fun refreshAllAllowsWifiOnlyFeedsWhenWifiConnectionExists() = runTest {
        val wifiOnlyUrl = "https://example.com/feed-wifi-manual-allowed.xml"
        insertFeed(wifiOnlyUrl, wifiOnly = true)

        enqueueSuccess(wifiOnlyUrl, rssXml("manual-wifi", "WLAN Hand", "Oslo Tipps"))

        val stats = repository.refreshAll(hasWifiConnection = true)

        assertEquals(1, stats.refreshedFeeds)
        assertEquals(0, stats.skippedFeeds)
        assertEquals(0, stats.failedFeeds)
        assertEquals(1, stats.newArticles)
        assertEquals(listOf("WLAN Hand"), searchTitles("Oslo", "oslo*"))
    }

    @Test
    fun backgroundRefreshAllowsFeedWifiOnlyWhenWifiConnectionExists() = runTest {
        val wifiOnlyUrl = "https://example.com/feed-wifi-allowed.xml"
        insertFeed(wifiOnlyUrl, wifiOnly = true)

        enqueueSuccess(wifiOnlyUrl, rssXml("wifi-allowed", "WLAN Reise", "Seoul Tipps"))

        val stats = repository.refreshAllInBackground(
            isUnmeteredNetwork = false,
            hasWifiConnection = true
        )

        assertEquals(1, stats.refreshedFeeds)
        assertEquals(0, stats.skippedFeeds)
        assertEquals(0, stats.failedFeeds)
        assertEquals(1, stats.newArticles)
        assertEquals(listOf("WLAN Reise"), searchTitles("Seoul", "seoul*"))
    }

    @Test
    fun refreshAllPreservesLatin1CharactersThroughRepositoryAndFts() = runTest {
        val url = "https://example.com/feed-latin1.xml"
        val feedId = insertFeed(url)

        val xml = """
            <?xml version="1.0" encoding="ISO-8859-1"?>
            <rss version="2.0">
              <channel>
                <title>Test Feed</title>
                <link>https://example.com/</link>
                <item>
                  <guid>latin1-1</guid>
                  <title>Straße &amp; Grüße</title>
                  <link>https://example.com/latin1-1</link>
                  <description><![CDATA[<p>Schöne Grüße aus Köln</p>]]></description>
                </item>
              </channel>
            </rss>
        """.trimIndent()

        enqueue(
            url = url,
            response = QueuedHttpResponse(
                code = 200,
                body = String(xml.toByteArray(Charsets.ISO_8859_1), Charsets.ISO_8859_1),
                contentType = "application/rss+xml; charset=ISO-8859-1"
            )
        )

        val stats = repository.refreshAll()
        val storedArticle = articleDao.getByFeedAndUniqueKeys(feedId, listOf("latin1-1")).single()

        assertEquals(1, stats.refreshedFeeds)
        assertEquals(1, stats.newArticles)
        assertEquals("Straße & Grüße", storedArticle.title)
        assertEquals("Schöne Grüße aus Köln", storedArticle.plainText)
        assertEquals(listOf("Straße & Grüße"), searchTitles("Köln", "köln*"))
        assertEquals(1, articleDao.countSearchIndexRows())
        assertEquals(0, articleDao.countFtsMaintenanceTriggers())
    }

    @Test
    fun refreshAllUpdatesLatin1CharactersThroughRepositoryAndFts() = runTest {
        val url = "https://example.com/feed-latin1-update.xml"
        val feedId = insertFeed(url)

        val firstXml = """
            <?xml version="1.0" encoding="ISO-8859-1"?>
            <rss version="2.0">
              <channel>
                <title>Test Feed</title>
                <link>https://example.com/</link>
                <item>
                  <guid>latin1-update</guid>
                  <title>Straße Alt</title>
                  <link>https://example.com/latin1-update</link>
                  <description><![CDATA[<p>Grüße aus Köln</p>]]></description>
                </item>
              </channel>
            </rss>
        """.trimIndent()
        val secondXml = """
            <?xml version="1.0" encoding="ISO-8859-1"?>
            <rss version="2.0">
              <channel>
                <title>Test Feed</title>
                <link>https://example.com/</link>
                <item>
                  <guid>latin1-update</guid>
                  <title>Straße Neu</title>
                  <link>https://example.com/latin1-update</link>
                  <description><![CDATA[<p>Schöne Grüße aus München</p>]]></description>
                </item>
              </channel>
            </rss>
        """.trimIndent()

        enqueue(
            url = url,
            response = QueuedHttpResponse(
                code = 200,
                body = String(firstXml.toByteArray(Charsets.ISO_8859_1), Charsets.ISO_8859_1),
                contentType = "application/rss+xml; charset=ISO-8859-1"
            )
        )
        assertEquals(1, repository.refreshAll().newArticles)

        enqueue(
            url = url,
            response = QueuedHttpResponse(
                code = 200,
                body = String(secondXml.toByteArray(Charsets.ISO_8859_1), Charsets.ISO_8859_1),
                contentType = "application/rss+xml; charset=ISO-8859-1"
            )
        )

        val secondStats = repository.refreshAll()
        val storedArticle = articleDao.getByFeedAndUniqueKeys(feedId, listOf("latin1-update")).single()

        assertEquals(1, secondStats.refreshedFeeds)
        assertEquals(0, secondStats.newArticles)
        assertEquals("Straße Neu", storedArticle.title)
        assertEquals("Schöne Grüße aus München", storedArticle.plainText)
        assertTrue(searchTitles("Köln", "köln*").isEmpty())
        assertEquals(listOf("Straße Neu"), searchTitles("München", "münchen*"))
        assertEquals(1, articleDao.countSearchIndexRows())
        assertEquals(0, articleDao.countFtsMaintenanceTriggers())
    }

    @Test
    fun refreshAllPreservesFlagsWhenLatin1ArticleUpdates() = runTest {
        val url = "https://example.com/feed-latin1-flags.xml"
        val feedId = insertFeed(url)

        val firstXml = """
            <?xml version="1.0" encoding="ISO-8859-1"?>
            <rss version="2.0">
              <channel>
                <title>Test Feed</title>
                <link>https://example.com/</link>
                <item>
                  <guid>latin1-flags</guid>
                  <title>Straße Alt</title>
                  <link>https://example.com/latin1-flags</link>
                  <description><![CDATA[<p>Grüße aus Köln</p>]]></description>
                </item>
              </channel>
            </rss>
        """.trimIndent()
        val secondXml = """
            <?xml version="1.0" encoding="ISO-8859-1"?>
            <rss version="2.0">
              <channel>
                <title>Test Feed</title>
                <link>https://example.com/</link>
                <item>
                  <guid>latin1-flags</guid>
                  <title>Straße Neu</title>
                  <link>https://example.com/latin1-flags</link>
                  <description><![CDATA[<p>Schöne Grüße aus München</p>]]></description>
                </item>
              </channel>
            </rss>
        """.trimIndent()

        enqueue(
            url = url,
            response = QueuedHttpResponse(
                code = 200,
                body = String(firstXml.toByteArray(Charsets.ISO_8859_1), Charsets.ISO_8859_1),
                contentType = "application/rss+xml; charset=ISO-8859-1"
            )
        )
        assertEquals(1, repository.refreshAll().newArticles)

        val initialArticle = articleDao.getByFeedAndUniqueKeys(feedId, listOf("latin1-flags")).single()
        repository.markRead(initialArticle.id)
        repository.setFavorite(initialArticle.id, true)

        enqueue(
            url = url,
            response = QueuedHttpResponse(
                code = 200,
                body = String(secondXml.toByteArray(Charsets.ISO_8859_1), Charsets.ISO_8859_1),
                contentType = "application/rss+xml; charset=ISO-8859-1"
            )
        )

        assertEquals(0, repository.refreshAll().newArticles)

        val storedArticle = articleDao.getByFeedAndUniqueKeys(feedId, listOf("latin1-flags")).single()

        assertEquals(initialArticle.id, storedArticle.id)
        assertTrue(storedArticle.isRead)
        assertTrue(storedArticle.isFavorite)
        assertEquals("Straße Neu", storedArticle.title)
        assertEquals("Schöne Grüße aus München", storedArticle.plainText)
        assertTrue(searchTitles("Köln", "köln*").isEmpty())
        assertEquals(listOf("Straße Neu"), searchTitles("München", "münchen*"))
        assertEquals(1, articleDao.countSearchIndexRows())
        assertEquals(0, articleDao.countFtsMaintenanceTriggers())
    }

    @Test
    fun refreshAllKeepsUnchangedLatin1ArticleStableAcrossSecondRun() = runTest {
        val url = "https://example.com/feed-latin1-stable.xml"
        val feedId = insertFeed(url)

        val xml = """
            <?xml version="1.0" encoding="ISO-8859-1"?>
            <rss version="2.0">
              <channel>
                <title>Test Feed</title>
                <link>https://example.com/</link>
                <item>
                  <guid>latin1-stable</guid>
                  <title>Straße Stabil</title>
                  <link>https://example.com/latin1-stable</link>
                  <description><![CDATA[<p>Grüße aus Köln</p>]]></description>
                </item>
              </channel>
            </rss>
        """.trimIndent()

        enqueue(
            url = url,
            response = QueuedHttpResponse(
                code = 200,
                body = String(xml.toByteArray(Charsets.ISO_8859_1), Charsets.ISO_8859_1),
                contentType = "application/rss+xml; charset=ISO-8859-1"
            )
        )
        val firstStats = repository.refreshAll()
        val initialArticle = articleDao.getByFeedAndUniqueKeys(feedId, listOf("latin1-stable")).single()

        enqueue(
            url = url,
            response = QueuedHttpResponse(
                code = 200,
                body = String(xml.toByteArray(Charsets.ISO_8859_1), Charsets.ISO_8859_1),
                contentType = "application/rss+xml; charset=ISO-8859-1"
            )
        )
        val secondStats = repository.refreshAll()
        val storedArticle = articleDao.getByFeedAndUniqueKeys(feedId, listOf("latin1-stable")).single()

        assertEquals(1, firstStats.newArticles)
        assertEquals(0, secondStats.newArticles)
        assertEquals(initialArticle.id, storedArticle.id)
        assertEquals("Straße Stabil", storedArticle.title)
        assertEquals("Grüße aus Köln", storedArticle.plainText)
        assertEquals(listOf("Straße Stabil"), searchTitles("Köln", "köln*"))
        assertEquals(1, articleDao.countArticles())
        assertEquals(1, articleDao.countSearchIndexRows())
        assertEquals(0, articleDao.countFtsMaintenanceTriggers())
    }

    private suspend fun insertFeed(url: String, wifiOnly: Boolean = false): Long {
        return feedDao.insert(
            FeedEntity(
                title = "Feed $nextDisplayOrder",
                url = url,
                displayOrder = nextDisplayOrder++,
                wifiOnly = wifiOnly
            )
        )
    }

    private suspend fun searchTitles(query: String, matchQuery: String): List<String> {
        return articleDao.searchArticles(query = query, matchQuery = matchQuery)
            .first()
            .map { it.articleTitle }
    }

    private fun enqueueSuccess(url: String, xml: String) {
        enqueue(
            url = url,
            response = QueuedHttpResponse(
                code = 200,
                body = xml,
                contentType = "application/rss+xml; charset=UTF-8"
            )
        )
    }

    private fun enqueueError(url: String, code: Int, times: Int) {
        repeat(times) {
            enqueue(
                url = url,
                response = QueuedHttpResponse(
                    code = code,
                    body = "",
                    contentType = null
                )
            )
        }
    }

    private fun enqueue(url: String, response: QueuedHttpResponse) {
        responseQueues.getOrPut(url) { ConcurrentLinkedQueue() }
            .add(response)
    }

    private fun buildQueuedClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor { chain ->
                val url = chain.request().url.toString()
                val queuedResponse = responseQueues[url]?.poll()
                    ?: error("No queued response for $url")
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(queuedResponse.code)
                    .message(if (queuedResponse.code in 200..299) "OK" else "Error")
                    .body(
                        queuedResponse.body.toResponseBody(
                            queuedResponse.contentType?.toMediaType()
                        )
                    )
                    .build()
            }
            .build()
    }

    private fun rssXml(uniqueKey: String, title: String, plainText: String): String {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0">
              <channel>
                <title>Test Feed</title>
                <link>https://example.com/</link>
                <item>
                  <guid>$uniqueKey</guid>
                  <title>$title</title>
                  <link>${feedUrlFor(uniqueKey)}</link>
                  <description><![CDATA[<p>$plainText</p>]]></description>
                </item>
              </channel>
            </rss>
        """.trimIndent()
    }

    private fun importableRssXml(
        feedUrl: String,
        uniqueKey: String,
        title: String,
        plainText: String
    ): String {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0" xmlns:atom="http://www.w3.org/2005/Atom">
              <channel>
                <title>Test Feed</title>
                <link>https://example.com/</link>
                <atom:link href="$feedUrl" rel="self" type="application/rss+xml" />
                <item>
                  <guid>$uniqueKey</guid>
                  <title>$title</title>
                  <link>${feedUrlFor(uniqueKey)}</link>
                  <description><![CDATA[<p>$plainText</p>]]></description>
                </item>
              </channel>
            </rss>
        """.trimIndent()
    }

    private fun rssXmlWithoutFeedHint(
        uniqueKey: String,
        title: String,
        plainText: String
    ): String {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0">
              <channel>
                <title>Normale Website</title>
                <link>https://example.com/articles/latest</link>
                <item>
                  <guid>$uniqueKey</guid>
                  <title>$title</title>
                  <link>${feedUrlFor(uniqueKey)}</link>
                  <description><![CDATA[<p>$plainText</p>]]></description>
                </item>
              </channel>
            </rss>
        """.trimIndent()
    }

    private fun feedUrlFor(uniqueKey: String): String {
        return "https://example.com/articles/$uniqueKey"
    }

    private fun rssXmlWithTwoItems(
        firstUniqueKey: String,
        firstTitle: String,
        firstPlainText: String,
        secondUniqueKey: String,
        secondTitle: String,
        secondPlainText: String
    ): String {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0">
              <channel>
                <title>Test Feed</title>
                <link>https://example.com/</link>
                <item>
                  <guid>$firstUniqueKey</guid>
                  <title>$firstTitle</title>
                  <link>https://example.com/articles/$firstUniqueKey</link>
                  <description><![CDATA[<p>$firstPlainText</p>]]></description>
                </item>
                <item>
                  <guid>$secondUniqueKey</guid>
                  <title>$secondTitle</title>
                  <link>https://example.com/articles/$secondUniqueKey</link>
                  <description><![CDATA[<p>$secondPlainText</p>]]></description>
                </item>
              </channel>
            </rss>
        """.trimIndent()
    }

}

private data class QueuedHttpResponse(
    val code: Int,
    val body: String,
    val contentType: String?
)

private class FailingInsertArticleDao(
    private val delegate: ArticleDao
) : ArticleDao by delegate {
    override suspend fun insertAll(items: List<ArticleEntity>): List<Long> {
        throw IllegalStateException("insertAll failed")
    }
}

private class RecordingUpdateArticleDao(
    private val delegate: ArticleDao
) : ArticleDao by delegate {
    val updatedBatches = mutableListOf<List<ArticleEntity>>()

    val updateAllCallCount: Int
        get() = updatedBatches.size

    override suspend fun updateAll(items: List<ArticleEntity>): Int {
        updatedBatches += items.map { it.copy() }
        return delegate.updateAll(items)
    }

    fun updatedUniqueKeys(): List<String> {
        return updatedBatches.flatten().map { it.uniqueKey }
    }

    fun reset() {
        updatedBatches.clear()
    }
}

private class CancellingInsertArticleDao(
    private val delegate: ArticleDao,
    private val cancelledUniqueKey: String
) : ArticleDao by delegate {
    override suspend fun insertAll(items: List<ArticleEntity>): List<Long> {
        if (items.any { it.uniqueKey == cancelledUniqueKey }) {
            throw CancellationException("cancelled during import")
        }
        return delegate.insertAll(items)
    }
}
