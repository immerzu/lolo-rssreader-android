package com.example.rssreader.data.repository

import android.database.sqlite.SQLiteConstraintException
import android.util.Log
import com.example.rssreader.data.db.ArticleDao
import com.example.rssreader.data.db.ArticleEntity
import com.example.rssreader.data.db.ArticleSearchResult
import com.example.rssreader.data.db.AppDatabase
import com.example.rssreader.data.db.FeedDao
import com.example.rssreader.data.db.FeedEntity
import com.example.rssreader.data.db.FeedSummary
import com.example.rssreader.data.errors.RssReaderException
import com.example.rssreader.data.network.FeedFetcher
import com.example.rssreader.data.network.FeedParser
import com.example.rssreader.data.opml.OpmlCodec
import com.example.rssreader.data.opml.OpmlFeedEntry
import com.example.rssreader.debug.DebugLogger
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

private val importableFeedSelfLinkRegex = Regex(
    """<(?:(?:\w+):)?link\b[^>]*\brel\s*=\s*["']self["'][^>]*\bhref\s*=\s*["']([^"']+)["']|<(?:(?:\w+):)?link\b[^>]*\bhref\s*=\s*["']([^"']+)["'][^>]*\brel\s*=\s*["']self["']""",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
)
private val importableFeedTypedLinkRegex = Regex(
    """<(?:(?:\w+):)?link\b[^>]*\bhref\s*=\s*["']([^"']+)["'][^>]*\btype\s*=\s*["'][^"']*(?:rss|atom|xml)[^"']*["']|<(?:(?:\w+):)?link\b[^>]*\btype\s*=\s*["'][^"']*(?:rss|atom|xml)[^"']*["'][^>]*\bhref\s*=\s*["']([^"']+)["']""",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
)
private val importableFeedTextLinkRegex = Regex(
    """<link>\s*(https?://[^<\s]+)\s*</link>""",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
)

data class RefreshRunStats(
    val refreshedFeeds: Int = 0,
    val skippedFeeds: Int = 0,
    val failedFeeds: Int = 0,
    val retryableFeeds: Int = 0,
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
    fun observeArticleNavigation(feedId: Long) = articleDao.observeNavigationByFeed(feedId)
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
                insertParsedFeed(
                    url = url,
                    customTitle = customTitle,
                    wifiOnly = wifiOnly,
                    parsed = parsed
                )
            } catch (_: SQLiteConstraintException) {
                throw RssReaderException.DuplicateFeed()
            }
        }

    suspend fun refreshAll(): RefreshRunStats {
        return runOnIo {
            DebugLogger.i(TAG, "refreshAll gestartet")
            var refreshedFeeds = 0
            var skippedFeeds = 0
            var failedFeeds = 0
            var retryableFeeds = 0
            var newArticles = 0
            feedDao.getAll().forEach { feed ->
                runCatching { refreshFeedInternal(feed) }
                    .onSuccess { insertedArticles ->
                        refreshedFeeds += 1
                        newArticles += insertedArticles
                    }
                    .onFailure { throwable ->
                        failedFeeds += 1
                        if (throwable.isRetryableRefreshFailure()) {
                            retryableFeeds += 1
                        }
                        Log.w(TAG, "Feed-Aktualisierung fehlgeschlagen: ${feed.url}", throwable)
                    }
            }
            RefreshRunStats(
                refreshedFeeds = refreshedFeeds,
                skippedFeeds = skippedFeeds,
                failedFeeds = failedFeeds,
                retryableFeeds = retryableFeeds,
                newArticles = newArticles
            ).also { stats ->
                DebugLogger.i(
                    TAG,
                    "refreshAll beendet: refreshed=${stats.refreshedFeeds}, failed=${stats.failedFeeds}, skipped=${stats.skippedFeeds}, new=${stats.newArticles}"
                )
            }
        }
    }

    suspend fun refreshFeed(feedId: Long): Int {
        return runOnIo {
            val feed = feedDao.getById(feedId) ?: return@runOnIo 0
            DebugLogger.i(TAG, "refreshFeed gestartet: feedId=$feedId, url=${feed.url}")
            refreshFeedInternal(feed).also { inserted ->
                DebugLogger.i(
                    TAG,
                    "refreshFeed beendet: feedId=$feedId, inserted=$inserted"
                )
            }
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
            var failedFeeds = 0
            var retryableFeeds = 0
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
                    .onFailure { throwable ->
                        failedFeeds += 1
                        if (throwable.isRetryableRefreshFailure()) {
                            retryableFeeds += 1
                        }
                        Log.w(TAG, "Hintergrund-Aktualisierung fehlgeschlagen: ${feed.url}", throwable)
                    }
            }

            RefreshRunStats(
                refreshedFeeds = refreshedFeeds,
                skippedFeeds = skippedFeeds,
                failedFeeds = failedFeeds,
                retryableFeeds = retryableFeeds,
                newArticles = newArticles
            )
        }
    }

    suspend fun updateFeed(feedId: Long, url: String, customTitle: String?, wifiOnly: Boolean) {
        runOnIo {
            val existingFeed = feedDao.getById(feedId) ?: return@runOnIo
            val parsed = parser.parse(fetcher.fetch(url), url)
            val urlChanged = existingFeed.url != url

            try {
                database.withTransaction {
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
                    if (urlChanged) {
                        articleDao.deleteByFeedId(feedId)
                    }
                    insertArticlesInCurrentTransaction(feedId = feedId, parsed = parsed)
                }
            } catch (_: SQLiteConstraintException) {
                throw RssReaderException.DuplicateFeed()
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
        DebugLogger.i(TAG, "Import gestartet")
        val importBytes = inputStream.readBytes()
        val entries = runCatching {
            OpmlCodec.parse(ByteArrayInputStream(importBytes))
        }.getOrElse { throwable ->
            if (throwable is RssReaderException.InvalidXml) {
                emptyList()
            } else {
                throw throwable
            }
        }
        var importedFeeds = 0
        var skippedFeeds = 0
        var failedFeeds = 0

        if (entries.isEmpty()) {
            val xml = importBytes.toString(StandardCharsets.UTF_8)
            val feedUrl = detectImportableFeedUrl(xml)
            val parsed = runCatching { parser.parse(xml, feedUrl) }.getOrElse { throwable ->
                if (throwable is RssReaderException.InvalidXml) {
                    throw RssReaderException.UnsupportedImportFile()
                }
                throw throwable
            }
            if (feedUrl.isNullOrBlank()) {
                return@runOnIo OpmlImportResult(
                    importedFeeds = 0,
                    skippedFeeds = 0,
                    failedFeeds = 0
                ).also {
                    DebugLogger.w(TAG, "Importdatei enthielt keine erkennbare Feed-URL")
                }
            }
            if (feedDao.existsByUrl(feedUrl)) {
                return@runOnIo OpmlImportResult(
                    importedFeeds = 0,
                    skippedFeeds = 1,
                    failedFeeds = 0
                ).also {
                    DebugLogger.i(TAG, "Import uebersprungen, Feed existiert bereits: $feedUrl")
                }
            }

            return@runOnIo runCatching {
                insertParsedFeed(
                    url = feedUrl,
                    customTitle = null,
                    wifiOnly = false,
                    parsed = parsed
                )
            }.fold(
                onSuccess = {
                    OpmlImportResult(importedFeeds = 1, skippedFeeds = 0, failedFeeds = 0).also {
                        DebugLogger.i(TAG, "Einzelner Feed aus XML importiert: $feedUrl")
                    }
                },
                onFailure = { throwable ->
                    if (throwable is SQLiteConstraintException) {
                        OpmlImportResult(importedFeeds = 0, skippedFeeds = 1, failedFeeds = 0).also {
                            DebugLogger.i(TAG, "Import uebersprungen, Feed existiert bereits: $feedUrl")
                        }
                    } else {
                        throw throwable
                    }
                }
            )
        }

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
        ).also { result ->
            DebugLogger.i(
                TAG,
                "Import beendet: imported=${result.importedFeeds}, skipped=${result.skippedFeeds}, failed=${result.failedFeeds}"
            )
        }
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
        entries.size.also { count ->
            DebugLogger.i(TAG, "Export beendet: feeds=$count")
        }
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
            insertArticlesInCurrentTransaction(feedId = feedId, parsed = parsed)
        }
    }

    private suspend fun insertArticlesInCurrentTransaction(
        feedId: Long,
        parsed: com.example.rssreader.data.network.ParsedFeed
    ): Int {
        val articles = createArticles(feedId = feedId, parsed = parsed)
        if (articles.isEmpty()) {
            return 0
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
        return insertedIds.count { it != -1L }
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

    private suspend fun insertParsedFeed(
        url: String,
        customTitle: String?,
        wifiOnly: Boolean,
        parsed: com.example.rssreader.data.network.ParsedFeed
    ): Long {
        return database.withTransaction {
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
            insertArticlesInCurrentTransaction(feedId = feedId, parsed = parsed)
            feedId
        }
    }

    companion object {
        private const val TAG = "FeedRepository"
    }
}

private fun Throwable.isRetryableRefreshFailure(): Boolean {
    return when (this) {
        is RssReaderException.Timeout,
        is RssReaderException.NetworkUnavailable,
        is RssReaderException.ConnectionFailed -> true

        is RssReaderException.HttpError -> code == 429 || code in 500..599
        else -> false
    }
}

internal fun detectImportableFeedUrl(xml: String): String? {
    return sequenceOf(
        importableFeedSelfLinkRegex.extractHttpUrl(xml),
        importableFeedTypedLinkRegex.extractHttpUrl(xml),
        importableFeedTextLinkRegex.extractHttpUrl(xml)?.takeIf(::looksLikeFeedUrl)
    ).firstOrNull { !it.isNullOrBlank() }
}

private fun Regex.extractHttpUrl(xml: String): String? {
    return find(xml)
        ?.groupValues
        ?.drop(1)
        ?.firstOrNull { it.isNotBlank() }
        ?.trim()
        ?.takeIf(::isHttpUrl)
}

private fun isHttpUrl(url: String): Boolean {
    return url.startsWith("http://", ignoreCase = true) ||
        url.startsWith("https://", ignoreCase = true)
}

private fun looksLikeFeedUrl(url: String): Boolean {
    val normalized = url.lowercase()
    return "/feed" in normalized ||
        ".xml" in normalized ||
        "rss" in normalized ||
        "atom" in normalized
}


