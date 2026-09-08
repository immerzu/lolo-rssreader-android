package de.lolo.rssreader.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import de.lolo.rssreader.R

/**
 * Baut den Teilen-Intent (ACTION_SEND) fuer einen Artikel: Titel als Betreff,
 * Titel und Link als Text. Liefert `null`, wenn weder Titel noch Link vorhanden sind.
 */
internal fun buildArticleShareIntent(title: String?, link: String?): Intent? {
    val safeTitle = title?.trim().orEmpty()
    val safeLink = link?.trim().orEmpty()
    if (safeTitle.isBlank() && safeLink.isBlank()) {
        return null
    }
    val shareText = buildString {
        if (safeTitle.isNotBlank()) {
            append(safeTitle)
        }
        if (safeTitle.isNotBlank() && safeLink.isNotBlank()) {
            append("\n\n")
        }
        if (safeLink.isNotBlank()) {
            append(safeLink)
        }
    }
    return Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        if (safeTitle.isNotBlank()) {
            putExtra(Intent.EXTRA_SUBJECT, safeTitle)
        }
        putExtra(Intent.EXTRA_TEXT, shareText)
    }
}

/**
 * Oeffnet das Android-Share-Sheet fuer einen Artikel.
 *
 * @return `true`, wenn ein Share-Sheet gestartet werden konnte.
 */
internal fun shareArticle(context: Context, title: String?, link: String?): Boolean {
    val shareIntent = buildArticleShareIntent(title, link) ?: return false
    val chooser = Intent.createChooser(shareIntent, context.getString(R.string.article_share_chooser_title))
    if (context !is Activity) {
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return runCatching {
        context.startActivity(chooser)
        true
    }.getOrDefault(false)
}
