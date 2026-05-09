package de.lolo.rssreader.data.db

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

@Entity(
    tableName = "feeds",
    indices = [Index(value = ["url"], unique = true)]
)
data class FeedEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val customTitle: String? = null,
    val url: String,
    val siteUrl: String? = null,
    val iconUrl: String? = null,
    val displayOrder: Int = 0,
    val lastFetchedAt: Long? = null,
    val wifiOnly: Boolean = false,
    val lastOpenedAt: Long? = null,
    val etag: String? = null,
    val lastModified: String? = null,
    val heavy: Boolean = false
)

@Entity(
    tableName = "articles",
    foreignKeys = [
        ForeignKey(
            entity = FeedEntity::class,
            parentColumns = ["id"],
            childColumns = ["feedId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("feedId"),
        Index(value = ["feedId", "uniqueKey"], unique = true)
    ]
)
data class ArticleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val feedId: Long,
    val uniqueKey: String,
    val title: String,
    val link: String,
    val publishedAt: Long? = null,
    val plainText: String,
    val contentHtml: String,
    val imageUrls: String,
    val isRead: Boolean = false,
    val isFavorite: Boolean = false
)

@Fts4
@Entity(tableName = "articles_fts")
data class ArticleFtsEntity(
    @PrimaryKey
    @ColumnInfo(name = "rowid")
    val rowId: Long,
    val title: String,
    val plainText: String
)



