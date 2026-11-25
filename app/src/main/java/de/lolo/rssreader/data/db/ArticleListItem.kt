package de.lolo.rssreader.data.db

/**
 * Schlanke Listenprojektion fuer Artikellisten (Audit RSS-A16): enthaelt bewusst kein
 * [ArticleEntity.contentHtml] und keinen Link, weil die Liste beides nicht darstellt.
 * Der vollstaendige Artikel wird erst im Reader per ID geladen.
 */
data class ArticleListItem(
    val id: Long,
    val feedId: Long,
    val title: String,
    /** Wird fuer "Teilen" im Listendialog benoetigt; der HTML-Inhalt bleibt bewusst aussen vor. */
    val link: String,
    val publishedAt: Long?,
    val plainText: String,
    val isRead: Boolean,
    val isFavorite: Boolean
)
