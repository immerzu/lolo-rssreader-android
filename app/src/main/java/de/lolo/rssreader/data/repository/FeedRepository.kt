package de.lolo.rssreader.data.repository

import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import de.lolo.rssreader.data.db.ArticleDao
import de.lolo.rssreader.data.db.ArticleEntity
import de.lolo.rssreader.data.db.ArticleSearchResult
import de.lolo.rssreader.data.db.AppDatabase
import de.lolo.rssreader.data.db.FeedDao
import de.lolo.rssreader.data.db.FeedEntity
import de.lolo.rssreader.data.db.FeedSummary
import de.lolo.rssreader.data.errors.RssReaderException
import de.lolo.rssreader.data.network.FeedFetcher
import de.lolo.rssreader.data.network.FeedParser
import de.lolo.rssreader.data.opml.OpmlCodec
import de.lolo.rssreader.data.opml.OpmlFeedEntry
import de.lolo.rssreader.debug.DebugLogger
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

private data class OpmlImportOutcome(
    val imported: Boolean = false,
    val skipped: Boolean = false,
    val failedUrl: String? = null
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

// Refresh/Import/Export-Lebenszyklus delegiert an Support-Dateien:
// FTS → FeedRepositoryFtsSupport, OPML → FeedRepositoryOpmlSupport, Refresh → FeedRepositoryRefreshSupport.
// Artikel-Hilfsfunktionen am Dateiende bleiben hier – zu eng mit Room-Entity-Lifecycle gekoppelt.
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
    private val refreshGuard = Any()

    private fun startRefresh(): Boolean = synchronized(refreshGuard) {
        if (refreshInProgress) {
            DebugLogger.w(TAG, "refreshAll abgebrochen: paralleler Refresh läuft bereits")
            false
        } else {
            refreshInProgress = true
            true
        }
    }

    private fun finishRefresh() {
        refreshInProgress = false
    }

    @Volatile
    private var refreshInProgress = false
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

    suspend fun addFeed(
        url: String,
        customTitle: String?,
        wifiOnly: Boolean,
        displayOrder: Int? = null
    ): Long =
        runOnIo {
            val fetchResult = fetchAndParseFeed(url)
            val success = fetchResult as de.lolo.rssreader.data.network.FeedFetchResult.Success
            val parsed = parser.parse(success.payload, url)
            try {
                insertParsedFeed(
                    url = url,
                    customTitle = customTitle,
                    wifiOnly = wifiOnly,
                    parsed = parsed,
                    displayOrder = displayOrder,
                    etag = success.etag,
                    lastModified = success.lastModified,
                    heavy = success.payload.defensiveMode
                )
            } catch (_: SQLiteConstraintException) {
                throw RssReaderException.DuplicateFeed()
            }
        }

    suspend fun refreshAll(hasWifiConnection: Boolean? = null): RefreshRunStats {
        return runOnIo {
            if (!startRefresh()) {
                return@runOnIo RefreshRunStats()
            }
            try {
                DebugLogger.i(TAG, "refreshAll gestartet")
                val feeds = feedDao.getAll()
                val refreshableFeeds = mutableListOf<FeedEntity>()
                var skippedFeeds = 0

                feeds.forEach { feed ->
                    if (hasWifiConnection != null && feed.wifiOnly && !hasWifiConnection) {
                        skippedFeeds += 1
                    } else {
                        refreshableFeeds += feed
                    }
                }

                refreshFeedsBounded(
                    feeds = refreshableFeeds,
                    skippedFeeds = skippedFeeds
                ).let { stats ->
                    rememberRefreshRunStats(stats).also {
                        DebugLogger.i(
                            TAG,
                            "refreshAll beendet: refreshed=${stats.refreshedFeeds}, failed=${stats.failedFeeds}, skipped=${stats.skippedFeeds}, new=${stats.newArticles}"
                        )
                    }
                }
            } finally {
                finishRefresh()
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
            val fetchResult = fetchAndParseFeed(feed.url)
            val success = fetchResult as de.lolo.rssreader.data.network.FeedFetchResult.Success
            val parsed = parser.parse(success.payload, feed.url)
            feedDao.update(
                feed.copy(
                    siteUrl = parsed.siteUrl ?: feed.siteUrl,
                    iconUrl = parsed.iconUrl,
                    lastFetchedAt = feed.lastFetchedAt,
                    etag = success.etag ?: feed.etag,
                    lastModified = success.lastModified ?: feed.lastModified
                )
            )
        }
    }

    suspend fun refreshAllInBackground(
        isUnmeteredNetwork: Boolean,
        hasWifiConnection: Boolean = isUnmeteredNetwork
    ): RefreshRunStats {
        return runOnIo {
            if (!startRefresh()) {
                return@runOnIo RefreshRunStats()
            }
            try {
                val feeds = feedDao.getAll()
                val refreshableFeeds = mutableListOf<FeedEntity>()
                var skippedFeeds = 0

                feeds.forEach { feed ->
                    if (feed.wifiOnly && !hasWifiConnection) {
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
            } finally {
                finishRefresh()
            }
        }
    }

    suspend fun updateFeed(feedId: Long, url: String, customTitle: String?, wifiOnly: Boolean) {
        runOnIo {
            val existingFeed = feedDao.getById(feedId) ?: return@runOnIo
            val fetchResult = fetchAndParseFeed(url)
            val success = fetchResult as de.lolo.rssreader.data.network.FeedFetchResult.Success
            val urlChanged = existingFeed.url != url
            val becameHeavy = !existingFeed.heavy && success.payload.defensiveMode
            val effectivePayload = if (existingFeed.heavy && !success.payload.defensiveMode) {
                success.payload.copy(defensiveMode = true)
            } else {
                success.payload
            }
            val parsed = parser.parse(effectivePayload, url)
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
                            wifiOnly = wifiOnly,
                            etag = if (urlChanged) success.etag else (success.etag ?: existingFeed.etag),
                            lastModified = if (urlChanged) success.lastModified else (success.lastModified ?: existingFeed.lastModified),
                            heavy = existingFeed.heavy || becameHeavy
                        )
                    )
                    if (urlChanged) {
                        articleDao.deleteByFeedId(feedId)
                    }
                    insertArticlesInCurrentTransaction(
                        feedId = feedId,
                        parsed = parsed,
                        searchIndexMayContainStaleRows = urlChanged,
                        heavy = existingFeed.heavy || becameHeavy
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

    suspend fun deleteAllFeeds(): Int {
        return runOnIo {
            ensureManualSearchIndexMaintenance()
            database.withTransaction {
                val deletedFeeds = feedDao.countFeeds()
                feedDao.deleteAll()
                articleDao.deleteStaleSearchIndexEntries()
                deletedFeeds
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
        val xml = importBytes.toString(StandardCharsets.UTF_8)
        val detectedFeedUrl = OpmlImportSupport.detectImportableFeedUrl(xml)
        val entries = runCatching {
            OpmlImportSupport.parseEntriesOrEmpty(importBytes)
        }.getOrElse { throwable ->
            if (throwable is RssReaderException.InvalidXml) {
                if (detectedFeedUrl.isNullOrBlank()) {
                    throw RssReaderException.UnsupportedImportFile()
                }
                emptyList()
            } else {
                throw throwable
            }
        }

        if (entries.isEmpty()) {
            val feedUrl = detectedFeedUrl
            val parsed = runCatching { parser.parse(xml, feedUrl) }.getOrElse { throwable ->
                if (throwable is RssReaderException.InvalidXml) {
                    throw RssReaderException.UnsupportedImportFile()
                }
                throw throwable
            }
            if (feedUrl.isNullOrBlank()) {
                DebugLogger.w(TAG, "Importdatei enthielt keine erkennbare Feed-URL")
                throw RssReaderException.UnsupportedImportFile()
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

        val baseDisplayOrder = feedDao.getMaxDisplayOrder()
        val outcomes = entries.mapIndexed { index, entry ->
            importOpmlEntry(
                entry = entry,
                displayOrder = baseDisplayOrder + index + 1
            )
        }

        val importedFeeds = outcomes.count { it.imported }
        val skippedFeeds = outcomes.count { it.skipped }
        val failedFeeds = outcomes.count { it.failedUrl != null }
        val firstFailedFeedUrl = outcomes.firstNotNullOfOrNull { it.failedUrl }

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
        // Heavy-Feed-Throttling: Mindestintervall zwischen Refreshes, um Bandbreite
        // und Akku zu schonen. Gilt für automatische und manuelle Refreshes.
        if (feed.heavy && feed.lastFetchedAt != null) {
            val elapsed = System.currentTimeMillis() - feed.lastFetchedAt
            if (elapsed < HEAVY_FEED_MIN_REFRESH_INTERVAL_MS) {
                DebugLogger.i(
                    TAG,
                    "refreshFeedInternal: Heavy-Feed-Refresh uebersprungen (Intervall): feedId=${feed.id} elapsedMin=${elapsed / 60_000}"
                )
                return 0
            }
        }
        val fetchResult = fetchAndParseFeed(
            url = feed.url,
            etag = feed.etag,
            lastModified = feed.lastModified
        )
        if (fetchResult is de.lolo.rssreader.data.network.FeedFetchResult.NotModified) {
            feedDao.update(
                feed.copy(lastFetchedAt = System.currentTimeMillis())
            )
            DebugLogger.i(TAG, "refreshFeedInternal: Feed unveraendert (304): feedId=${feed.id}")
            return 0
        }
        val success = fetchResult as de.lolo.rssreader.data.network.FeedFetchResult.Success
        // ETag-Fast-Path: Wenn der Server 200 liefert, aber denselben ETag wie beim letzten
        // Abruf, ist der Feed inhaltlich unverändert. Parsing, Artikel-Update und FTS werden
        // übersprungen – nur lastFetchedAt wird aktualisiert.
        if (feed.etag != null && success.etag != null && feed.etag == success.etag) {
            feedDao.update(feed.copy(lastFetchedAt = System.currentTimeMillis()))
            DebugLogger.i(TAG, "refreshFeedInternal: Feed inhaltlich unveraendert (200 + gleicher ETag): feedId=${feed.id}")
            return 0
        }
        // Heavy-Feed-Erkennung: Wenn der Server defensiveMode ausgelöst hat (Body > 5 MB),
        // wird der Feed dauerhaft als "heavy" markiert. Bei bereits als heavy markierten
        // Feeds wird defensiveMode immer erzwungen, auch wenn der Body diesmal kleiner ist.
        val becameHeavy = !feed.heavy && success.payload.defensiveMode
        val payload = if (feed.heavy && !success.payload.defensiveMode) {
            DebugLogger.i(TAG, "refreshFeedInternal: defensiveMode erzwungen (heavy feed): feedId=${feed.id}")
            success.payload.copy(defensiveMode = true)
        } else {
            success.payload
        }
        val parsed = parser.parse(payload, feed.url)
        return database.withTransaction {
            feedDao.update(
                buildRefreshedFeedEntity(
                    existingFeed = feed,
                    parsed = parsed,
                    fetchedAt = System.currentTimeMillis(),
                    etag = success.etag,
                    lastModified = success.lastModified,
                    heavy = feed.heavy || becameHeavy
                )
            )
            if (becameHeavy) {
                DebugLogger.i(TAG, "feed_heavy_marked feedId=${feed.id} url=${feed.url} bytes=${success.payload.byteSize}")
            }
            insertArticlesInCurrentTransaction(
                feedId = feed.id,
                parsed = parsed,
                searchIndexMayContainStaleRows = false,
                heavy = feed.heavy || becameHeavy
            )
        }
    }

    private suspend fun insertArticlesInCurrentTransaction(
        feedId: Long,
        parsed: de.lolo.rssreader.data.network.ParsedFeed,
        searchIndexMayContainStaleRows: Boolean,
        heavy: Boolean = false
    ): Int {
        ensureManualSearchIndexMaintenance()
        val articles = createArticles(feedId = feedId, parsed = parsed)
        if (articles.isEmpty()) {
            if (searchIndexMayContainStaleRows) {
                articleDao.deleteStaleSearchIndexEntries()
            }
            DebugLogger.i(
                TAG,
                "article_write feedId=$feedId inserted=0 updated=0 unchanged=0 ftsMode=manual ftsSync=false ftsCleanup=$searchIndexMayContainStaleRows heavy=$heavy"
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
        // FTS-Synchronisierung bei heavy Feeds überspringen: der Content ist
        // bereits getruncated, und die FTS-Indizierung wäre teuer bei geringem Nutzen.
        val shouldSyncSearchIndex = !heavy && shouldSyncSearchIndexByFeed(
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
            "article_write feedId=$feedId inserted=$insertedArticles updated=$updatedArticles unchanged=$unchangedArticles ftsMode=manual ftsSync=$shouldSyncSearchIndex ftsCleanup=$searchIndexMayContainStaleRows heavy=$heavy"
        )
        return insertedArticles
    }

    private fun createArticles(
        feedId: Long,
        parsed: de.lolo.rssreader.data.network.ParsedFeed
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
        parsed: de.lolo.rssreader.data.network.ParsedFeed,
        fetchedAt: Long,
        etag: String?,
        lastModified: String?,
        heavy: Boolean
    ): FeedEntity {
        return existingFeed.copy(
            title = parsed.title,
            siteUrl = parsed.siteUrl ?: existingFeed.siteUrl,
            iconUrl = parsed.iconUrl ?: existingFeed.iconUrl,
            lastFetchedAt = fetchedAt,
            etag = etag ?: existingFeed.etag,
            lastModified = lastModified ?: existingFeed.lastModified,
            heavy = heavy
        )
    }

    private suspend fun <T> runOnIo(block: suspend () -> T): T = withContext(ioDispatcher) {
        block()
    }

    private suspend fun fetchAndParseFeed(
        url: String,
        etag: String? = null,
        lastModified: String? = null
    ): de.lolo.rssreader.data.network.FeedFetchResult {
        return fetcher.fetch(url, etag = etag, lastModified = lastModified)
    }

    private suspend fun importOpmlEntry(
        entry: OpmlFeedEntry,
        displayOrder: Int
    ): OpmlImportOutcome {
        if (feedDao.existsByUrl(entry.url)) {
            return OpmlImportOutcome(skipped = true)
        }

        return runCatching {
            addFeed(
                url = entry.url,
                customTitle = entry.title,
                wifiOnly = false,
                displayOrder = displayOrder
            )
        }.fold(
            onSuccess = {
                OpmlImportOutcome(imported = true)
            },
            onFailure = { throwable ->
                if (throwable is CancellationException) {
                    throw throwable
                }
                if (throwable is RssReaderException.DuplicateFeed) {
                    DebugLogger.i(TAG, "Import uebersprungen, Feed existiert bereits: ${entry.url}")
                    OpmlImportOutcome(skipped = true)
                } else {
                    DebugLogger.w(
                        TAG,
                        "Feed-Import fehlgeschlagen: url=${entry.url}, title=${entry.title.orEmpty()}",
                        throwable
                    )
                    OpmlImportOutcome(failedUrl = entry.url)
                }
            }
        )
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
        DebugLogger.w(TAG, "$logPrefix fehlgeschlagen: ${feed.url}", throwable)
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
        parsed: de.lolo.rssreader.data.network.ParsedFeed,
        displayOrder: Int? = null,
        etag: String? = null,
        lastModified: String? = null,
        heavy: Boolean = false
    ): Long {
        return database.withTransaction {
            val feedId = feedDao.insert(
                FeedEntity(
                    title = parsed.title,
                    customTitle = customTitle?.takeIf { it.isNotBlank() },
                    url = url,
                    siteUrl = parsed.siteUrl,
                    iconUrl = parsed.iconUrl,
                    displayOrder = displayOrder ?: (feedDao.getMaxDisplayOrder() + 1),
                    lastFetchedAt = System.currentTimeMillis(),
                    wifiOnly = wifiOnly,
                    etag = etag,
                    lastModified = lastModified,
                    heavy = heavy
                )
            )
            insertArticlesInCurrentTransaction(
                feedId = feedId,
                parsed = parsed,
                searchIndexMayContainStaleRows = false,
                heavy = heavy
            )
            if (etag != null || lastModified != null) {
                DebugLogger.i(TAG, "feed_cache_saved feedId=$feedId url=$url etag=${etag != null} lastModified=${lastModified != null}")
            }
            if (heavy) {
                DebugLogger.i(TAG, "feed_heavy_marked feedId=$feedId url=$url reason=defensiveImport")
            }
            feedId
        }
    }

    companion object {
        private const val TAG = "FeedRepository"
        private const val HEAVY_FEED_MIN_REFRESH_INTERVAL_MS = 6L * 60 * 60 * 1000 // 6 Stunden
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



