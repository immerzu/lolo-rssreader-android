package com.example.rssreader.data.repository

import com.example.rssreader.data.errors.RssReaderException
import com.example.rssreader.data.opml.OpmlCodec
import com.example.rssreader.data.opml.OpmlFeedEntry
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream

private const val OPML_IMPORT_SOFT_MAX_BYTES = 2L * 1024L * 1024L
private const val OPML_IMPORT_BUFFER_SIZE = 8 * 1024

private val importableFeedSelfLinkRegex = Regex(
    """<(?:(?:\w+):)?link\b[^>]*\brel\s*=\s*["']self["'][^>]*\bhref\s*=\s*["']([^"']+)["']|<(?:(?:\w+):)?link\b[^>]*\bhref\s*=\s*["']([^"']+)["'][^>]*\brel\s*=\s*["']self["']""",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
)
private val importableFeedTypedLinkRegex = Regex(
    """<(?:(?:\w+):)?link\b[^>]*\bhref\s*=\s*["']([^"']+)["'][^>]*\btype\s*=\s*["'][^"']*(?:rss|atom|xml)[^"']*["']|<(?:(?:\w+):)?link\b[^>]*\btype\s*=\s*["'][^"']*(?:rss|atom|xml)[^"']*["'][^>]*\bhref\s*=\s*["']([^"']+)["']""",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
)
private val importableFeedTextLinkRegex = Regex(
    """<link>\s*(https?://[^<\s]+)\s*</link>""",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
)

internal object OpmlImportSupport {
    fun readImportBytes(inputStream: InputStream): ByteArray {
        return readInputStreamBounded(
            inputStream = inputStream,
            maxBytes = OPML_IMPORT_SOFT_MAX_BYTES
        )
    }

    fun parseEntriesOrEmpty(importBytes: ByteArray): List<OpmlFeedEntry> {
        return runCatching {
            OpmlCodec.parse(ByteArrayInputStream(importBytes))
        }.getOrElse { throwable ->
            if (throwable is RssReaderException.InvalidXml) {
                emptyList()
            } else {
                throw throwable
            }
        }
    }

    fun detectImportableFeedUrl(xml: String): String? {
        return sequenceOf(
            importableFeedSelfLinkRegex.extractHttpUrl(xml),
            importableFeedTypedLinkRegex.extractHttpUrl(xml),
            importableFeedTextLinkRegex.extractHttpUrl(xml)?.takeIf(::looksLikeFeedUrl)
        ).firstOrNull { !it.isNullOrBlank() }
    }
}

internal fun readInputStreamBounded(
    inputStream: InputStream,
    maxBytes: Long
): ByteArray {
    val output = ByteArrayOutputStream(minOf(maxBytes, OPML_IMPORT_BUFFER_SIZE.toLong()).toInt())
    val buffer = ByteArray(OPML_IMPORT_BUFFER_SIZE)
    var totalRead = 0L

    inputStream.use { stream ->
        while (true) {
            val read = stream.read(buffer)
            if (read <= 0) {
                return output.toByteArray()
            }
            totalRead += read
            if (totalRead > maxBytes) {
                throw RssReaderException.ImportFileTooLarge(
                    actualSizeBytes = totalRead,
                    limitBytes = maxBytes
                )
            }
            output.write(buffer, 0, read)
        }
    }
}

internal fun detectImportableFeedUrl(xml: String): String? {
    return OpmlImportSupport.detectImportableFeedUrl(xml)
}

private fun Regex.extractHttpUrl(xml: String): String? {
    return find(xml)
        ?.groupValues
        ?.drop(1)
        ?.firstOrNull { it.isNotBlank() }
        ?.trim()
        ?.takeIf(::isHttpUrl)
}

private fun isHttpUrl(url: String): Boolean {
    return url.startsWith("http://", ignoreCase = true) ||
        url.startsWith("https://", ignoreCase = true)
}

private fun looksLikeFeedUrl(url: String): Boolean {
    val normalized = url.lowercase()
    return "/feed" in normalized ||
        ".xml" in normalized ||
        "rss" in normalized ||
        "atom" in normalized
}
