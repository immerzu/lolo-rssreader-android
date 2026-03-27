package com.example.rssreader.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class ArticleSearchResult(
    val articleId: Long,
    val feedId: Long,
    val feedTitle: String,
    val feedCustomTitle: String?,
    val articleTitle: String,
    val plainText: String,
    val publishedAt: Long?,
    val isRead: Boolean,
    val isFavorite: Boolean
)

data class ArticleNavigationEntry(
    val id: Long,
    val publishedAt: Long?
)

@Dao
interface ArticleDao {
    @Query(
        """
        SELECT * FROM articles
        WHERE feedId = :feedId
        ORDER BY isRead ASC, COALESCE(publishedAt, 0) DESC, id DESC
        """
    )
    fun observeByFeed(feedId: Long): Flow<List<ArticleEntity>>

    @Query(
        """
        SELECT id, publishedAt
        FROM articles
        WHERE feedId = :feedId
        ORDER BY COALESCE(publishedAt, 0) DESC, id DESC
        """
    )
    fun observeNavigationByFeed(feedId: Long): Flow<List<ArticleNavigationEntry>>

    @Query("SELECT * FROM articles WHERE id = :articleId LIMIT 1")
    suspend fun getById(articleId: Long): ArticleEntity?

    @Query("SELECT COUNT(*) FROM articles")
    suspend fun countArticles(): Int

    @Query("SELECT * FROM articles WHERE feedId = :feedId AND uniqueKey IN (:uniqueKeys)")
    suspend fun getByFeedAndUniqueKeys(feedId: Long, uniqueKeys: List<String>): List<ArticleEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<ArticleEntity>): List<Long>

    @Query(
        """
        UPDATE articles
        SET
            title = :title,
            link = :link,
            publishedAt = :publishedAt,
            plainText = :plainText,
            contentHtml = :contentHtml,
            imageUrls = :imageUrls
        WHERE feedId = :feedId AND uniqueKey = :uniqueKey
        """
    )
    suspend fun updateByUniqueKey(
        feedId: Long,
        uniqueKey: String,
        title: String,
        link: String,
        publishedAt: Long?,
        plainText: String,
        contentHtml: String,
        imageUrls: String
    )

    @Query("UPDATE articles SET isRead = 1 WHERE id = :articleId")
    suspend fun markRead(articleId: Long)

    @Query("UPDATE articles SET isRead = 0 WHERE id = :articleId")
    suspend fun markUnread(articleId: Long)

    @Query("UPDATE articles SET isFavorite = :isFavorite WHERE id = :articleId")
    suspend fun setFavorite(articleId: Long, isFavorite: Boolean)

    @Query("UPDATE articles SET isRead = 1 WHERE feedId = :feedId")
    suspend fun markAllRead(feedId: Long): Int

    @Query("UPDATE articles SET isRead = 0 WHERE feedId = :feedId")
    suspend fun markAllUnread(feedId: Long): Int

    @Query("UPDATE articles SET isRead = 1")
    suspend fun markAllReadGlobally(): Int

    @Query("UPDATE articles SET isRead = 0")
    suspend fun markAllUnreadGlobally(): Int

    @Query("DELETE FROM articles WHERE isRead = 1 AND isFavorite = 0")
    suspend fun deleteAllRead(): Int

    @Query("DELETE FROM articles WHERE feedId = :feedId AND isRead = 1 AND isFavorite = 0")
    suspend fun deleteReadByFeedId(feedId: Long): Int

    @Query("DELETE FROM articles WHERE isFavorite = 0")
    suspend fun deleteAllNonFavorite(): Int

    @Query("DELETE FROM articles WHERE feedId = :feedId AND isFavorite = 0")
    suspend fun deleteByFeedId(feedId: Long): Int

    @Query(
        """
        SELECT COUNT(*)
        FROM sqlite_master
        WHERE type = 'trigger'
          AND name IN ('articles_fts_ai', 'articles_fts_au', 'articles_fts_ad')
        """
    )
    suspend fun countFtsMaintenanceTriggers(): Int

    @Query(
        """
        INSERT OR REPLACE INTO articles_fts(rowid, title, plainText)
        SELECT id, title, plainText
        FROM articles
        WHERE feedId = :feedId
        """
    )
    suspend fun syncSearchIndexByFeed(feedId: Long)

    @Query(
        """
        DELETE FROM articles_fts
        WHERE rowid NOT IN (SELECT id FROM articles)
        """
    )
    suspend fun deleteStaleSearchIndexEntries()

    @Query("SELECT COUNT(*) FROM articles_fts")
    suspend fun countSearchIndexRows(): Int

    @Query(
        """
        SELECT
            articles.id AS articleId,
            articles.feedId AS feedId,
            feeds.title AS feedTitle,
            feeds.customTitle AS feedCustomTitle,
            articles.title AS articleTitle,
            articles.plainText AS plainText,
            articles.publishedAt AS publishedAt,
            articles.isRead AS isRead,
            articles.isFavorite AS isFavorite
        FROM articles
        INNER JOIN feeds ON feeds.id = articles.feedId
        WHERE
            articles.id IN (
                SELECT rowid
                FROM articles_fts
                WHERE articles_fts MATCH :matchQuery
            )
            OR feeds.title LIKE '%' || :query || '%'
            OR COALESCE(feeds.customTitle, '') LIKE '%' || :query || '%'
        ORDER BY COALESCE(articles.publishedAt, 0) DESC, articles.id DESC
        """
    )
    fun searchArticles(query: String, matchQuery: String): Flow<List<ArticleSearchResult>>

    @Query(
        """
        SELECT
            articles.id AS articleId,
            articles.feedId AS feedId,
            feeds.title AS feedTitle,
            feeds.customTitle AS feedCustomTitle,
            articles.title AS articleTitle,
            articles.plainText AS plainText,
            articles.publishedAt AS publishedAt,
            articles.isRead AS isRead,
            articles.isFavorite AS isFavorite
        FROM articles
        INNER JOIN feeds ON feeds.id = articles.feedId
        WHERE
            articles.title LIKE '%' || :query || '%'
            OR articles.plainText LIKE '%' || :query || '%'
            OR feeds.title LIKE '%' || :query || '%'
            OR COALESCE(feeds.customTitle, '') LIKE '%' || :query || '%'
        ORDER BY COALESCE(articles.publishedAt, 0) DESC, articles.id DESC
        """
    )
    fun searchArticlesFallback(query: String): Flow<List<ArticleSearchResult>>
}


