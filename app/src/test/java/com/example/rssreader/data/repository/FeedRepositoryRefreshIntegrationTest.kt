package com.example.rssreader.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.rssreader.data.db.AppDatabase
import com.example.rssreader.data.db.ArticleDao
import com.example.rssreader.data.db.ArticleEntity
import com.example.rssreader.data.db.FeedDao
import com.example.rssreader.data.db.FeedEntity
import com.example.rssreader.data.network.FeedFetcher
import com.example.rssreader.data.network.FeedParser
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
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

        val stats = repository.refreshAllInBackground(isUnmeteredNetwork = false)

        assertEquals(1, stats.refreshedFeeds)
        assertEquals(1, stats.skippedFeeds)
        assertEquals(0, stats.failedFeeds)
        assertEquals(1, stats.newArticles)
        assertEquals(listOf("Normale Reise"), searchTitles("Taipei", "taipei*"))
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

        val stats = repository.refreshAllInBackground(isUnmeteredNetwork = false)

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
                  <link>https://example.com/articles/$uniqueKey</link>
                  <description><![CDATA[<p>$plainText</p>]]></description>
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
