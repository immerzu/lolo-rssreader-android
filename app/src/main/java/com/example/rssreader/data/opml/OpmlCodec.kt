package com.example.rssreader.data.opml

import java.io.InputStream
import javax.xml.parsers.DocumentBuilderFactory

data class OpmlFeedEntry(
    val url: String,
    val title: String?
)

object OpmlCodec {
    fun parse(inputStream: InputStream): List<OpmlFeedEntry> {
        val documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        val document = inputStream.use(documentBuilder::parse)
        val outlines = document.getElementsByTagName("outline")
        val entries = mutableListOf<OpmlFeedEntry>()

        for (index in 0 until outlines.length) {
            val node = outlines.item(index)
            val attributes = node.attributes ?: continue
            val xmlUrl = attributes.getNamedItem("xmlUrl")?.nodeValue?.trim().orEmpty()
            if (xmlUrl.isBlank()) {
                continue
            }

            val title = attributes.getNamedItem("title")?.nodeValue?.takeIf { it.isNotBlank() }
                ?: attributes.getNamedItem("text")?.nodeValue?.takeIf { it.isNotBlank() }

            entries += OpmlFeedEntry(url = xmlUrl, title = title)
        }

        return entries.distinctBy { it.url }
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


========================================================================================================================