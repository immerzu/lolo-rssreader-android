package de.lolo.rssreader.data.opml

import de.lolo.rssreader.data.errors.RssReaderException
import de.lolo.rssreader.data.repository.readInputStreamBounded
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource

data class OpmlFeedEntry(
    val url: String,
    val title: String?
)

object OpmlCodec {
    // Geteilte Obergrenze fuer OPML-Importe. Direkt im Parser erzwungen, damit auch
    // zukuenftige Aufrufer von OpmlCodec.parse keinen unbegrenzten Stream vollstaendig
    // einlesen koennen. Entspricht dem Importlimit in FeedRepositoryOpmlSupport.
    const val OPML_IMPORT_MAX_BYTES = 2L * 1024L * 1024L

    fun parse(inputStream: InputStream): List<OpmlFeedEntry> {
        val inputBytes = readInputStreamBounded(inputStream, OPML_IMPORT_MAX_BYTES)
        val document = parseDocumentSafely(inputBytes)
        val outlines = document.getElementsByTagName("outline")
        val entries = mutableListOf<OpmlFeedEntry>()

        for (index in 0 until outlines.length) {
            runCatching {
                val node = outlines.item(index) ?: return@runCatching null
                val attributes = node.attributes ?: return@runCatching null
                val xmlUrl = attributes.getNamedItem("xmlUrl")?.nodeValue?.trim().orEmpty()
                if (xmlUrl.isBlank()) {
                    return@runCatching null
                }

                val title = attributes.getNamedItem("title")?.nodeValue?.trim()?.takeIf { it.isNotBlank() }
                    ?: attributes.getNamedItem("text")?.nodeValue?.trim()?.takeIf { it.isNotBlank() }

                OpmlFeedEntry(url = xmlUrl, title = title)
            }.getOrNull()?.let(entries::add)
        }

        return entries.distinctBy { it.url }
    }

    private fun parseDocumentSafely(inputBytes: ByteArray) = runCatching {
        val hardenedFactory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            runCatching { isXIncludeAware = false }
            setExpandEntityReferences(false)
            runCatching { setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
            // Viele echte OPML-Dateien enthalten ein DOCTYPE. Das komplett zu verbieten
            // waere strenger als der frueher funktionierende Importpfad.
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            runCatching { setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
        }
        hardenedFactory.newDocumentBuilder().apply {
            // Externe Entities und DTDs werden nicht aufgeloest: Der Resolver liefert
            // eine leere Quelle, unabhaengig davon, ob die Feature-Schalter auf dem
            // jeweiligen Android-Parser greifen (Audit RSS-A30).
            setEntityResolver { _, _ -> InputSource(StringReader("")) }
        }.parse(ByteArrayInputStream(inputBytes))
    }.getOrElse { throwable ->
        throw RssReaderException.InvalidXml(throwable)
    }

    fun build(feedEntries: List<OpmlFeedEntry>): String {
        return buildString {
            appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
            appendLine("""<opml version="1.0">""")
            appendLine("  <head>")
            appendLine("    <title>RSS Reader Feeds</title>")
            appendLine("  </head>")
            appendLine("  <body>")
            feedEntries.forEach { feed ->
                appendLine(
                    """    <outline text="${escapeXml(feed.title ?: feed.url)}" title="${escapeXml(feed.title ?: feed.url)}" type="rss" xmlUrl="${escapeXml(feed.url)}" />"""
                )
            }
            appendLine("  </body>")
            appendLine("</opml>")
        }
    }

    private fun escapeXml(value: String): String {
        return value
            .replace("&", "&amp;")
            .replace("\"", "&quot;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("'", "&apos;")
    }
}


