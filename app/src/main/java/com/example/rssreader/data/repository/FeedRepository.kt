package com.example.rssreader.data.repository

import android.database.sqlite.SQLiteConstraintException
import android.util.Log
import androidx.room.withTransaction
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
private const val REFRESH_PARALLELISM = 2

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
    val failedFeeds: Int = 0,
    val firstFailedFeedUrl: String? = null
)

data class RepositoryDiagnosticsSnapshot(
    val feedCount: Int,
    val articleCount: Int,
    val searchIndexRowCount: Int,
    val manualFtsMode: Boolean,
    val lastRefreshRunStats: RefreshRunStats?,
    val lastImportResult: OpmlImportResult?,
    val debugLogFilePath: String?
)

class FeedRepository(
    private val database: AppDatabase,
    private val feedDao: FeedDao,
    private val articleDao: ArticleDao,
    private val fetcher: FeedFetcher,
    private val parser: FeedParser,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    // Fresh databases at the current schema version never create the old FTS triggers.
    // MIGRATION_7_8 removes that historical v6 -> v7 trigger legacy from supported upgrades.
    // Manual repository-side FTS maintenance remains the only intended runtime source of truth.
    @Volatile
    private var lastRefreshRunStats: RefreshRunStats? = null
    @Volatile
    private var lastImportResult: OpmlImportResult? = null
    private val ftsMaintenance = FtsMaintenance(database) { droppedTriggers ->
        DebugLogger.i(TAG, "fts_mode mode=manual droppedTriggers=$droppedTriggers")
    }

    fun observeFeedSummaries(): Flow<List<FeedSummary>> = feedDao.observeSummaries()
    fun observeFeeds(): Flow<List<FeedEntity>> = feedDao.observeFeeds()
    fun observeFeed(feedId: Long) = feedDao.observeById(feedId)
    fun observeArticles(feedId: Long) = articleDao.observeByFeed(feedId)
    fun observeArticleNavigation(feedId: Long) = articleDao.observeNavigationByFeed(feedId)
    fun searchArticles(query: String): Flow<List<ArticleSearchResult>> =
        if (query.isBlank()) {
            kotlinx.coroutines.flow.flowOf(emptyList())
        } else {
            val searchSpec = buildArticleSearchSpec(query)
            if (searchSpec.matchQuery == null) {
                articleDao.searchArticlesFallback(searchSpec.query)
            } else {
                articleDao.searchArticles(
                    query = searchSpec.query,
                    matchQuery = searchSpec.matchQuery
                )
            }
        }

    suspend fun addFeed(url: String, customTitle: String?, wifiOnly: Boolean): Long =
        runOnIo {
            val parsed = fetchAndParseFeed(url)
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
            refreshFeedsBounded(feedDao.getAll()).let { stats ->
                rememberRefreshRunStats(stats).also {
                    DebugLogger.i(
                        TAG,
                        "refreshAll beendet: refreshed=${stats.refreshedFeeds}, failed=${stats.failedFeeds}, skipped=${stats.skippedFeeds}, new=${stats.newArticles}"
                    )
                }
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
            val parsed = fetchAndParseFeed(feed.url)
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
            val feeds = feedDao.getAll()
            val refreshableFeeds = mutableListOf<FeedEntity>()
            var skippedFeeds = 0

            feeds.forEach { feed ->
                if (feed.wifiOnly && !isUnmeteredNetwork) {
                    skippedFeeds += 1
                } else {
                    refreshableFeeds += feed
                }
            }

            refreshFeedsBounded(
                feeds = refreshableFeeds,
                skippedFeeds = skippedFeeds,
                logPrefix = "Hintergrund-Aktualisierung"
            ).let(::rememberRefreshRunStats)
        }
    }

    suspend fun updateFeed(feedId: Long, url: String, customTitle: String?, wifiOnly: Boolean) {
        runOnIo {
            val existingFeed = feedDao.getById(feedId) ?: return@runOnIo
            val parsed = fetchAndParseFeed(url)
            val urlChanged = existingFeed.url != url
            ensureManualSearchIndexMaintenance()

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
                    insertArticlesInCurrentTransaction(
                        feedId = feedId,
                        parsed = parsed,
                        searchIndexMayContainStaleRows = urlChanged
                    )
                }
            } catch (_: SQLiteConstraintException) {
                throw RssReaderException.DuplicateFeed()
            }
        }
    }

    suspend fun deleteFeed(feedId: Long) {
        runOnIo {
            ensureManualSearchIndexMaintenance()
            database.withTransaction {
                feedDao.deleteById(feedId)
                articleDao.deleteStaleSearchIndexEntries()
            }
        }
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

    suspend fun markAllRead(feedId: Long): Int {
        return runOnIo { articleDao.markAllRead(feedId) }
    }

    suspend fun markAllUnread(feedId: Long): Int {
        return runOnIo { articleDao.markAllUnread(feedId) }
    }

    suspend fun markAllReadGlobally(): Int {
        return runOnIo { articleDao.markAllReadGlobally() }
    }

    suspend fun markAllUnreadGlobally(): Int {
        return runOnIo {
            val affectedArticles = articleDao.markAllUnreadGlobally()
            feedDao.resetAllOpenedStates()
            affectedArticles
        }
    }

    suspend fun deleteAllReadEntries(): Int {
        return deleteArticlesWithSearchCleanup { articleDao.deleteAllRead() }
    }

    suspend fun deleteFeedReadEntries(feedId: Long): Int {
        return deleteArticlesWithSearchCleanup { articleDao.deleteReadByFeedId(feedId) }
    }

    suspend fun deleteAllEntries(): Int {
        return deleteArticlesWithSearchCleanup { articleDao.deleteAllNonFavorite() }
    }

    suspend fun deleteFeedEntries(feedId: Long): Int {
        return deleteArticlesWithSearchCleanup { articleDao.deleteByFeedId(feedId) }
    }

    suspend fun feedCount(): Int = runOnIo { feedDao.countFeeds() }

    suspend fun diagnosticsSnapshot(): RepositoryDiagnosticsSnapshot = runOnIo {
        RepositoryDiagnosticsSnapshot(
            feedCount = feedDao.countFeeds(),
            articleCount = articleDao.countArticles(),
            searchIndexRowCount = articleDao.countSearchIndexRows(),
            manualFtsMode = true,
            lastRefreshRunStats = lastRefreshRunStats,
            lastImportResult = lastImportResult,
            debugLogFilePath = DebugLogger.currentLogFilePath()
        )
    }

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
        val importBytes = readImportBytes(inputStream)
        val entries = OpmlImportSupport.parseEntriesOrEmpty(importBytes)
        var importedFeeds = 0
        var skippedFeeds = 0
        var failedFeeds = 0
        var firstFailedFeedUrl: String? = null

        if (entries.isEmpty()) {
            val xml = importBytes.toString(StandardCharsets.UTF_8)
            val feedUrl = OpmlImportSupport.detectImportableFeedUrl(xml)
            val parsed = runCatching { parser.parse(xml, feedUrl) }.getOrElse { throwable ->
                if (throwable is RssReaderException.InvalidXml) {
                    throw RssReaderException.UnsupportedImportFile()
                }
                throw throwable
            }
            if (feedUrl.isNullOrBlank()) {
                return@runOnIo rememberImportCounts(
                    importedFeeds = 0,
                    skippedFeeds = 0,
                    failedFeeds = 0
                ).also {
                    DebugLogger.w(TAG, "Importdatei enthielt keine erkennbare Feed-URL")
                }
            }
            if (feedDao.existsByUrl(feedUrl)) {
                return@runOnIo rememberImportCounts(
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
                    rememberImportCounts(
                        importedFeeds = 1,
                        skippedFeeds = 0,
                        failedFeeds = 0
                    ).also {
                        DebugLogger.i(TAG, "Einzelner Feed aus XML importiert: $feedUrl")
                    }
                },
                onFailure = { throwable ->
                    if (throwable is SQLiteConstraintException) {
                        rememberImportCounts(
                            importedFeeds = 0,
                            skippedFeeds = 1,
                            failedFeeds = 0
                        ).also {
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
            }.onFailure { throwable ->
                if (throwable is CancellationException) {
                    throw throwable
                }
                failedFeeds += 1
                if (firstFailedFeedUrl == null) {
                    firstFailedFeedUrl = entry.url
                }
                DebugLogger.w(
                    TAG,
                    "Feed-Import fehlgeschlagen: url=${entry.url}, title=${entry.title.orEmpty()}",
                    throwable
                )
            }
        }

        rememberImportCounts(
            importedFeeds = importedFeeds,
            skippedFeeds = skippedFeeds,
            failedFeeds = failedFeeds,
            firstFailedFeedUrl = firstFailedFeedUrl
        ).let { result ->
            result.also {
            DebugLogger.i(
                TAG,
                "Import beendet: imported=${result.importedFeeds}, skipped=${result.skippedFeeds}, failed=${result.failedFeeds}"
            )
            }
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
        val parsed = fetchAndParseFeed(feed.url)
        return database.withTransaction {
            feedDao.update(
                buildRefreshedFeedEntity(
                    existingFeed = feed,
                    parsed = parsed,
                    fetchedAt = System.currentTimeMillis()
                )
            )
            insertArticlesInCurrentTransaction(
                feedId = feed.id,
                parsed = parsed,
                searchIndexMayContainStaleRows = false
            )
        }
    }

    private suspend fun insertArticlesInCurrentTransaction(
        feedId: Long,
        parsed: com.example.rssreader.data.network.ParsedFeed,
        searchIndexMayContainStaleRows: Boolean
    ): Int {
        ensureManualSearchIndexMaintenance()
        val articles = createArticles(feedId = feedId, parsed = parsed)
        if (articles.isEmpty()) {
            if (searchIndexMayContainStaleRows) {
                articleDao.deleteStaleSearchIndexEntries()
            }
            DebugLogger.i(
                TAG,
                "article_write feedId=$feedId inserted=0 updated=0 unchanged=0 ftsMode=manual ftsSync=false ftsCleanup=$searchIndexMayContainStaleRows"
            )
            return 0
        }
        val insertedIds = articleDao.insertAll(articles)
        val conflictingArticles = collectConflictingArticles(
            articles = articles,
            insertedIds = insertedIds
        )
        var updatedArticles = 0
        var unchangedArticles = 0
        if (conflictingArticles.isNotEmpty()) {
            val latestConflictingArticles = collapseConflictingArticlesByUniqueKey(conflictingArticles)
            val existingArticlesByUniqueKey = articleDao
                .getByFeedAndUniqueKeys(
                    feedId = feedId,
                    uniqueKeys = latestConflictingArticles.map { it.uniqueKey }
                )
                .associateBy { it.uniqueKey }
            val changedArticles = ArrayList<ArticleEntity>(latestConflictingArticles.size)

            latestConflictingArticles.forEach { article ->
                val existingArticle = existingArticlesByUniqueKey[article.uniqueKey]
                if (existingArticle != null && hasSameStoredArticleContent(existingArticle, article)) {
                    unchangedArticles += 1
                    return@forEach
                }
                if (existingArticle != null) {
                    changedArticles += buildUpdatedArticleEntity(
                        existingArticle = existingArticle,
                        incomingArticle = article
                    )
                }
            }
            if (changedArticles.isNotEmpty()) {
                articleDao.updateAll(changedArticles)
                updatedArticles = changedArticles.size
            }
        }
        val insertedArticles = insertedIds.count { it != -1L }
        val shouldSyncSearchIndex = shouldSyncSearchIndexByFeed(
            insertedArticles = insertedArticles,
            updatedArticles = updatedArticles
        )
        if (shouldSyncSearchIndex) {
            articleDao.syncSearchIndexByFeed(feedId)
        }
        if (searchIndexMayContainStaleRows) {
            articleDao.deleteStaleSearchIndexEntries()
        }
        DebugLogger.i(
            TAG,
            "article_write feedId=$feedId inserted=$insertedArticles updated=$updatedArticles unchanged=$unchangedArticles ftsMode=manual ftsSync=$shouldSyncSearchIndex ftsCleanup=$searchIndexMayContainStaleRows"
        )
        return insertedArticles
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

    private fun buildRefreshedFeedEntity(
        existingFeed: FeedEntity,
        parsed: com.example.rssreader.data.network.ParsedFeed,
        fetchedAt: Long
    ): FeedEntity {
        return existingFeed.copy(
            title = parsed.title,
            siteUrl = parsed.siteUrl ?: existingFeed.siteUrl,
            iconUrl = parsed.iconUrl ?: existingFeed.iconUrl,
            lastFetchedAt = fetchedAt
        )
    }

    private suspend fun <T> runOnIo(block: suspend () -> T): T = withContext(ioDispatcher) {
        block()
    }

    private suspend fun fetchAndParseFeed(url: String): com.example.rssreader.data.network.ParsedFeed {
        return parser.parse(fetcher.fetch(url), url)
    }

    private fun readImportBytes(inputStream: InputStream): ByteArray {
        return OpmlImportSupport.readImportBytes(inputStream)
    }

    private suspend fun refreshFeedsBounded(
        feeds: List<FeedEntity>,
        skippedFeeds: Int = 0,
        logPrefix: String = "Feed-Aktualisierung"
    ): RefreshRunStats {
        val coordinator = RefreshCoordinator<FeedEntity>(
            parallelism = REFRESH_PARALLELISM,
            task = { feed: FeedEntity ->
                RefreshFeedOutcome(
                    insertedArticles = refreshFeedInternal(feed),
                    retryableFailure = false
                )
            },
            onFailure = { feed: FeedEntity, throwable: Throwable ->
                buildRefreshFailureOutcome(
                    feed = feed,
                    logPrefix = logPrefix,
                    throwable = throwable
                )
            }
        )
        val outcomes = coordinator.run(feeds)
        return buildRefreshRunStats(
            outcomes = outcomes,
            skippedFeeds = skippedFeeds
        )
    }

    private fun buildRefreshFailureOutcome(
        feed: FeedEntity,
        logPrefix: String,
        throwable: Throwable
    ): RefreshFeedOutcome {
        Log.w(TAG, "$logPrefix fehlgeschlagen: ${feed.url}", throwable)
        return RefreshFeedOutcome(
            insertedArticles = null,
            retryableFailure = throwable.isRetryableRefreshFailure()
        )
    }

    private suspend fun deleteArticlesWithSearchCleanup(
        deleteAction: suspend () -> Int
    ): Int = runOnIo {
        ensureManualSearchIndexMaintenance()
        database.withTransaction {
            val deletedArticles = deleteAction()
            if (shouldDeleteStaleSearchIndexEntriesAfterDeletion(deletedArticles)) {
                articleDao.deleteStaleSearchIndexEntries()
            }
            deletedArticles
        }
    }

    private fun rememberImportResult(result: OpmlImportResult): OpmlImportResult {
        lastImportResult = result
        return result
    }

    private fun rememberImportCounts(
        importedFeeds: Int,
        skippedFeeds: Int,
        failedFeeds: Int,
        firstFailedFeedUrl: String? = null
    ): OpmlImportResult {
        return rememberImportResult(
            OpmlImportResult(
                importedFeeds = importedFeeds,
                skippedFeeds = skippedFeeds,
                failedFeeds = failedFeeds,
                firstFailedFeedUrl = firstFailedFeedUrl
            )
        )
    }

    private fun rememberRefreshRunStats(stats: RefreshRunStats): RefreshRunStats {
        lastRefreshRunStats = stats
        return stats
    }

    private suspend fun ensureManualSearchIndexMaintenance() {
        ftsMaintenance.ensureManualMode()
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
            insertArticlesInCurrentTransaction(
                feedId = feedId,
                parsed = parsed,
                searchIndexMayContainStaleRows = false
            )
            feedId
        }
    }

    companion object {
        private const val TAG = "FeedRepository"
    }
}

internal data class ArticleSearchSpec(
    val query: String,
    val matchQuery: String?
)

internal fun buildArticleSearchSpec(query: String): ArticleSearchSpec {
    val normalizedQuery = query.trim()
    val tokens = articleSearchTokenRegex.findAll(normalizedQuery)
        .map { it.value.lowercase() }
        .filter { it.isNotBlank() }
        .distinct()
        .toList()

    if (tokens.isEmpty()) {
        return ArticleSearchSpec(
            query = normalizedQuery,
            matchQuery = null
        )
    }

    return ArticleSearchSpec(
        query = normalizedQuery,
        matchQuery = tokens.joinToString(" AND ") { "$it*" }
    )
}

private val articleSearchTokenRegex = Regex("[\\p{L}\\p{N}]+")

internal fun hasSameStoredArticleContent(
    existingArticle: ArticleEntity,
    incomingArticle: ArticleEntity
): Boolean {
    return existingArticle.title == incomingArticle.title &&
        existingArticle.link == incomingArticle.link &&
        existingArticle.publishedAt == incomingArticle.publishedAt &&
        existingArticle.plainText == incomingArticle.plainText &&
        existingArticle.contentHtml == incomingArticle.contentHtml &&
        existingArticle.imageUrls == incomingArticle.imageUrls
}

internal fun shouldSyncSearchIndexByFeed(
    insertedArticles: Int,
    updatedArticles: Int
): Boolean = insertedArticles > 0 || updatedArticles > 0

internal fun shouldDeleteStaleSearchIndexEntriesAfterDeletion(
    deletedArticles: Int
): Boolean = deletedArticles > 0

internal fun collectConflictingArticles(
    articles: List<ArticleEntity>,
    insertedIds: List<Long>
): List<ArticleEntity> {
    if (articles.isEmpty() || insertedIds.isEmpty()) {
        return emptyList()
    }
    val conflictingArticles = ArrayList<ArticleEntity>(minOf(articles.size, insertedIds.size))
    val limit = minOf(articles.size, insertedIds.size)
    for (index in 0 until limit) {
        if (insertedIds[index] == -1L) {
            conflictingArticles += articles[index]
        }
    }
    return conflictingArticles
}

internal fun collapseConflictingArticlesByUniqueKey(
    conflictingArticles: List<ArticleEntity>
): List<ArticleEntity> {
    if (conflictingArticles.size < 2) {
        return conflictingArticles
    }
    val latestArticlesByUniqueKey = LinkedHashMap<String, ArticleEntity>(conflictingArticles.size)
    conflictingArticles.forEach { article ->
        latestArticlesByUniqueKey[article.uniqueKey] = article
    }
    return latestArticlesByUniqueKey.values.toList()
}

internal fun buildUpdatedArticleEntity(
    existingArticle: ArticleEntity,
    incomingArticle: ArticleEntity
): ArticleEntity {
    return existingArticle.copy(
        title = incomingArticle.title,
        link = incomingArticle.link,
        publishedAt = incomingArticle.publishedAt,
        plainText = incomingArticle.plainText,
        contentHtml = incomingArticle.contentHtml,
        imageUrls = incomingArticle.imageUrls
    )
}

private fun Throwable.isRetryableRefreshFailure(): Boolean = when (this) {
    is RssReaderException.Timeout,
    is RssReaderException.NetworkUnavailable,
    is RssReaderException.ConnectionFailed -> true

    is RssReaderException.HttpError -> code == 429 || code in 500..599
    else -> false
}



