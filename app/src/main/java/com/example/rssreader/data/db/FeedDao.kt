package com.example.rssreader.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FeedDao {
    @Query(
        """
        SELECT
            feeds.id AS id,
            feeds.title AS title,
            feeds.customTitle AS customTitle,
            feeds.url AS url,
            feeds.siteUrl AS siteUrl,
            feeds.iconUrl AS iconUrl,
            feeds.displayOrder AS displayOrder,
            feeds.lastFetchedAt AS lastFetchedAt,
            feeds.wifiOnly AS wifiOnly,
            feeds.lastOpenedAt AS lastOpenedAt,
            COUNT(articles.id) AS totalArticles,
            COALESCE(SUM(CASE WHEN articles.isRead = 0 THEN 1 ELSE 0 END), 0) AS unreadArticles
        FROM feeds
        LEFT JOIN articles ON articles.feedId = feeds.id
        GROUP BY feeds.id
        ORDER BY
            feeds.displayOrder ASC,
            feeds.id ASC
        """
    )
    fun observeSummaries(): Flow<List<FeedSummary>>

    @Query("SELECT * FROM feeds ORDER BY displayOrder ASC, id ASC")
    fun observeFeeds(): Flow<List<FeedEntity>>

    @Query("SELECT * FROM feeds WHERE id = :feedId LIMIT 1")
    fun observeById(feedId: Long): Flow<FeedEntity?>

    @Query("SELECT * FROM feeds WHERE id = :feedId LIMIT 1")
    suspend fun getById(feedId: Long): FeedEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM feeds WHERE url = :url)")
    suspend fun existsByUrl(url: String): Boolean

    @Query("UPDATE feeds SET lastOpenedAt = :openedAt WHERE id = :feedId")
    suspend fun markOpened(feedId: Long, openedAt: Long)

    @Query("UPDATE feeds SET lastOpenedAt = NULL")
    suspend fun resetAllOpenedStates()

    @Query("SELECT COALESCE(MAX(displayOrder), 0) FROM feeds")
    suspend fun getMaxDisplayOrder(): Int

    @Query("SELECT COUNT(*) FROM feeds")
    suspend fun countFeeds(): Int

    @Query("UPDATE feeds SET displayOrder = :displayOrder WHERE id = :feedId")
    suspend fun updateDisplayOrder(feedId: Long, displayOrder: Int)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(feed: FeedEntity): Long

    @Update
    suspend fun update(feed: FeedEntity)

    @Query("DELETE FROM feeds WHERE id = :feedId")
    suspend fun deleteById(feedId: Long)

    @Query("SELECT * FROM feeds ORDER BY displayOrder ASC, id ASC")
    suspend fun getAll(): List<FeedEntity>
}
