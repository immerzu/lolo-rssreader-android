package com.example.rssreader.data.repository

import android.database.sqlite.SQLiteConstraintException
import com.example.rssreader.data.db.ArticleDao
import com.example.rssreader.data.db.ArticleEntity
import com.example.rssreader.data.db.ArticleSearchResult
import com.example.rssreader.data.db.FeedDao
import com.example.rssreader.data.db.FeedEntity
import com.example.rssreader.data.db.FeedSummary
import com.example.rssreader.data.network.FeedFetcher
import com.example.rssreader.data.network.FeedParser
import com.example.rssreader.data.opml.OpmlCodec
import com.example.rssreader.data.opml.OpmlFeedEntry
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import androidx.room.withTransaction
import com.example.rssreader.data.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

data class RefreshRunStats(
    val refreshedFeeds: Int = 0,
    val skippedFeeds: Int = 0,
    val newArticles: Int = 0
)

data class OpmlImportResult(
    val importedFeeds: Int = 0,
    val skippedFeeds: Int = 0,
    val failedFeeds: Int = 0
)

class FeedRepository(
    private val database: AppDatabase,
    private val feedDao: FeedDao,
    private val articleDao: ArticleDao,
    private val fetcher: FeedFetcher,
    private val parser: FeedParser
) {
    fun observeFeedSummaries(): Flow<List<FeedSummary>> = feedDao.observeSummaries()
    fun observeFeeds(): Flow<List<FeedEntity>> = feedDao.observeFeeds()
    fun observeFeed(feedId: Long) = feedDao.observeById(feedId)
    fun observeArticles(feedId: Long) = articleDao.observeByFeed(feedId)
    fun searchArticles(query: String): Flow<List<ArticleSearchResult>> =
        if (query.isBlank()) {
            kotlinx.coroutines.flow.flowOf(emptyList())
        } else {
            articleDao.searchArticles(query)
        }

    suspend fun addFeed(url: String, customTitle: String?, wifiOnly: Boolean): Long =
        runOnIo {
            val parsed = parser.parse(fetcher.fetch(url), url)
            try {
                val feedId = feedDao.insert(
                    FeedEntity(
                        title = parsed.title,
                        customTitle = customTitle?.takeIf { it.isNotBlank() },
                        url = url,
                        siteUrl = parsed.siteUrl,
                        iconUrl = parsed.iconUrl,
                        displayOrder = feedDao.getMaxDisplayOrder() + 1,
                        lastFetchedAt = System.currentTimeMillis(),
                        wifiOnly = wifiOnly
                    )
                )
                insertArticles(feedId = feedId, parsed = parsed)
                feedId
            } catch (_: SQLiteConstraintException) {
                throw IllegalStateException("Dieser Feed ist bereits vorhanden.")
            }
        }

    suspend fun refreshAll(): RefreshRunStats {
        return runOnIo {
            var refreshedFeeds = 0
            var newArticles = 0
            feedDao.getAll().forEach { feed ->
                newArticles += refreshFeedInternal(feed)
                refreshedFeeds += 1
            }
            RefreshRunStats(
                refreshedFeeds = refreshedFeeds,
                newArticles = newArticles
            )
        }
    }

    suspend fun refreshFeed(feedId: Long): Int {
        return runOnIo {
            val feed = feedDao.getById(feedId) ?: return@runOnIo 0
            refreshFeedInternal(feed)
        }
    }

    suspend fun refreshFeedIcon(feedId: Long) {
        runOnIo {
            val feed = feedDao.getById(feedId) ?: return@runOnIo
            val parsed = parser.parse(fetcher.fetch(feed.url), feed.url)
            feedDao.update(
                feed.copy(
                    siteUrl = parsed.siteUrl ?: feed.siteUrl,
                    iconUrl = parsed.iconUrl,
                    lastFetchedAt = feed.lastFetchedAt
                )
            )
        }
    }

    suspend fun refreshAllInBackground(isUnmeteredNetwork: Boolean): RefreshRunStats {
        return runOnIo {
            var refreshedFeeds = 0
            var skippedFeeds = 0
            var newArticles = 0

            feedDao.getAll().forEach { feed ->
                if (feed.wifiOnly && !isUnmeteredNetwork) {
                    skippedFeeds += 1
                    return@forEach
                }

                runCatching { refreshFeedInternal(feed) }
                    .onSuccess { insertedArticles ->
                        refreshedFeeds += 1
                        newArticles += insertedArticles
                    }
            }

            RefreshRunStats(
                refreshedFeeds = refreshedFeeds,
                skippedFeeds = skippedFeeds,
                newArticles = newArticles
            )
        }
    }

    suspend fun updateFeed(feedId: Long, url: String, customTitle: String?, wifiOnly: Boolean) {
        runOnIo {
            val existingFeed = feedDao.getById(feedId) ?: return@runOnIo
            val parsed = parser.parse(fetcher.fetch(url), url)

            if (existingFeed.url != url) {
                articleDao.deleteByFeedId(feedId)
            }

            try {
                feedDao.update(
                    existingFeed.copy(
                        title = parsed.title,
                        customTitle = customTitle?.takeIf { it.isNotBlank() },
                        url = url,
                        siteUrl = parsed.siteUrl,
                        iconUrl = parsed.iconUrl,
                        lastFetchedAt = System.currentTimeMillis(),
                        wifiOnly = wifiOnly
                    )
                )
                insertArticles(feedId = feedId, parsed = parsed)
            } catch (_: SQLiteConstraintException) {
                throw IllegalStateException("Dieser Feed ist bereits vorhanden.")
            }
        }
    }

    suspend fun deleteFeed(feedId: Long) {
        runOnIo { feedDao.deleteById(feedId) }
    }

    suspend fun getFeed(feedId: Long) = runOnIo { feedDao.getById(feedId) }
    suspend fun getArticle(articleId: Long) = runOnIo { articleDao.getById(articleId) }
    suspend fun markRead(articleId: Long) {
        runOnIo { articleDao.markRead(articleId) }
    }

    suspend fun markUnread(articleId: Long) {
        runOnIo { articleDao.markUnread(articleId) }
    }

    suspend fun setFavorite(articleId: Long, isFavorite: Boolean) {
        runOnIo { articleDao.setFavorite(articleId, isFavorite) }
    }

    suspend fun markAllRead(feedId: Long) {
        runOnIo { articleDao.markAllRead(feedId) }
    }

    suspend fun markAllUnread(feedId: Long) {
        runOnIo { articleDao.markAllUnread(feedId) }
    }

    suspend fun markAllReadGlobally() {
        runOnIo { articleDao.markAllReadGlobally() }
    }

    suspend fun markAllUnreadGlobally() {
        runOnIo {
            articleDao.markAllUnreadGlobally()
            feedDao.resetAllOpenedStates()
        }
    }

    suspend fun deleteAllReadEntries() {
        runOnIo { articleDao.deleteAllRead() }
    }

    suspend fun deleteFeedReadEntries(feedId: Long) {
        runOnIo { articleDao.deleteReadByFeedId(feedId) }
    }

    suspend fun deleteAllEntries() {
        runOnIo { articleDao.deleteAllNonFavorite() }
    }

    suspend fun deleteFeedEntries(feedId: Long) {
        runOnIo { articleDao.deleteByFeedId(feedId) }
    }

    suspend fun feedCount(): Int = runOnIo { feedDao.countFeeds() }

    suspend fun markFeedOpened(feedId: Long) {
        runOnIo { feedDao.markOpened(feedId, System.currentTimeMillis()) }
    }

    suspend fun moveFeedUp(feedId: Long) {
        runOnIo {
            database.withTransaction {
                val feeds = feedDao.getAll()
                val currentIndex = feeds.indexOfFirst { it.id == feedId }
                if (currentIndex <= 0) {
                    return@withTransaction
                }
                val currentFeed = feeds[currentIndex]
                val previousFeed = feeds[currentIndex - 1]
                feedDao.updateDisplayOrder(currentFeed.id, previousFeed.displayOrder)
                feedDao.updateDisplayOrder(previousFeed.id, currentFeed.displayOrder)
            }
        }
    }

    suspend fun moveFeedDown(feedId: Long) {
        runOnIo {
            database.withTransaction {
                val feeds = feedDao.getAll()
                val currentIndex = feeds.indexOfFirst { it.id == feedId }
                if (currentIndex == -1 || currentIndex >= feeds.lastIndex) {
                    return@withTransaction
                }
                val currentFeed = feeds[currentIndex]
                val nextFeed = feeds[currentIndex + 1]
                feedDao.updateDisplayOrder(currentFeed.id, nextFeed.displayOrder)
                feedDao.updateDisplayOrder(nextFeed.id, currentFeed.displayOrder)
            }
        }
    }

    suspend fun resetFeedUpdatedAt(feedId: Long) {
        runOnIo {
            val feed = feedDao.getById(feedId) ?: return@runOnIo
            feedDao.update(feed.copy(lastFetchedAt = null))
        }
    }

    suspend fun importOpml(inputStream: InputStream): OpmlImportResult = runOnIo {
        val entries = OpmlCodec.parse(inputStream)
        var importedFeeds = 0
        var skippedFeeds = 0
        var failedFeeds = 0

        entries.forEach { entry ->
            if (feedDao.existsByUrl(entry.url)) {
                skippedFeeds += 1
                return@forEach
            }

            runCatching {
                addFeed(
                    url = entry.url,
                    customTitle = entry.title,
                    wifiOnly = false
                )
            }.onSuccess {
                importedFeeds += 1
            }.onFailure {
                failedFeeds += 1
            }
        }

        OpmlImportResult(
            importedFeeds = importedFeeds,
            skippedFeeds = skippedFeeds,
            failedFeeds = failedFeeds
        )
    }

    suspend fun exportOpml(outputStream: OutputStream): Int = runOnIo {
        val entries = feedDao.getAll().map { feed ->
            OpmlFeedEntry(
                url = feed.url,
                title = feed.customTitle?.ifBlank { null } ?: feed.title
            )
        }
        val xml = OpmlCodec.build(entries)
        outputStream.write(xml.toByteArray(StandardCharsets.UTF_8))
        outputStream.flush()
        entries.size
    }

    private suspend fun refreshFeedInternal(feed: FeedEntity): Int {
        val parsed = parser.parse(fetcher.fetch(feed.url), feed.url)
        feedDao.update(
            feed.copy(
                title = parsed.title,
                siteUrl = parsed.siteUrl ?: feed.siteUrl,
                iconUrl = parsed.iconUrl ?: feed.iconUrl,
                lastFetchedAt = System.currentTimeMillis()
            )
        )
        return insertArticles(feedId = feed.id, parsed = parsed)
    }

    private suspend fun insertArticles(
        feedId: Long,
        parsed: com.example.rssreader.data.network.ParsedFeed
    ): Int {
        return database.withTransaction {
            val articles = createArticles(feedId = feedId, parsed = parsed)
            if (articles.isEmpty()) {
                return@withTransaction 0
            }
            val insertedIds = articleDao.insertAll(articles)
            val conflictingArticles = articles.filterIndexed { index, _ ->
                insertedIds.getOrNull(index) == -1L
            }
            if (conflictingArticles.isNotEmpty()) {
                val existingArticlesByUniqueKey = articleDao
                    .getByFeedAndUniqueKeys(
                        feedId = feedId,
                        uniqueKeys = conflictingArticles.map { it.uniqueKey }
                    )
                    .associateBy { it.uniqueKey }

                conflictingArticles.forEach { article ->
                    val existingArticle = existingArticlesByUniqueKey[article.uniqueKey]
                    if (existingArticle != null &&
                        existingArticle.title == article.title &&
                        existingArticle.link == article.link &&
                        existingArticle.publishedAt == article.publishedAt &&
                        existingArticle.plainText == article.plainText &&
                        existingArticle.contentHtml == article.contentHtml &&
                        existingArticle.imageUrls == article.imageUrls
                    ) {
                        return@forEach
                    }
                    articleDao.updateByUniqueKey(
                        feedId = article.feedId,
                        uniqueKey = article.uniqueKey,
                        title = article.title,
                        link = article.link,
                        publishedAt = article.publishedAt,
                        plainText = article.plainText,
                        contentHtml = article.contentHtml,
                        imageUrls = article.imageUrls
                    )
                }
            }
            insertedIds.count { it != -1L }
        }
    }

    private fun createArticles(
        feedId: Long,
        parsed: com.example.rssreader.data.network.ParsedFeed
    ): List<ArticleEntity> {
        return parsed.items.map { item ->
            ArticleEntity(
                feedId = feedId,
                uniqueKey = item.uniqueKey,
                title = item.title,
                link = item.link,
                publishedAt = item.publishedAt,
                plainText = item.plainText,
                contentHtml = item.contentHtml,
                imageUrls = item.imageUrls.joinToString("\n")
            )
        }
    }

    private suspend fun <T> runOnIo(block: suspend () -> T): T = withContext(Dispatchers.IO) {
        block()
    }
}
