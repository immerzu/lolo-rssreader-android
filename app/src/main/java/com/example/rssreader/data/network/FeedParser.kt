package com.example.rssreader.data.network

import android.util.Log
import android.util.Xml
import androidx.core.text.HtmlCompat
import com.example.rssreader.data.errors.RssReaderException
import com.example.rssreader.debug.DebugLogger
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
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
        private const val LARGE_FEED_DEFENSIVE_MAX_ITEMS = 120
        private const val LARGE_FEED_DEFENSIVE_MAX_ITEM_CONTENT_CHARS = 180_000
        private const val LARGE_FEED_DEFENSIVE_MAX_DERIVED_CONTENT_CHARS = 60_000
        private const val LARGE_FEED_DEFENSIVE_SKIP_IMAGE_SCAN_CHARS = 90_000
        private const val LARGE_FEED_DEFENSIVE_FORCE_SKIP_IMAGE_SCAN_CHARS = 140_000
        private const val LARGE_FEED_DEFENSIVE_MAX_IMAGE_SCAN_CHARS = 24_000
        private const val LARGE_FEED_DEFENSIVE_MAX_REGEX_IMAGES = 24
        private const val READ_ELEMENT_CONTENT_CAPACITY = 4 * 1024
        private val imageSrcRegex = Regex("<img[^>]+src=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
        private val scriptTagRegex = Regex(
            "<script\\b[^>]*>.*?</script>",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        private val styleTagRegex = Regex(
            "<style\\b[^>]*>.*?</style>",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        private val noscriptTagRegex = Regex(
            "<noscript\\b[^>]*>.*?</noscript>",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        private val htmlCommentRegex = Regex(
            "<!--.*?-->",
            setOf(RegexOption.DOT_MATCHES_ALL)
        )
        private val nbspRegex = Regex("&nbsp;")
        private val invisibleFormattingRegex = Regex("[\\u200B-\\u200F\\u2060\\uFEFF\\u00AD\\uFFFC]")
        private val controlCharacterRegex = Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F\\u007F]")
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

    fun parse(payload: FetchedFeedPayload, sourceUrl: String? = null): ParsedFeed {
        return parseInternal(
            inputPath = "reader",
            runtime = FeedParseRuntime(
                sourceUrl = sourceUrl,
                payloadByteSize = payload.byteSize,
                defensiveMode = payload.defensiveMode
            ),
            setInput = { parser -> parser.setInput(payload.openReader()) }
        )
    }

    fun parse(xml: String, sourceUrl: String? = null): ParsedFeed {
        return parseInternal(
            inputPath = "string",
            runtime = FeedParseRuntime(
                sourceUrl = sourceUrl,
                payloadByteSize = null,
                defensiveMode = false
            ),
            setInput = { parser -> parser.setInput(StringReader(xml)) }
        )
    }

    private fun parseInternal(
        inputPath: String,
        runtime: FeedParseRuntime,
        setInput: (XmlPullParser) -> Unit
    ): ParsedFeed {
        return try {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
            setInput(parser)

            while (parser.eventType != XmlPullParser.START_TAG &&
                parser.eventType != XmlPullParser.END_DOCUMENT
            ) {
                parser.next()
            }

            val parsedFeed = when (parser.normalizedName()) {
                "rss" -> parseRss(parser, runtime)
                "feed" -> parseAtom(parser, runtime)
                else -> throw RssReaderException.InvalidXml()
            }
            DebugLogger.i(
                TAG,
                "feed_parse url=${runtime.sourceUrl.orEmpty()} bytes=${runtime.payloadByteSize ?: -1} input=$inputPath defensive=${runtime.defensiveMode} capped=${runtime.itemCapApplied} truncated=${runtime.truncatedItems > 0} imageScanSkipped=${runtime.imageScanSkipped} items=${parsedFeed.items.size}"
            )
            parsedFeed
        } catch (exception: RssReaderException) {
            throw exception
        } catch (exception: Exception) {
            throw RssReaderException.InvalidXml(exception)
        }
    }

    private fun parseRss(parser: XmlPullParser, runtime: FeedParseRuntime): ParsedFeed {
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
                        iconUrl = parseRssImage(parser, siteUrl ?: runtime.sourceUrl)
                    }

                    parser.matchesTag("itunes:image", "image") &&
                        parser.normalizedNamespace() == "http://www.itunes.com/dtds/podcast-1.0.dtd" &&
                        insideChannel && iconUrl.isNullOrBlank() -> {
                        iconUrl = resolveUrl(
                            baseUrl = siteUrl ?: runtime.sourceUrl,
                            value = parser.getAttributeValue(null, "href")
                        )
                    }

                    parser.matchesTag("item") && parser.isNamespaceEmpty() -> {
                        if (runtime.canParseMoreItems(items.size)) {
                            items += parseRssItem(parser, runtime)
                        } else {
                            runtime.markItemCap()
                            parser.skipCurrentElement()
                        }
                    }
                }
            } else if (parser.eventType == XmlPullParser.END_TAG && parser.matchesTag("channel")) {
                insideChannel = false
            }
            parser.next()
        }

        val resolvedSiteUrl = resolveUrl(runtime.sourceUrl, siteUrl)
        val resolvedIconUrl = resolveUrl(resolvedSiteUrl ?: runtime.sourceUrl, iconUrl)
        return ParsedFeed(feedTitle, resolvedSiteUrl, resolvedIconUrl, items)
    }

    private fun parseRssItem(parser: XmlPullParser, runtime: FeedParseRuntime): ParsedArticle {
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
                        descriptionHtml = parser.readElementContent(
                            maxChars = runtime.earlyContentReadCharLimit()
                        ) { limit ->
                            runtime.markEarlyContentBound(
                                tagName = "description",
                                limit = limit
                            )
                        }
                    }

                    isDirectChild && parser.matchesTag("content:encoded", "encoded") -> {
                        contentEncodedHtml = parser.readElementContent(
                            maxChars = runtime.earlyContentReadCharLimit()
                        ) { limit ->
                            runtime.markEarlyContentBound(
                                tagName = "content:encoded",
                                limit = limit
                            )
                        }
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
        val resolvedTitle = title?.ifBlank { null } ?: "Ohne Titel"
        val chosenContent = contentEncodedHtml?.takeIf { it.isNotBlank() }
            ?: descriptionHtml.orEmpty()
        val boundedContent = runtime.applyDefensiveContentLimit(
            title = resolvedTitle,
            rawContent = chosenContent
        )
        val derivedContent = runtime.prepareDerivedContent(
            title = resolvedTitle,
            rawContent = boundedContent
        )
        val previewContent = derivedContent.maybeStripPlainTextNoise()
        val plainText = sanitizePlainText(
            htmlToPlainText(previewContent),
            contentSource = contentSource
        )
        val htmlImages = runtime.extractSupplementalImageUrls(
            rawHtml = derivedContent,
            originalHtmlLength = boundedContent.length,
            existingImageCount = images.size
        )

        val article = ParsedArticle(
            uniqueKey = guid.ifBlank { link.ifBlank { resolvedTitle } },
            title = resolvedTitle,
            link = link,
            publishedAt = publishedAt,
            plainText = plainText,
            contentHtml = boundedContent.trimIfNeeded(),
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

    private fun parseAtom(parser: XmlPullParser, runtime: FeedParseRuntime): ParsedFeed {
        val feedDepth = parser.depth
        var feedTitle = "Unbekannter Feed"
        var siteUrl: String? = null
        var iconUrl: String? = null
        val items = mutableListOf<ParsedArticle>()

        while (!(parser.eventType == XmlPullParser.END_TAG && parser.normalizedName() == "feed")) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.depth == feedDepth + 1) {
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

                    "entry" -> {
                        if (runtime.canParseMoreItems(items.size)) {
                            items += parseAtomEntry(parser, runtime)
                        } else {
                            runtime.markItemCap()
                            parser.skipCurrentElement()
                        }
                    }
                }
            }
            parser.next()
        }

        val resolvedSiteUrl = resolveUrl(runtime.sourceUrl, siteUrl)
        val resolvedIconUrl = resolveUrl(resolvedSiteUrl ?: runtime.sourceUrl, iconUrl)
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

    private fun parseAtomEntry(parser: XmlPullParser, runtime: FeedParseRuntime): ParsedArticle {
        val entryDepth = parser.depth
        var id = ""
        var title: String? = null
        var preferredLink: String? = null
        var fallbackLink: String? = null
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
                    "summary" -> summaryHtml = parser.readElementContent(
                        maxChars = runtime.earlyContentReadCharLimit()
                    ) { limit ->
                        runtime.markEarlyContentBound(
                            tagName = "summary",
                            limit = limit
                        )
                    }
                    "content" -> contentHtml = parser.readElementContent(
                        maxChars = runtime.earlyContentReadCharLimit()
                    ) { limit ->
                        runtime.markEarlyContentBound(
                            tagName = "content",
                            limit = limit
                        )
                    }
                    "published", "updated" -> {
                        rawPublishedAt = parser.nextText()
                        publishedAt = parseDate(rawPublishedAt.orEmpty())
                    }

                    "author" -> author = parseAtomAuthor(parser)
                    "link" -> {
                        val href = parser.getAttributeValue(null, "href")?.trim().orEmpty()
                        val rel = parser.getAttributeValue(null, "rel")?.trim().orEmpty()
                        if (href.isNotBlank()) {
                            when {
                                rel.isBlank() || rel.equals("alternate", ignoreCase = true) -> {
                                    if (preferredLink.isNullOrBlank()) {
                                        preferredLink = href
                                    }
                                }

                                !rel.isNonArticleLinkRel() && fallbackLink.isNullOrBlank() -> {
                                    fallbackLink = href
                                }
                            }
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
        val resolvedTitle = title?.ifBlank { null } ?: "Ohne Titel"
        val link = preferredLink ?: fallbackLink.orEmpty()
        val chosenContent = contentHtml?.takeIf { it.isNotBlank() } ?: summaryHtml.orEmpty()
        val boundedContent = runtime.applyDefensiveContentLimit(
            title = resolvedTitle,
            rawContent = chosenContent
        )
        val derivedContent = runtime.prepareDerivedContent(
            title = resolvedTitle,
            rawContent = boundedContent
        )
        val previewContent = derivedContent.maybeStripPlainTextNoise()
        val plainText = sanitizePlainText(
            htmlToPlainText(previewContent),
            contentSource = contentSource
        )
        val images = runtime.extractSupplementalImageUrls(
            rawHtml = derivedContent,
            originalHtmlLength = boundedContent.length,
            existingImageCount = 0
        )

        val article = ParsedArticle(
            uniqueKey = id.ifBlank { link.ifBlank { resolvedTitle } },
            title = resolvedTitle,
            link = link,
            publishedAt = publishedAt,
            plainText = plainText,
            contentHtml = boundedContent.trimIfNeeded(),
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
        }.getOrElse { throwable ->
            Log.w(TAG, "URL konnte nicht aufgeloest werden: base=$baseUrl, value=$candidate", throwable)
            candidate
        }
    }

    private fun extractImageUrls(rawHtml: String, maxCount: Int = Int.MAX_VALUE): List<String> {
        if (!rawHtml.contains("<img", ignoreCase = true)) {
            return emptyList()
        }
        return imageSrcRegex
            .findAll(rawHtml)
            .map { it.groupValues[1] }
            .filterNot(::isLikelyAuthorAvatarUrl)
            .take(maxCount)
            .toList()
    }

    private fun isLikelyAuthorAvatarUrl(url: String): Boolean {
        val normalized = url.lowercase(Locale.US)
        return "gravatar.com/avatar/" in normalized || ".gravatar.com/avatar/" in normalized
    }

    private fun htmlToPlainText(raw: String): String {
        val normalized = if (raw.indexOf('<') == -1 && raw.indexOf('&') == -1) {
            raw
        } else {
            HtmlCompat.fromHtml(
                raw,
                HtmlCompat.FROM_HTML_MODE_LEGACY
            ).toString()
        }
        return normalized
            .replace(nbspRegex, " ")
            .replace(invisibleFormattingRegex, "")
            .replace(controlCharacterRegex, " ")
            .replace(whitespaceRegex, " ")
            .trim()
    }

    private fun String.stripPlainTextNoise(): String {
        return htmlCommentRegex.replace(
            noscriptTagRegex.replace(
                styleTagRegex.replace(
                    scriptTagRegex.replace(this, " "),
                    " "
                ),
                " "
            ),
            " "
        )
    }

    private fun String.maybeStripPlainTextNoise(): String {
        if (!contains("<script", ignoreCase = true) &&
            !contains("<style", ignoreCase = true) &&
            !contains("<noscript", ignoreCase = true) &&
            !contains("<!--")
        ) {
            return this
        }
        return stripPlainTextNoise()
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

    private fun XmlPullParser.readElementContent(
        maxChars: Int? = null,
        onEarlyBound: ((Int) -> Unit)? = null
    ): String {
        val startDepth = depth
        val startName = name
        val content = StringBuilder(
            minOf(maxChars ?: READ_ELEMENT_CONTENT_CAPACITY, READ_ELEMENT_CONTENT_CAPACITY)
        )
        var earlyBoundLogged = false

        fun markEarlyBound() {
            if (!earlyBoundLogged && maxChars != null) {
                earlyBoundLogged = true
                onEarlyBound?.invoke(maxChars)
            }
        }

        while (true) {
            when (nextToken()) {
                XmlPullParser.START_TAG -> {
                    if (maxChars != null && content.length >= maxChars) {
                        markEarlyBound()
                        continue
                    }
                    content.ensureAppendCapacity(16 + name.orEmpty().length + (attributeCount * 24))
                    content.append('<').append(name)
                    for (index in 0 until attributeCount) {
                        content
                            .append(' ')
                            .append(getAttributeName(index))
                            .append("=\"")
                            .append(escapeHtmlAttribute(getAttributeValue(index)))
                            .append('"')
                    }
                    content.append('>')
                }

                XmlPullParser.END_TAG -> {
                    if (depth == startDepth && name == startName) {
                        return content.toString()
                    }
                    if (maxChars != null && content.length >= maxChars) {
                        markEarlyBound()
                        continue
                    }
                    content.ensureAppendCapacity(3 + name.orEmpty().length)
                    content.append("</").append(name).append('>')
                }

                XmlPullParser.TEXT,
                XmlPullParser.CDSECT,
                XmlPullParser.IGNORABLE_WHITESPACE,
                XmlPullParser.ENTITY_REF -> {
                    val value = text.orEmpty()
                    if (maxChars == null) {
                        content.ensureAppendCapacity(value.length)
                        content.append(value)
                    } else {
                        val remaining = maxChars - content.length
                        when {
                            remaining <= 0 -> markEarlyBound()
                            value.length <= remaining -> {
                                content.ensureAppendCapacity(value.length)
                                content.append(value)
                            }
                            else -> {
                                content.ensureAppendCapacity(remaining)
                                content.append(value, 0, remaining)
                                markEarlyBound()
                            }
                        }
                    }
                }

                XmlPullParser.COMMENT,
                XmlPullParser.PROCESSING_INSTRUCTION,
                XmlPullParser.DOCDECL -> Unit

                XmlPullParser.END_DOCUMENT -> return content.toString()

                else -> Unit
            }
        }
    }

    private fun XmlPullParser.skipCurrentElement() {
        val startDepth = depth
        while (true) {
            when (next()) {
                XmlPullParser.END_DOCUMENT -> return
                XmlPullParser.END_TAG -> if (depth == startDepth) {
                    return
                }
            }
        }
    }

    private fun escapeHtmlAttribute(value: String?): String =
        value.orEmpty()
            .replace("&", "&amp;")
            .replace("\"", "&quot;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")

    private fun String.isNonArticleLinkRel(): Boolean =
        equals("self", ignoreCase = true) ||
            equals("edit", ignoreCase = true) ||
            equals("enclosure", ignoreCase = true) ||
            equals("replies", ignoreCase = true)

    private fun StringBuilder.ensureAppendCapacity(additionalChars: Int) {
        if (additionalChars > 0) {
            ensureCapacity(length + additionalChars)
        }
    }

    private fun String.trimIfNeeded(): String {
        return if (isEmpty() || (first().isWhitespace().not() && last().isWhitespace().not())) {
            this
        } else {
            trim()
        }
    }

    private inner class FeedParseRuntime(
        val sourceUrl: String?,
        val payloadByteSize: Int?,
        val defensiveMode: Boolean
    ) {
        private val itemCap: Int? = if (defensiveMode) LARGE_FEED_DEFENSIVE_MAX_ITEMS else null
        private val perItemContentCharLimit: Int? =
            if (defensiveMode) LARGE_FEED_DEFENSIVE_MAX_ITEM_CONTENT_CHARS else null

        var itemCapApplied: Boolean = false
            private set
        var truncatedItems: Int = 0
            private set
        var imageScanSkipped: Boolean = false
            private set

        fun canParseMoreItems(currentCount: Int): Boolean = itemCap == null || currentCount < itemCap

        fun earlyContentReadCharLimit(): Int? = perItemContentCharLimit

        fun markItemCap() {
            if (!itemCapApplied) {
                itemCapApplied = true
                DebugLogger.w(
                    TAG,
                    "feed_parse_cap url=${sourceUrl.orEmpty()} bytes=${payloadByteSize ?: -1} limit=${itemCap ?: -1}"
                )
            }
        }

        fun applyDefensiveContentLimit(title: String, rawContent: String): String {
            val limit = perItemContentCharLimit ?: return rawContent
            if (rawContent.length <= limit) {
                return rawContent
            }
            truncatedItems += 1
            DebugLogger.w(
                TAG,
                "feed_parse_item_truncated url=${sourceUrl.orEmpty()} chars=${rawContent.length} limit=$limit title=${previewTitle(title)}"
            )
            val truncated = rawContent.take(limit)
            val lastSafeBoundary = maxOf(truncated.lastIndexOf('>'), truncated.lastIndexOf(' '))
            return if (lastSafeBoundary > limit / 2) {
                truncated.substring(0, lastSafeBoundary)
            } else {
                truncated
            }
        }

        fun markEarlyContentBound(tagName: String, limit: Int) {
            truncatedItems += 1
            DebugLogger.i(
                TAG,
                "feed_parse_item_bounded_early url=${sourceUrl.orEmpty()} tag=$tagName limit=$limit"
            )
        }

        fun prepareDerivedContent(
            title: String,
            rawContent: String
        ): String {
            if (!defensiveMode || rawContent.length <= LARGE_FEED_DEFENSIVE_MAX_DERIVED_CONTENT_CHARS) {
                return rawContent
            }
            DebugLogger.i(
                TAG,
                "feed_parse_derived_bounded url=${sourceUrl.orEmpty()} chars=${rawContent.length} limit=$LARGE_FEED_DEFENSIVE_MAX_DERIVED_CONTENT_CHARS title=${previewTitle(title)}"
            )
            return rawContent.take(LARGE_FEED_DEFENSIVE_MAX_DERIVED_CONTENT_CHARS)
        }

        fun extractSupplementalImageUrls(
            rawHtml: String,
            originalHtmlLength: Int,
            existingImageCount: Int
        ): List<String> {
            if (
                defensiveMode &&
                (
                    (existingImageCount > 0 &&
                        originalHtmlLength >= LARGE_FEED_DEFENSIVE_SKIP_IMAGE_SCAN_CHARS) ||
                        originalHtmlLength >= LARGE_FEED_DEFENSIVE_FORCE_SKIP_IMAGE_SCAN_CHARS
                    )
            ) {
                imageScanSkipped = true
                DebugLogger.i(
                    TAG,
                    "feed_parse_image_scan_skipped url=${sourceUrl.orEmpty()} chars=$originalHtmlLength existingImages=$existingImageCount"
                )
                return emptyList()
            }

            val scanHtml = if (defensiveMode && rawHtml.length > LARGE_FEED_DEFENSIVE_MAX_IMAGE_SCAN_CHARS) {
                rawHtml.take(LARGE_FEED_DEFENSIVE_MAX_IMAGE_SCAN_CHARS)
            } else {
                rawHtml
            }

            if (defensiveMode && originalHtmlLength > scanHtml.length) {
                DebugLogger.i(
                    TAG,
                    "feed_parse_image_scan_bounded url=${sourceUrl.orEmpty()} chars=$originalHtmlLength scanChars=${scanHtml.length}"
                )
            }

            val maxCount = if (defensiveMode) {
                LARGE_FEED_DEFENSIVE_MAX_REGEX_IMAGES
            } else {
                Int.MAX_VALUE
            }
            return extractImageUrls(scanHtml, maxCount)
        }

        private fun previewTitle(value: String): String {
            val normalized = value.replace(whitespaceRegex, " ").trim()
            return if (normalized.length <= 48) normalized else normalized.take(45) + "..."
        }
    }
}


