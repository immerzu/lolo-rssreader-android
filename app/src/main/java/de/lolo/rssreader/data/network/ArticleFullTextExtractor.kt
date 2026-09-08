package de.lolo.rssreader.data.network

import de.lolo.rssreader.data.errors.RssReaderException
import de.lolo.rssreader.debug.DebugLogger
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.URI
import java.net.UnknownHostException
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit

/**
 * Volltext, der aus der Originalseite eines Artikels gewonnen wurde.
 *
 * [html] ist bereits von Skripten, Navigation und Werbung befreit und enthaelt
 * ausschliesslich absolute http(s)-URLs. Die Darstellung uebernimmt anschliessend
 * die bestehende Reader-Pipeline (Sanitizing, Bildfilter, Schriftgroesse, Theme).
 */
data class ExtractedArticleContent(
    val title: String?,
    val html: String,
    val plainText: String,
    val imageUrls: List<String>
)

/**
 * Laedt die Originalseite eines Artikels und extrahiert daraus den lesbaren
 * Artikeltext. Wird nur auf ausdrueckliche Nutzeraktion aus der Leseransicht
 * aufgerufen; bei jedem Fehler greift die Leseransicht auf den Feed-Inhalt zurueck.
 *
 * Sicherheitsleitlinien analog zu [FeedFetcher]: nur HTTPS, Groessenlimit,
 * Credential-Maskierung in Logs, keine Ausfuehrung von Skripten.
 */
open class ArticleFullTextExtractor(
    private val client: OkHttpClient = defaultClient
) {
    open suspend fun fetch(url: String): ExtractedArticleContent {
        val normalizedUrl = validateUrl(url)
        DebugLogger.i(TAG, "fulltext_fetch url=${normalizedUrl.maskUrlCredentialsForLog()}")

        val request = try {
            Request.Builder()
                .url(normalizedUrl)
                .header("User-Agent", AppUserAgent.value)
                .header("Accept", ARTICLE_ACCEPT_HEADER)
                .header("Accept-Charset", ARTICLE_ACCEPT_CHARSET_HEADER)
                .get()
                .build()
        } catch (throwable: IllegalArgumentException) {
            throw RssReaderException.InvalidUrl(normalizedUrl, throwable)
        }

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    DebugLogger.w(TAG, "fulltext_http_error code=${response.code} url=${normalizedUrl.maskUrlCredentialsForLog()}", null)
                    throw RssReaderException.HttpError(response.code)
                }
                val body = response.body ?: throw RssReaderException.EmptyResponse()
                val bytes = readBounded(
                    inputStream = body.byteStream(),
                    declaredContentLength = body.contentLength()
                )
                if (bytes.isEmpty()) {
                    throw RssReaderException.EmptyResponse()
                }

                val charset = extractCharset(response.header("Content-Type"))
                val document = if (charset != null) {
                    Jsoup.parse(String(bytes, charset), normalizedUrl)
                } else {
                    // jsoup ermittelt das Charset selbst (BOM, <meta charset>).
                    Jsoup.parse(bytes.inputStream(), null, normalizedUrl)
                }

                val extracted = extractArticleContentFromDocument(document)
                    ?: throw RssReaderException.FullTextUnavailable()
                DebugLogger.i(
                    TAG,
                    "fulltext_extracted url=${normalizedUrl.maskUrlCredentialsForLog()} " +
                        "bytes=${bytes.size} plainChars=${extracted.plainText.length} images=${extracted.imageUrls.size}"
                )
                return extracted
            }
        } catch (exception: SocketTimeoutException) {
            throw RssReaderException.Timeout(exception)
        } catch (exception: UnknownHostException) {
            throw RssReaderException.NetworkUnavailable(exception)
        } catch (exception: ConnectException) {
            throw RssReaderException.ConnectionFailed(exception)
        } catch (exception: IOException) {
            throw RssReaderException.ConnectionFailed(exception)
        }
    }

    private fun validateUrl(url: String): String {
        val trimmed = url.trim()
        val uri = runCatching { URI(trimmed) }.getOrNull() ?: throw RssReaderException.InvalidUrl(trimmed)
        val scheme = uri.scheme?.lowercase() ?: throw RssReaderException.InvalidUrl(trimmed)
        if (scheme == "http") {
            throw RssReaderException.HttpNotAllowed(trimmed)
        }
        if (scheme != "https" || uri.host.isNullOrBlank()) {
            throw RssReaderException.InvalidUrl(trimmed)
        }
        return trimmed
    }

    private fun readBounded(inputStream: InputStream, declaredContentLength: Long): ByteArray {
        if (declaredContentLength > MAX_ARTICLE_HTML_BYTES) {
            throw RssReaderException.ArticleTooLarge(declaredContentLength, MAX_ARTICLE_HTML_BYTES)
        }
        val output = ByteArrayOutputStream(
            if (declaredContentLength in 1..MAX_ARTICLE_HTML_BYTES) declaredContentLength.toInt() else STREAM_BUFFER_SIZE
        )
        val buffer = ByteArray(STREAM_BUFFER_SIZE)
        var total = 0L
        inputStream.use { stream ->
            while (true) {
                val read = stream.read(buffer)
                if (read <= 0) {
                    break
                }
                total += read
                if (total > MAX_ARTICLE_HTML_BYTES) {
                    throw RssReaderException.ArticleTooLarge(total, MAX_ARTICLE_HTML_BYTES)
                }
                output.write(buffer, 0, read)
            }
        }
        return output.toByteArray()
    }

    private fun extractCharset(contentTypeHeader: String?): Charset? {
        val charsetName = contentTypeHeader
            ?.substringAfter("charset=", missingDelimiterValue = "")
            ?.substringBefore(';')
            ?.trim()
            ?.trim('"', '\'')
            .orEmpty()
        if (charsetName.isBlank()) {
            return null
        }
        return runCatching { Charset.forName(charsetName) }.getOrNull()
    }

    private fun String.maskUrlCredentialsForLog(): String {
        val uri = runCatching { URI(this) }.getOrNull() ?: return this
        val userInfo = uri.rawUserInfo ?: return this
        val masked = if (userInfo.contains(':')) "***:***" else "***"
        return replace("$userInfo@", "$masked@")
    }

    companion object {
        private const val TAG = "ArticleFullText"
        private const val ARTICLE_ACCEPT_HEADER =
            "text/html, application/xhtml+xml;q=0.9, text/plain;q=0.5, */*;q=0.1"
        private const val ARTICLE_ACCEPT_CHARSET_HEADER = "utf-8, iso-8859-1;q=0.8"
        internal const val MAX_ARTICLE_HTML_BYTES = 3L * 1024L * 1024L
        private const val STREAM_BUFFER_SIZE = 16 * 1024

        private val defaultClient: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            // Kein HTTPS->HTTP-Downgrade (analog FeedFetcher).
            .followSslRedirects(false)
            .build()
    }
}

/**
 * Mindestlaenge des extrahierten Textes. Kuerzere Ergebnisse gelten als "kein
 * Volltext gewinnbar" und loesen den Fallback auf den Feed-Inhalt aus.
 */
internal const val MIN_FULL_TEXT_PLAIN_LENGTH = 200

/**
 * Reihenfolge = Spezifitaet. Der erste Kandidat, der mindestens
 * [CANDIDATE_MIN_SHARE] der Textmenge des groessten Kandidaten enthaelt, gewinnt.
 */
private val CANDIDATE_SELECTORS = listOf(
    "article",
    "main",
    "[role=main]",
    "[itemprop=articleBody]",
    ".post-content",
    ".entry-content",
    ".article-content",
    ".article-body",
    ".content",
    "#content",
    "body"
)

private const val CANDIDATE_MIN_SHARE = 0.6

private const val STRIP_SELECTOR = "script, style, noscript, template, svg, canvas, form, " +
    "nav, header, footer, aside, button, select, textarea, " +
    "[role=navigation], [role=banner], [role=contentinfo], [role=search], [aria-hidden=true], " +
    ".nav, .navigation, .navbar, .menu, .sidebar, .side-bar, .widget, .skip-link, " +
    ".screen-reader-text, .comments, #comments, .comment, .comment-respond, " +
    ".advertisement, .advert, .ads, .ad, .social, .share, .sharing, .sharedaddy, " +
    ".newsletter, .subscribe, .related, .related-posts, .breadcrumb, .pagination, .pager, " +
    ".cookie, .cookie-banner, .gdpr, .byline, .author-box, .tags, .meta, .post-navigation"

private val whitespaceRegex = Regex("\\s+")
private val safeImageSchemeRegex = Regex("^https?://", RegexOption.IGNORE_CASE)

/**
 * Reine Extraktionslogik ohne Netzwerkzugriff (unit-testbar).
 *
 * @return `null`, wenn kein ausreichender Artikeltext gefunden wurde.
 */
internal fun extractArticleContentFromHtml(html: String, baseUrl: String): ExtractedArticleContent? {
    if (html.isBlank()) {
        return null
    }
    return extractArticleContentFromDocument(Jsoup.parse(html, baseUrl))
}

internal fun extractArticleContentFromDocument(document: Document): ExtractedArticleContent? {
    document.select(STRIP_SELECTOR).remove()
    val candidate = selectBestCandidate(document) ?: return null

    // Skript- und Inline-Event-Reste entfernen, bevor URLs absolut gemacht werden.
    candidate.select("script, style, noscript, iframe").remove()
    candidate.allElements.forEach { element ->
        val unsafeAttributes = element.attributes().map { it.key }
            .filter { it.startsWith("on", ignoreCase = true) || it.equals("srcdoc", ignoreCase = true) }
        unsafeAttributes.forEach(element::removeAttr)
    }
    candidate.select("a[href]").forEach { link ->
        val href = link.absUrl("href")
        if (href.isBlank() || href.startsWith("javascript:", ignoreCase = true)) {
            link.removeAttr("href")
        } else {
            link.attr("href", href)
        }
    }
    candidate.select("img[src]").forEach { image ->
        val src = image.absUrl("src")
        if (src.isBlank() || !safeImageSchemeRegex.containsMatchIn(src)) {
            image.remove()
        } else {
            image.attr("src", src)
        }
    }
    candidate.select("source[srcset], img[srcset]").forEach { it.removeAttr("srcset") }

    val plainText = candidate.text().replace(whitespaceRegex, " ").trim()
    if (plainText.length < MIN_FULL_TEXT_PLAIN_LENGTH) {
        return null
    }
    val imageUrls = candidate.select("img[src]")
        .map { it.attr("src").trim() }
        .filter { safeImageSchemeRegex.containsMatchIn(it) }
        .distinct()

    return ExtractedArticleContent(
        title = resolveTitle(document),
        html = candidate.html(),
        plainText = plainText,
        imageUrls = imageUrls
    )
}

private fun selectBestCandidate(document: Document): Element? {
    val candidates = CANDIDATE_SELECTORS.flatMap { selector ->
        document.select(selector)
    }.filter { it.text().isNotBlank() }
    if (candidates.isEmpty()) {
        return null
    }
    val maxLength = candidates.maxOf { it.text().length }
    val threshold = (maxLength * CANDIDATE_MIN_SHARE).toInt()
    // CANDIDATE_SELECTORS ist nach Spezifitaet geordnet: erster ausreichend
    // umfangreicher Treffer gewinnt, sonst der umfangreichste Kandidat.
    return CANDIDATE_SELECTORS.asSequence()
        .flatMap { document.select(it).asSequence() }
        .firstOrNull { it.text().isNotBlank() && it.text().length >= threshold }
        ?: candidates.maxByOrNull { it.text().length }
}

private fun resolveTitle(document: Document): String? {
    document.selectFirst("h1")?.text()?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
    return document.title().trim().takeIf { it.isNotBlank() }
}
