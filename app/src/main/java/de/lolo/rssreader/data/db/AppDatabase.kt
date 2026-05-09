package de.lolo.rssreader.data.db

import androidx.room.Database
import androidx.room.migration.Migration
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [FeedEntity::class, ArticleEntity::class, ArticleFtsEntity::class],
    version = 10,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun feedDao(): FeedDao
    abstract fun articleDao(): ArticleDao

    companion object {
        private val legacyFtsTriggerNames = listOf(
            "articles_fts_ai",
            "articles_fts_au",
            "articles_fts_ad"
        )

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE feeds ADD COLUMN lastOpenedAt INTEGER")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE feeds ADD COLUMN siteUrl TEXT")
                db.execSQL("ALTER TABLE feeds ADD COLUMN iconUrl TEXT")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE feeds ADD COLUMN displayOrder INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE feeds SET displayOrder = id WHERE displayOrder = 0")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE articles ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE articles ADD COLUMN contentHtml TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Historical v7 introduced FTS plus trigger-based maintenance.
                // MIGRATION_7_8 removes that trigger legacy again, leaving only manual sync.
                db.execSQL(
                    """
                    CREATE VIRTUAL TABLE IF NOT EXISTS `articles_fts`
                    USING fts4(`title`, `plainText`)
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO articles_fts(rowid, title, plainText)
                    SELECT id, title, plainText FROM articles
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TRIGGER IF NOT EXISTS articles_fts_ai AFTER INSERT ON articles BEGIN
                        INSERT INTO articles_fts(rowid, title, plainText)
                        VALUES (new.id, new.title, new.plainText);
                    END
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TRIGGER IF NOT EXISTS articles_fts_ad AFTER DELETE ON articles BEGIN
                        INSERT INTO articles_fts(articles_fts, rowid, title, plainText)
                        VALUES('delete', old.id, old.title, old.plainText);
                    END
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TRIGGER IF NOT EXISTS articles_fts_au AFTER UPDATE ON articles BEGIN
                        INSERT INTO articles_fts(articles_fts, rowid, title, plainText)
                        VALUES('delete', old.id, old.title, old.plainText);
                        INSERT INTO articles_fts(rowid, title, plainText)
                        VALUES (new.id, new.title, new.plainText);
                    END
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Manual repository-side FTS maintenance is the only supported runtime strategy.
                // This migration removes the historical trigger-based side path from old v7 DBs.
                legacyFtsTriggerNames.forEach { triggerName ->
                    db.execSQL("DROP TRIGGER IF EXISTS $triggerName")
                }
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE feeds ADD COLUMN etag TEXT")
                db.execSQL("ALTER TABLE feeds ADD COLUMN lastModified TEXT")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE feeds ADD COLUMN heavy INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}



