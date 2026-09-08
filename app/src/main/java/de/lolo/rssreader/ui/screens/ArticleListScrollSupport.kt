package de.lolo.rssreader.ui.screens

import de.lolo.rssreader.data.db.ArticleEntity

/**
 * Ermittelt die Artikel, die beim Scrollen vollstaendig durchlaufen wurden und
 * daher automatisch als gelesen markiert werden sollen.
 *
 * "Durchlaufen" bedeutet: Der Artikel liegt vollstaendig oberhalb des aktuell
 * sichtbaren Bereichs, d. h. sein Listenindex ist kleiner als der Index des
 * ersten sichtbaren Artikels. Der gerade sichtbare Artikel selbst wird nicht
 * markiert, damit der Nutzer ihn noch lesen kann.
 *
 * @param articles Sichtbare Artikelliste in Anzeigereihenfolge (ohne Header).
 * @param firstVisibleArticleIndex Index des ersten sichtbaren Artikels.
 * @param alreadyMarkedIds Artikel-IDs, die in diesem Bildschirmaufruf bereits
 *   durch das Scrollen markiert wurden (verhindert wiederholte Datenbankaufrufe).
 */
internal fun articlesToMarkReadOnScroll(
    articles: List<ArticleEntity>,
    firstVisibleArticleIndex: Int,
    alreadyMarkedIds: Set<Long>
): List<ArticleEntity> {
    if (firstVisibleArticleIndex <= 0) {
        return emptyList()
    }
    val passedCount = firstVisibleArticleIndex.coerceAtMost(articles.size)
    return articles.take(passedCount).filter { article ->
        !article.isRead && article.id !in alreadyMarkedIds
    }
}

/**
 * Rechnet den LazyColumn-Index (inklusive Kopfzeile) in den Index des ersten
 * sichtbaren Artikels um. `hasHeaderItem` beschreibt die vorhandene Kopfzeile
 * mit der Anzahl ungelesener Artikel.
 */
internal fun firstVisibleArticleIndexFor(listIndex: Int, hasHeaderItem: Boolean): Int {
    val headerOffset = if (hasHeaderItem) 1 else 0
    return (listIndex - headerOffset).coerceAtLeast(0)
}
