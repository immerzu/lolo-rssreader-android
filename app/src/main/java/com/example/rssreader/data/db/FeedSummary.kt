package com.example.rssreader.data.db

data class FeedSummary(
    val id: Long,
    val title: String,
    val customTitle: String?,
    val url: String,
    val siteUrl: String?,
    val iconUrl: String?,
    val displayOrder: Int,
    val lastFetchedAt: Long?,
    val wifiOnly: Boolean,
    val lastOpenedAt: Long?,
    val totalArticles: Long,
    val unreadArticles: Long
) {
    val displayTitle: String
        get() = customTitle?.takeIf { it.isNotBlank() } ?: title

    val isUnreadFeed: Boolean
        get() = unreadArticles > 0
}



