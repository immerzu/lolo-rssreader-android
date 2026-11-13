package de.lolo.rssreader.data.network

import de.lolo.rssreader.data.errors.RssReaderException
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * Zentrale Zeichenkodierungs-Erkennung fuer Feed-XML.
 *
 * Reihenfolge: BOM, HTTP-Content-Type, XML-Prolog, UTF-8, ISO-8859-1. Ein UTF-16-BOM
 * wird auch ohne passenden HTTP-Header erkannt, und die Suche nach `charset=` ist
 * unabhaengig von der Schreibweise (Audit RSS-A10).
 */
internal object FeedEncoding {

    private val XML_PROLOG_CHARSET = Regex(
        """<\?xml[^>]*encoding=["']([A-Za-z0-9._\-]+)["']""",
        RegexOption.IGNORE_CASE
    )

    fun resolveCharset(responseBytes: ByteArray, contentTypeHeader: String?): Charset {
        val candidates = buildList {
            bomCharset(responseBytes)?.let(::add)
            extractCharset(contentTypeHeader)?.let(::add)
            extractXmlPrologCharset(responseBytes)?.let(::add)
            add(Charsets.UTF_8)
            add(Charsets.ISO_8859_1)
        }.distinctBy { it.name() }

        candidates.forEach { charset ->
            try {
                decodeStrict(responseBytes, charset)
                return charset
            } catch (_: CharacterCodingException) {
                // Naechsten Kandidaten versuchen.
            }
        }

        throw RssReaderException.EncodingError()
    }

    fun decode(responseBytes: ByteArray, contentTypeHeader: String? = null): String {
        val charset = resolveCharset(responseBytes, contentTypeHeader)
        return decodeStrict(responseBytes, charset).removePrefix("\uFEFF")
    }

    fun decodeStrict(responseBytes: ByteArray, charset: Charset): String {
        return charset
            .newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(responseBytes))
            .toString()
    }

    private fun bomCharset(bytes: ByteArray): Charset? {
        if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() &&
            bytes[2] == 0xBF.toByte()
        ) {
            return Charsets.UTF_8
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return Charsets.UTF_16LE
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return Charsets.UTF_16BE
        }
        return null
    }

    private fun extractCharset(contentTypeHeader: String?): Charset? {
        val header = contentTypeHeader ?: return null
        val markerIndex = header.lowercase().indexOf("charset=")
        if (markerIndex < 0) {
            return null
        }
        val charsetName = header
            .substring(markerIndex + "charset=".length)
            .substringBefore(';')
            .trim()
            .trim('"', '\'')
        if (charsetName.isBlank()) {
            return null
        }
        return runCatching { Charset.forName(charsetName) }.getOrNull()
    }

    private fun extractXmlPrologCharset(responseBytes: ByteArray): Charset? {
        // UTF-16-Vorlauf: Der Prolog ist bei UTF-16 mit Nullbytes durchsetzt und in
        // einer ISO-8859-1-Vorschau nicht lesbar; dann entscheidet das BOM.
        if (responseBytes.size >= 2) {
            val first = responseBytes[0]
            val second = responseBytes[1]
            if (first == 0x3C.toByte() && second == 0x00.toByte()) {
                return Charsets.UTF_16LE
            }
            if (first == 0x00.toByte() && second == 0x3C.toByte()) {
                return Charsets.UTF_16BE
            }
        }
        val previewLength = minOf(responseBytes.size, 512)
        val preview = String(responseBytes, 0, previewLength, Charsets.ISO_8859_1)
        val charsetName = XML_PROLOG_CHARSET.find(preview)?.groupValues?.getOrNull(1).orEmpty()
        if (charsetName.isBlank()) {
            return null
        }
        return runCatching { Charset.forName(charsetName) }.getOrNull()
    }
}
