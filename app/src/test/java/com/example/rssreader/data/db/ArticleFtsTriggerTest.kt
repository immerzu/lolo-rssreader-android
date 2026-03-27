package com.example.rssreader.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.rssreader.data.repository.disableLegacyFtsMaintenanceTriggers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ArticleFtsTriggerTest {

    private lateinit var database: AppDatabase
    private lateinit var feedDao: FeedDao
    private lateinit var articleDao: ArticleDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .addMigrations(AppDatabase.MIGRATION_2_3)
            .addMigrations(AppDatabase.MIGRATION_3_4)
            .addMigrations(AppDatabase.MIGRATION_4_5)
            .addMigrations(AppDatabase.MIGRATION_5_6)
            .addMigrations(AppDatabase.MIGRATION_6_7)
            .addMigrations(AppDatabase.MIGRATION_7_8)
            .build()
        feedDao = database.feedDao()
        articleDao = database.articleDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun freshRoomDatabaseNeedsManualFtsSync() = runBlocking {
        val feedId = insertFeed()
        articleDao.insertAll(
            listOf(
                article(
                    feedId = feedId,
                    uniqueKey = "a-1",
                    title = "Asien Reise",
                    plainText = "Thailand Tipps"
                )
            )
        )

        assertEquals(0, articleDao.countFtsMaintenanceTriggers())

        val resultsBeforeSync = articleDao.searchArticles(
            query = "Thailand",
            matchQuery = "thailand*"
        ).first()
        assertTrue(resultsBeforeSync.isEmpty())

        articleDao.syncSearchIndexByFeed(feedId)

        val resultsAfterSync = articleDao.searchArticles(
            query = "Thailand",
            matchQuery = "thailand*"
        ).first()

        assertEquals(1, resultsAfterSync.size)
        assertEquals("Asien Reise", resultsAfterSync.single().articleTitle)
    }

    @Test
    fun freshRoomDatabaseDoesNotCarryLegacyFtsTriggers() = runBlocking {
        insertFeed()

        assertEquals(0, articleDao.countFtsMaintenanceTriggers())
    }

    @Test
    fun freshRoomDatabaseLegacyTriggerCleanupFallbackIsNoOp() = runBlocking {
        insertFeed()

        assertEquals(0, disableLegacyFtsMaintenanceTriggers(database))
        assertEquals(0, articleDao.countFtsMaintenanceTriggers())
    }

    @Test
    fun freshRoomDatabaseManualSyncKeepsUpdateSearchResultsInSync() = runBlocking {
        val feedId = insertFeed()
        articleDao.insertAll(
            listOf(
                article(
                    feedId = feedId,
                    uniqueKey = "a-1",
                    title = "Alter Titel",
                    plainText = "Japan Reise"
                )
            )
        )
        articleDao.syncSearchIndexByFeed(feedId)

        articleDao.updateByUniqueKey(
            feedId = feedId,
            uniqueKey = "a-1",
            title = "Neuer Titel",
            link = "https://example.com/articles/a-1",
            publishedAt = 1_700_000_000_000,
            plainText = "Korea Reise",
            contentHtml = "<p>Korea Reise</p>",
            imageUrls = ""
        )
        articleDao.syncSearchIndexByFeed(feedId)

        val oldResults = articleDao.searchArticles(
            query = "Japan",
            matchQuery = "japan*"
        ).first()
        val newResults = articleDao.searchArticles(
            query = "Korea",
            matchQuery = "korea*"
        ).first()

        assertTrue(oldResults.isEmpty())
        assertEquals(1, newResults.size)
        assertEquals("Neuer Titel", newResults.single().articleTitle)
    }

    @Test
    fun freshRoomDatabaseRepeatedManualSyncPreservesSearchState() = runBlocking {
        val feedId = insertFeed()
        articleDao.insertAll(
            listOf(
                article(
                    feedId = feedId,
                    uniqueKey = "a-1",
                    title = "Bali Reise",
                    plainText = "Ubud Tempel"
                )
            )
        )

        articleDao.syncSearchIndexByFeed(feedId)
        articleDao.syncSearchIndexByFeed(feedId)

        val results = articleDao.searchArticles(
            query = "Ubud",
            matchQuery = "ubud*"
        ).first()

        assertEquals(1, results.size)
        assertEquals(1, articleDao.countSearchIndexRows())
    }

    @Test
    fun deleteCleanupRemovesStaleRowsWhenManualSyncIsUsed() = runBlocking {
        val feedId = insertFeed()
        articleDao.insertAll(
            listOf(
                article(
                    feedId = feedId,
                    uniqueKey = "a-1",
                    title = "Reise Vietnam",
                    plainText = "Hanoi Tipps"
                )
            )
        )
        articleDao.syncSearchIndexByFeed(feedId)

        assertEquals(1, articleDao.countSearchIndexRows())

        articleDao.deleteByFeedId(feedId)
        articleDao.deleteStaleSearchIndexEntries()

        assertEquals(0, articleDao.countSearchIndexRows())
    }

    @Test
    fun migratedDatabaseRemovesLegacyTriggersAndKeepsSearchInSync() = runBlocking {
        database.close()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "fts-migration-${System.nanoTime()}.db"
        val databasePath = context.getDatabasePath(databaseName)
        databasePath.parentFile?.mkdirs()
        if (databasePath.exists()) {
            databasePath.delete()
        }

        createVersion6Database(databasePath.absolutePath)

        val migratedDatabase = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
            .allowMainThreadQueries()
            .addMigrations(AppDatabase.MIGRATION_6_7)
            .addMigrations(AppDatabase.MIGRATION_7_8)
            .build()

        try {
            val migratedArticleDao = migratedDatabase.articleDao()

            assertEquals(0, migratedArticleDao.countFtsMaintenanceTriggers())

            val migratedResults = migratedArticleDao.searchArticles(
                query = "Thailand",
                matchQuery = "thailand*"
            ).first()
            assertEquals(1, migratedResults.size)

            migratedArticleDao.insertAll(
                listOf(
                    article(
                        feedId = 1L,
                        uniqueKey = "a-2",
                        title = "Bali Tipps",
                        plainText = "Ubud Reise"
                    )
                )
            )
            migratedArticleDao.syncSearchIndexByFeed(1L)

            val insertedResults = migratedArticleDao.searchArticles(
                query = "Ubud",
                matchQuery = "ubud*"
            ).first()
            assertEquals(1, insertedResults.size)
            assertEquals(2, migratedArticleDao.countSearchIndexRows())

            migratedArticleDao.updateByUniqueKey(
                feedId = 1L,
                uniqueKey = "a-1",
                title = "Neu Titel",
                link = "https://example.com/articles/a-1",
                publishedAt = 1_700_000_000_000,
                plainText = "Bangkok Reise",
                contentHtml = "<p>Bangkok Reise</p>",
                imageUrls = ""
            )
            migratedArticleDao.syncSearchIndexByFeed(1L)

            val oldResults = migratedArticleDao.searchArticles(
                query = "Thailand",
                matchQuery = "thailand*"
            ).first()
            val updatedResults = migratedArticleDao.searchArticles(
                query = "Bangkok",
                matchQuery = "bangkok*"
            ).first()

            assertTrue(oldResults.isEmpty())
            assertEquals(1, updatedResults.size)

            migratedArticleDao.deleteByFeedId(1L)
            migratedArticleDao.deleteStaleSearchIndexEntries()

            val deletedResults = migratedArticleDao.searchArticles(
                query = "Bangkok",
                matchQuery = "bangkok*"
            ).first()
            assertTrue(deletedResults.isEmpty())
            assertEquals(0, migratedArticleDao.countSearchIndexRows())
        } finally {
            migratedDatabase.close()
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun migratedDatabaseManualSyncKeepsUpdatedRowsSearchable() = runBlocking {
        database.close()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "fts-migration-update-${System.nanoTime()}.db"
        val databasePath = context.getDatabasePath(databaseName)
        databasePath.parentFile?.mkdirs()
        if (databasePath.exists()) {
            databasePath.delete()
        }

        createVersion6Database(databasePath.absolutePath)

        val migratedDatabase = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
            .allowMainThreadQueries()
            .addMigrations(AppDatabase.MIGRATION_6_7)
            .addMigrations(AppDatabase.MIGRATION_7_8)
            .build()

        try {
            val migratedArticleDao = migratedDatabase.articleDao()
            assertEquals(0, migratedArticleDao.countFtsMaintenanceTriggers())

            migratedArticleDao.updateByUniqueKey(
                feedId = 1L,
                uniqueKey = "a-1",
                title = "Neu Titel",
                link = "https://example.com/articles/a-1",
                publishedAt = 1_700_000_000_000,
                plainText = "Bangkok Reise",
                contentHtml = "<p>Bangkok Reise</p>",
                imageUrls = ""
            )
            migratedArticleDao.syncSearchIndexByFeed(1L)

            val oldResults = migratedArticleDao.searchArticles(
                query = "Thailand",
                matchQuery = "thailand*"
            ).first()
            val updatedResults = migratedArticleDao.searchArticles(
                query = "Bangkok",
                matchQuery = "bangkok*"
            ).first()

            assertTrue(oldResults.isEmpty())
            assertEquals(1, updatedResults.size)
            assertEquals("Neu Titel", updatedResults.single().articleTitle)
        } finally {
            migratedDatabase.close()
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun migratedDatabaseRepeatedManualSyncPreservesSearchState() = runBlocking {
        database.close()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "fts-migration-repeat-${System.nanoTime()}.db"
        val databasePath = context.getDatabasePath(databaseName)
        databasePath.parentFile?.mkdirs()
        if (databasePath.exists()) {
            databasePath.delete()
        }

        createVersion6Database(databasePath.absolutePath)

        val migratedDatabase = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
            .allowMainThreadQueries()
            .addMigrations(AppDatabase.MIGRATION_6_7)
            .addMigrations(AppDatabase.MIGRATION_7_8)
            .build()

        try {
            val migratedArticleDao = migratedDatabase.articleDao()

            migratedArticleDao.syncSearchIndexByFeed(1L)
            migratedArticleDao.syncSearchIndexByFeed(1L)

            val results = migratedArticleDao.searchArticles(
                query = "Thailand",
                matchQuery = "thailand*"
            ).first()

            assertEquals(1, results.size)
            assertEquals(1, migratedArticleDao.countSearchIndexRows())
            assertEquals(0, migratedArticleDao.countFtsMaintenanceTriggers())
        } finally {
            migratedDatabase.close()
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun version7To8MigrationDropsLegacyTriggersAndKeepsSearchInSync() = runBlocking {
        database.close()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "fts-migration-7-to-8-${System.nanoTime()}.db"
        val databasePath = context.getDatabasePath(databaseName)
        databasePath.parentFile?.mkdirs()
        if (databasePath.exists()) {
            databasePath.delete()
        }

        createVersion7DatabaseWithLegacyFtsTriggers(databasePath.absolutePath)

        val migratedDatabase = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
            .allowMainThreadQueries()
            .addMigrations(AppDatabase.MIGRATION_7_8)
            .build()

        try {
            val migratedArticleDao = migratedDatabase.articleDao()

            assertEquals(0, migratedArticleDao.countFtsMaintenanceTriggers())

            val migratedResults = migratedArticleDao.searchArticles(
                query = "Thailand",
                matchQuery = "thailand*"
            ).first()
            assertEquals(1, migratedResults.size)
            assertEquals("Asien Reise", migratedResults.single().articleTitle)

            migratedArticleDao.insertAll(
                listOf(
                    article(
                        feedId = 1L,
                        uniqueKey = "a-2",
                        title = "Bali Tipps",
                        plainText = "Ubud Reise"
                    )
                )
            )
            migratedArticleDao.syncSearchIndexByFeed(1L)

            val insertedResults = migratedArticleDao.searchArticles(
                query = "Ubud",
                matchQuery = "ubud*"
            ).first()
            assertEquals(1, insertedResults.size)

            migratedArticleDao.updateByUniqueKey(
                feedId = 1L,
                uniqueKey = "a-1",
                title = "Neu Titel",
                link = "https://example.com/articles/a-1",
                publishedAt = 1_700_000_000_000,
                plainText = "Bangkok Reise",
                contentHtml = "<p>Bangkok Reise</p>",
                imageUrls = ""
            )
            migratedArticleDao.syncSearchIndexByFeed(1L)

            val oldResults = migratedArticleDao.searchArticles(
                query = "Thailand",
                matchQuery = "thailand*"
            ).first()
            val updatedResults = migratedArticleDao.searchArticles(
                query = "Bangkok",
                matchQuery = "bangkok*"
            ).first()
            assertTrue(oldResults.isEmpty())
            assertEquals(1, updatedResults.size)

            migratedArticleDao.deleteByFeedId(1L)
            migratedArticleDao.deleteStaleSearchIndexEntries()

            val deletedResults = migratedArticleDao.searchArticles(
                query = "Bangkok",
                matchQuery = "bangkok*"
            ).first()
            assertTrue(deletedResults.isEmpty())
            assertEquals(0, migratedArticleDao.countSearchIndexRows())
        } finally {
            migratedDatabase.close()
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun version7To8MigrationRepeatedManualSyncPreservesSearchState() = runBlocking {
        database.close()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "fts-migration-7-to-8-repeat-${System.nanoTime()}.db"
        val databasePath = context.getDatabasePath(databaseName)
        databasePath.parentFile?.mkdirs()
        if (databasePath.exists()) {
            databasePath.delete()
        }

        createVersion7DatabaseWithLegacyFtsTriggers(databasePath.absolutePath)

        val migratedDatabase = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
            .allowMainThreadQueries()
            .addMigrations(AppDatabase.MIGRATION_7_8)
            .build()

        try {
            val migratedArticleDao = migratedDatabase.articleDao()

            migratedArticleDao.syncSearchIndexByFeed(1L)
            migratedArticleDao.syncSearchIndexByFeed(1L)

            val results = migratedArticleDao.searchArticles(
                query = "Thailand",
                matchQuery = "thailand*"
            ).first()

            assertEquals(1, results.size)
            assertEquals(1, migratedArticleDao.countSearchIndexRows())
            assertEquals(0, migratedArticleDao.countFtsMaintenanceTriggers())
        } finally {
            migratedDatabase.close()
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun version7To8MigrationLeavesLegacyTriggerCleanupFallbackAsNoOp() = runBlocking {
        database.close()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "fts-migration-7-to-8-noop-${System.nanoTime()}.db"
        val databasePath = context.getDatabasePath(databaseName)
        databasePath.parentFile?.mkdirs()
        if (databasePath.exists()) {
            databasePath.delete()
        }

        createVersion7DatabaseWithLegacyFtsTriggers(databasePath.absolutePath)

        val migratedDatabase = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
            .allowMainThreadQueries()
            .addMigrations(AppDatabase.MIGRATION_7_8)
            .build()

        try {
            val migratedArticleDao = migratedDatabase.articleDao()

            assertEquals(0, migratedArticleDao.countFtsMaintenanceTriggers())
            assertEquals(0, disableLegacyFtsMaintenanceTriggers(migratedDatabase))
            assertEquals(0, migratedArticleDao.countFtsMaintenanceTriggers())
        } finally {
            migratedDatabase.close()
            context.deleteDatabase(databaseName)
        }
    }

    private fun createVersion6Database(path: String) {
        SQLiteDatabase.openOrCreateDatabase(path, null).use { sqliteDb ->
            sqliteDb.execSQL(
                "CREATE TABLE IF NOT EXISTS feeds (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `customTitle` TEXT, `url` TEXT NOT NULL, `siteUrl` TEXT, `iconUrl` TEXT, `displayOrder` INTEGER NOT NULL, `lastFetchedAt` INTEGER, `wifiOnly` INTEGER NOT NULL, `lastOpenedAt` INTEGER)"
            )
            sqliteDb.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_feeds_url ON feeds (url)"
            )
            sqliteDb.execSQL(
                "CREATE TABLE IF NOT EXISTS articles (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `feedId` INTEGER NOT NULL, `uniqueKey` TEXT NOT NULL, `title` TEXT NOT NULL, `link` TEXT NOT NULL, `publishedAt` INTEGER, `plainText` TEXT NOT NULL, `contentHtml` TEXT NOT NULL, `imageUrls` TEXT NOT NULL, `isRead` INTEGER NOT NULL, `isFavorite` INTEGER NOT NULL, FOREIGN KEY(`feedId`) REFERENCES `feeds`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
            )
            sqliteDb.execSQL(
                "CREATE INDEX IF NOT EXISTS index_articles_feedId ON articles (feedId)"
            )
            sqliteDb.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_articles_feedId_uniqueKey ON articles (feedId, uniqueKey)"
            )
            sqliteDb.execSQL(
                "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)"
            )
            sqliteDb.execSQL(
                "INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES(42, '360599cc84597be3144e61e08a6d10ef')"
            )
            sqliteDb.execSQL(
                "INSERT INTO feeds (id, title, customTitle, url, siteUrl, iconUrl, displayOrder, lastFetchedAt, wifiOnly, lastOpenedAt) VALUES (1, 'Test Feed', NULL, 'https://example.com/feed.xml', NULL, NULL, 1, NULL, 0, NULL)"
            )
            sqliteDb.execSQL(
                "INSERT INTO articles (id, feedId, uniqueKey, title, link, publishedAt, plainText, contentHtml, imageUrls, isRead, isFavorite) VALUES (1, 1, 'a-1', 'Asien Reise', 'https://example.com/articles/a-1', 1700000000000, 'Thailand Tipps', '<p>Thailand Tipps</p>', '', 0, 0)"
            )
            sqliteDb.version = 6
        }
    }

    private fun createVersion7DatabaseWithLegacyFtsTriggers(path: String) {
        SQLiteDatabase.openOrCreateDatabase(path, null).use { sqliteDb ->
            sqliteDb.execSQL(
                "CREATE TABLE IF NOT EXISTS feeds (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `customTitle` TEXT, `url` TEXT NOT NULL, `siteUrl` TEXT, `iconUrl` TEXT, `displayOrder` INTEGER NOT NULL, `lastFetchedAt` INTEGER, `wifiOnly` INTEGER NOT NULL, `lastOpenedAt` INTEGER)"
            )
            sqliteDb.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_feeds_url ON feeds (url)"
            )
            sqliteDb.execSQL(
                "CREATE TABLE IF NOT EXISTS articles (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `feedId` INTEGER NOT NULL, `uniqueKey` TEXT NOT NULL, `title` TEXT NOT NULL, `link` TEXT NOT NULL, `publishedAt` INTEGER, `plainText` TEXT NOT NULL, `contentHtml` TEXT NOT NULL, `imageUrls` TEXT NOT NULL, `isRead` INTEGER NOT NULL, `isFavorite` INTEGER NOT NULL, FOREIGN KEY(`feedId`) REFERENCES `feeds`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
            )
            sqliteDb.execSQL(
                "CREATE INDEX IF NOT EXISTS index_articles_feedId ON articles (feedId)"
            )
            sqliteDb.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_articles_feedId_uniqueKey ON articles (feedId, uniqueKey)"
            )
            sqliteDb.execSQL(
                """
                CREATE VIRTUAL TABLE IF NOT EXISTS `articles_fts`
                USING fts4(`title`, `plainText`)
                """.trimIndent()
            )
            sqliteDb.execSQL(
                "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)"
            )
            sqliteDb.execSQL(
                "INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES(42, '6fc7b8e6477ca67067a1f6c4d59d76a8')"
            )
            sqliteDb.execSQL(
                "INSERT INTO feeds (id, title, customTitle, url, siteUrl, iconUrl, displayOrder, lastFetchedAt, wifiOnly, lastOpenedAt) VALUES (1, 'Test Feed', NULL, 'https://example.com/feed.xml', NULL, NULL, 1, NULL, 0, NULL)"
            )
            sqliteDb.execSQL(
                "INSERT INTO articles (id, feedId, uniqueKey, title, link, publishedAt, plainText, contentHtml, imageUrls, isRead, isFavorite) VALUES (1, 1, 'a-1', 'Asien Reise', 'https://example.com/articles/a-1', 1700000000000, 'Thailand Tipps', '<p>Thailand Tipps</p>', '', 0, 0)"
            )
            sqliteDb.execSQL(
                "INSERT INTO articles_fts(rowid, title, plainText) VALUES (1, 'Asien Reise', 'Thailand Tipps')"
            )
            sqliteDb.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS articles_fts_ai AFTER INSERT ON articles BEGIN
                    INSERT INTO articles_fts(rowid, title, plainText)
                    VALUES (new.id, new.title, new.plainText);
                END
                """.trimIndent()
            )
            sqliteDb.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS articles_fts_ad AFTER DELETE ON articles BEGIN
                    INSERT INTO articles_fts(articles_fts, rowid, title, plainText)
                    VALUES('delete', old.id, old.title, old.plainText);
                END
                """.trimIndent()
            )
            sqliteDb.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS articles_fts_au AFTER UPDATE ON articles BEGIN
                    INSERT INTO articles_fts(articles_fts, rowid, title, plainText)
                    VALUES('delete', old.id, old.title, old.plainText);
                    INSERT INTO articles_fts(rowid, title, plainText)
                    VALUES (new.id, new.title, new.plainText);
                END
                """.trimIndent()
            )
            sqliteDb.version = 7
        }
    }

    private suspend fun insertFeed(): Long {
        return feedDao.insert(
            FeedEntity(
                title = "Test Feed",
                url = "https://example.com/feed.xml",
                displayOrder = 1
            )
        )
    }

    private fun article(
        feedId: Long,
        uniqueKey: String,
        title: String,
        plainText: String
    ): ArticleEntity {
        return ArticleEntity(
            feedId = feedId,
            uniqueKey = uniqueKey,
            title = title,
            link = "https://example.com/articles/$uniqueKey",
            publishedAt = 1_700_000_000_000,
            plainText = plainText,
            contentHtml = "<p>$plainText</p>",
            imageUrls = ""
        )
    }
}
