package com.example.rssreader.data.network

import android.util.Log
import android.util.Xml
import androidx.core.text.HtmlCompat
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.net.URI
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

class FeedParser {

    companion object {
        private const val TAG = "FeedParser"
        private val imageSrcRegex = Regex("<img[^>]+src=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
        private val nbspRegex = Regex("&nbsp;")
        private val whitespaceRegex = Regex("\\s+")
        private val weiterlesenRegex = Regex(
            "\\s*Weiterlesen\\s*[\\u2192\\u00BB\\u203A>]*\\s*$",
            RegexOption.IGNORE_CASE
        )
    }

    private val rssFormatters = listOf(
        DateTimeFormatter.RFC_1123_DATE_TIME,
        DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US),
        DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm Z", Locale.US),
        DateTimeFormatter.ISO_OFFSET_DATE_TIME,
        DateTimeFormatter.ISO_ZONED_DATE_TIME
    )

    fun parse(xml: String, sourceUrl: String? = null): ParsedFeed {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        parser.setInput(StringReader(xml))

        while (parser.eventType != XmlPullParser.START_TAG &&
            parser.eventType != XmlPullParser.END_DOCUMENT
        ) {
            parser.next()
        }

        return when (parser.normalizedName()) {
            "rss" -> parseRss(parser, sourceUrl)
            "feed" -> parseAtom(parser, sourceUrl)
            else -> throw IllegalArgumentException("Kein gueltiger RSS- oder Atom-Feed")
        }
    }

    private fun parseRss(parser: XmlPullParser, sourceUrl: String?): ParsedFeed {
        var feedTitle = "Unbekannter Feed"
        var siteUrl: String? = null
        var iconUrl: String? = null
        val items = mutableListOf<ParsedArticle>()
        var insideChannel = false

        while (!(parser.eventType == XmlPullParser.END_TAG && parser.normalizedName() == "rss")) {
            if (parser.eventType == XmlPullParser.START_TAG) {
                when {
                    parser.matchesTag("channel") -> insideChannel = true
                    parser.matchesTag("title") && parser.isNamespaceEmpty() &&
                        insideChannel && feedTitle == "Unbekannter Feed" -> {
                        feedTitle = parser.nextText().trim().ifBlank { "Unbekannter Feed" }
                    }

                    parser.matchesTag("link") && parser.isNamespaceEmpty() &&
                        insideChannel && siteUrl.isNullOrBlank() -> {
                        siteUrl = parser.nextText().trim().ifBlank { null }
                    }

                    parser.matchesTag("image") && parser.isNamespaceEmpty() &&
                        insideChannel && iconUrl.isNullOrBlank() -> {
                        iconUrl = parseRssImage(parser, siteUrl ?: sourceUrl)
                    }

                    parser.matchesTag("itunes:image", "image") &&
                        parser.normalizedNamespace() == "http://www.itunes.com/dtds/podcast-1.0.dtd" &&
                        insideChannel && iconUrl.isNullOrBlank() -> {
                        iconUrl = resolveUrl(
                            baseUrl = siteUrl ?: sourceUrl,
                            value = parser.getAttributeValue(null, "href")
                        )
                    }

                    parser.matchesTag("item") && parser.isNamespaceEmpty() -> {
                        items += parseRssItem(parser)
                    }
                }
            } else if (parser.eventType == XmlPullParser.END_TAG && parser.matchesTag("channel")) {
                insideChannel = false
            }
            parser.next()
        }

        val resolvedSiteUrl = resolveUrl(sourceUrl, siteUrl)
        val resolvedIconUrl = resolveUrl(resolvedSiteUrl ?: sourceUrl, iconUrl)
        return ParsedFeed(feedTitle, resolvedSiteUrl, resolvedIconUrl, items)
    }

    private fun parseRssItem(parser: XmlPullParser): ParsedArticle {
        val itemDepth = parser.depth
        var guid = ""
        var title: String? = null
        var link = ""
        var author: String? = null
        var descriptionHtml: String? = null
        var contentEncodedHtml: String? = null
        var publishedAt: Long? = null
        var rawPublishedAt: String? = null
        var mediaTitle: String? = null
        val images = mutableListOf<String>()

        while (!(parser.eventType == XmlPullParser.END_TAG &&
            parser.depth == itemDepth &&
            parser.matchesTag("item"))
        ) {
            if (parser.eventType == XmlPullParser.START_TAG) {
                val isDirectChild = parser.depth == itemDepth + 1
                when {
                    isDirectChild && parser.matchesTag("guid") && parser.isNamespaceEmpty() -> {
                        guid = parser.nextText().trim()
                    }

                    isDirectChild && parser.matchesTag("title") && parser.isNamespaceEmpty() -> {
                        title = parser.nextText().trim().ifBlank { null }
                    }

                    isDirectChild && parser.matchesTag("link") && parser.isNamespaceEmpty() -> {
                        link = parser.nextText().trim()
                    }

                    isDirectChild && parser.matchesTag("description") &&
                        parser.isNamespaceEmpty() -> {
                        descriptionHtml = parser.nextText()
                    }

                    isDirectChild && parser.matchesTag("content:encoded", "encoded") -> {
                        contentEncodedHtml = parser.nextText()
                    }

                    isDirectChild && parser.matchesTag("dc:creator", "creator") -> {
                        author = parser.nextText().trim().ifBlank { null }
                    }

                    isDirectChild && parser.matchesTag("author") && parser.isNamespaceEmpty() -> {
                        author = parser.nextText().trim().ifBlank { null }
                    }

                    isDirectChild && parser.matchesTag("pubDate") && parser.isNamespaceEmpty() -> {
                        rawPublishedAt = parser.nextText()
                        publishedAt = parseDate(rawPublishedAt.orEmpty())
                    }

                    isDirectChild && parser.matchesTag("dc:date", "date", "published", "updated") -> {
                        rawPublishedAt = parser.nextText()
                        publishedAt = parseDate(rawPublishedAt.orEmpty())
                    }

                    isDirectChild && parser.isMediaImageTag() -> {
                        parser.getAttributeValue(null, "url")
                            ?.takeIf { it.isNotBlank() }
                            ?.takeUnless(::isLikelyAuthorAvatarUrl)
                            ?.let(images::add)
                    }

                    parser.matchesTag("media:title", "title") &&
                        parser.normalizedNamespace() == "http://search.yahoo.com/mrss/" -> {
                        mediaTitle = parser.nextText().trim().ifBlank { null }
                    }
                }
            }
            parser.next()
        }

        val contentSource = when {
            !contentEncodedHtml.isNullOrBlank() -> ParsedContentSource.CONTENT_ENCODED
            !descriptionHtml.isNullOrBlank() -> ParsedContentSource.DESCRIPTION
            else -> ParsedContentSource.NONE
        }
        val chosenContent = contentEncodedHtml?.takeIf { it.isNotBlank() }
            ?: descriptionHtml.orEmpty()
        val plainText = sanitizePlainText(
            htmlToPlainText(chosenContent),
            contentSource = contentSource
        )
        val htmlImages = extractImageUrls(chosenContent)
        val resolvedTitle = title?.ifBlank { null } ?: "Ohne Titel"

        val article = ParsedArticle(
            uniqueKey = guid.ifBlank { link.ifBlank { resolvedTitle } },
            title = resolvedTitle,
            link = link,
            publishedAt = publishedAt,
            plainText = plainText,
            contentHtml = chosenContent.trim(),
            imageUrls = (images + htmlImages).distinct(),
            author = author,
            contentSource = contentSource
        )

        logParsedItem(
            feedType = "rss",
            article = article,
            rawTitle = title,
            rawAuthor = author,
            rawPublishedAt = rawPublishedAt,
            rawDescription = descriptionHtml,
            rawContentEncoded = contentEncodedHtml,
            mediaTitle = mediaTitle
        )
        return article
    }

    private fun parseAtom(parser: XmlPullParser, sourceUrl: String?): ParsedFeed {
        var feedTitle = "Unbekannter Feed"
        var siteUrl: String? = null
        var iconUrl: String? = null
        val items = mutableListOf<ParsedArticle>()

        while (!(parser.eventType == XmlPullParser.END_TAG && parser.normalizedName() == "feed")) {
            if (parser.eventType == XmlPullParser.START_TAG) {
                when (parser.normalizedName()) {
                    "title" -> if (feedTitle == "Unbekannter Feed") {
                        feedTitle = parser.nextText().trim().ifBlank { "Unbekannter Feed" }
                    }

                    "icon", "logo" -> if (iconUrl.isNullOrBlank()) {
                        iconUrl = parser.nextText().trim().ifBlank { null }
                    }

                    "link" -> {
                        val href = parser.getAttributeValue(null, "href")?.trim().orEmpty()
                        val rel = parser.getAttributeValue(null, "rel")?.trim().orEmpty()
                        when {
                            href.isBlank() -> Unit
                            rel.equals("icon", ignoreCase = true) && iconUrl.isNullOrBlank() -> {
                                iconUrl = href
                            }

                            (rel.isBlank() || rel.equals("alternate", ignoreCase = true)) &&
                                siteUrl.isNullOrBlank() -> {
                                siteUrl = href
                            }
                        }
                    }

                    "entry" -> items += parseAtomEntry(parser)
                }
            }
            parser.next()
        }

        val resolvedSiteUrl = resolveUrl(sourceUrl, siteUrl)
        val resolvedIconUrl = resolveUrl(resolvedSiteUrl ?: sourceUrl, iconUrl)
        return ParsedFeed(feedTitle, resolvedSiteUrl, resolvedIconUrl, items)
    }

    private fun parseRssImage(parser: XmlPullParser, baseUrl: String?): String? {
        var imageUrl: String? = null
        val imageDepth = parser.depth

        while (!(parser.eventType == XmlPullParser.END_TAG &&
            parser.depth == imageDepth &&
            parser.matchesTag("image"))
        ) {
            if (parser.eventType == XmlPullParser.START_TAG &&
                parser.depth == imageDepth + 1 &&
                parser.matchesTag("url")
            ) {
                imageUrl = resolveUrl(baseUrl, parser.nextText())
            }
            parser.next()
        }

        return imageUrl
    }

    private fun parseAtomEntry(parser: XmlPullParser): ParsedArticle {
        val entryDepth = parser.depth
        var id = ""
        var title: String? = null
        var link = ""
        var author: String? = null
        var summaryHtml: String? = null
        var contentHtml: String? = null
        var publishedAt: Long? = null
        var rawPublishedAt: String? = null

        while (!(parser.eventType == XmlPullParser.END_TAG &&
            parser.depth == entryDepth &&
            parser.matchesTag("entry"))
        ) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.depth == entryDepth + 1) {
                when (parser.normalizedName()) {
                    "id" -> id = parser.nextText().trim()
                    "title" -> title = parser.nextText().trim().ifBlank { null }
                    "summary" -> summaryHtml = parser.nextText()
                    "content" -> contentHtml = parser.nextText()
                    "published", "updated" -> {
                        rawPublishedAt = parser.nextText()
                        publishedAt = parseDate(rawPublishedAt.orEmpty())
                    }

                    "author" -> author = parseAtomAuthor(parser)
                    "link" -> {
                        val href = parser.getAttributeValue(null, "href")
                        if (!href.isNullOrBlank()) {
                            link = href
                        }
                    }
                }
            }
            parser.next()
        }

        val contentSource = when {
            !contentHtml.isNullOrBlank() -> ParsedContentSource.CONTENT
            !summaryHtml.isNullOrBlank() -> ParsedContentSource.SUMMARY
            else -> ParsedContentSource.NONE
        }
        val chosenContent = contentHtml?.takeIf { it.isNotBlank() } ?: summaryHtml.orEmpty()
        val plainText = sanitizePlainText(
            htmlToPlainText(chosenContent),
            contentSource = contentSource
        )
        val images = extractImageUrls(chosenContent)
        val resolvedTitle = title?.ifBlank { null } ?: "Ohne Titel"

        val article = ParsedArticle(
            uniqueKey = id.ifBlank { link.ifBlank { resolvedTitle } },
            title = resolvedTitle,
            link = link,
            publishedAt = publishedAt,
            plainText = plainText,
            contentHtml = chosenContent.trim(),
            imageUrls = images.distinct(),
            author = author,
            contentSource = contentSource
        )

        logParsedItem(
            feedType = "atom",
            article = article,
            rawTitle = title,
            rawAuthor = author,
            rawPublishedAt = rawPublishedAt,
            rawDescription = summaryHtml,
            rawContentEncoded = contentHtml,
            mediaTitle = null
        )
        return article
    }

    private fun parseAtomAuthor(parser: XmlPullParser): String? {
        val authorDepth = parser.depth
        var authorName: String? = null

        while (!(parser.eventType == XmlPullParser.END_TAG &&
            parser.depth == authorDepth &&
            parser.matchesTag("author"))
        ) {
            if (parser.eventType == XmlPullParser.START_TAG &&
                parser.depth == authorDepth + 1 &&
                parser.matchesTag("name")
            ) {
                authorName = parser.nextText().trim().ifBlank { null }
            }
            parser.next()
        }

        return authorName
    }

    private fun parseDate(rawValue: String): Long? {
        val value = rawValue.trim()
        if (value.isBlank()) {
            return null
        }

        rssFormatters.forEach { formatter ->
            runCatching {
                ZonedDateTime.parse(value, formatter).toInstant().toEpochMilli()
            }.getOrNull()?.let { return it }

            runCatching {
                OffsetDateTime.parse(value, formatter).toInstant().toEpochMilli()
            }.getOrNull()?.let { return it }
        }

        return parseIsoDate(value)
    }

    private fun parseIsoDate(value: String): Long? {
        return try {
            Instant.parse(value).toEpochMilli()
        } catch (_: DateTimeParseException) {
            try {
                OffsetDateTime.parse(value).toInstant().toEpochMilli()
            } catch (_: DateTimeParseException) {
                try {
                    LocalDateTime.parse(value).toInstant(ZoneOffset.UTC).toEpochMilli()
                } catch (_: DateTimeParseException) {
                    null
                }
            }
        }
    }

    private fun resolveUrl(baseUrl: String?, value: String?): String? {
        val candidate = value?.trim().orEmpty()
        if (candidate.isBlank()) {
            return null
        }
        return runCatching {
            val uri = URI(candidate)
            if (uri.isAbsolute) {
                uri.toString()
            } else {
                val base = baseUrl?.let(::URI) ?: return candidate
                base.resolve(uri).toString()
            }
        }.getOrElse { candidate }
    }

    private fun extractImageUrls(rawHtml: String): List<String> {
        return imageSrcRegex
            .findAll(rawHtml)
            .map { it.groupValues[1] }
            .filterNot(::isLikelyAuthorAvatarUrl)
            .toList()
    }

    private fun isLikelyAuthorAvatarUrl(url: String): Boolean {
        val normalized = url.lowercase(Locale.US)
        return "gravatar.com/avatar/" in normalized || ".gravatar.com/avatar/" in normalized
    }

    private fun htmlToPlainText(raw: String): String {
        var normalized = raw
        repeat(2) {
            normalized = HtmlCompat.fromHtml(
                normalized,
                HtmlCompat.FROM_HTML_MODE_LEGACY
            ).toString()
        }
        return normalized
            .replace(nbspRegex, " ")
            .replace(whitespaceRegex, " ")
            .trim()
    }

    private fun sanitizePlainText(text: String, contentSource: ParsedContentSource): String {
        if (text.isBlank()) {
            return text
        }
        return when (contentSource) {
            ParsedContentSource.DESCRIPTION,
            ParsedContentSource.SUMMARY -> {
                text.replace(weiterlesenRegex, "").trim()
            }

            else -> text
        }
    }

    private fun logParsedItem(
        feedType: String,
        article: ParsedArticle,
        rawTitle: String?,
        rawAuthor: String?,
        rawPublishedAt: String?,
        rawDescription: String?,
        rawContentEncoded: String?,
        mediaTitle: String?
    ) {
        if (!Log.isLoggable(TAG, Log.DEBUG)) {
            return
        }
        Log.d(
            TAG,
            buildString {
                append("Parsed ")
                append(feedType)
                append(" item: titleField=")
                append(quoted(preview(rawTitle)))
                append(", authorField=")
                append(quoted(preview(rawAuthor)))
                append(", mediaTitle=")
                append(quoted(preview(mediaTitle)))
                append(", resolvedTitle=")
                append(quoted(preview(article.title)))
                append(", link=")
                append(quoted(preview(article.link)))
                append(", pubDate=")
                append(quoted(preview(rawPublishedAt)))
                append(", contentSource=")
                append(article.contentSource)
                append(", hasDescription=")
                append(!rawDescription.isNullOrBlank())
                append(", hasContentEncoded=")
                append(!rawContentEncoded.isNullOrBlank())
                append(", plainText=")
                append(quoted(preview(article.plainText)))
                append(", images=")
                append(article.imageUrls.size)
            }
        )
    }

    private fun preview(value: String?, maxLength: Int = 96): String? {
        val normalized = value
            ?.replace(whitespaceRegex, " ")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return null
        return if (normalized.length <= maxLength) {
            normalized
        } else {
            normalized.take(maxLength - 3) + "..."
        }
    }

    private fun quoted(value: String?): String = value?.let { "\"$it\"" } ?: "null"

    private fun XmlPullParser.matchesTag(vararg candidates: String): Boolean {
        val tagName = name ?: return false
        val normalized = normalizedName()
        val prefixed = prefix?.takeIf { it.isNotBlank() }?.let { "$it:$normalized" }
        return candidates.any { candidate ->
            candidate.equals(tagName, ignoreCase = true) ||
                candidate.equals(normalized, ignoreCase = true) ||
                prefixed?.equals(candidate, ignoreCase = true) == true
        }
    }

    private fun XmlPullParser.normalizedName(): String = (name ?: "").substringAfter(':')

    private fun XmlPullParser.normalizedNamespace(): String? = namespace?.takeIf { it.isNotBlank() }

    private fun XmlPullParser.isNamespaceEmpty(): Boolean = normalizedNamespace().isNullOrBlank()

    private fun XmlPullParser.isMediaImageTag(): Boolean {
        val normalized = normalizedName()
        if (normalizedNamespace() == "http://search.yahoo.com/mrss/" &&
            (normalized == "content" || normalized == "thumbnail")
        ) {
            return true
        }
        if (isNamespaceEmpty() && (normalized == "thumbnail" || normalized == "enclosure")) {
            val type = getAttributeValue(null, "type").orEmpty()
            return type.isBlank() || type.startsWith("image", ignoreCase = true)
        }
        return false
    }
}


========================================================================================================================