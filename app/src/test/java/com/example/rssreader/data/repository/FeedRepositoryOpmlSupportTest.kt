package com.example.rssreader.data.repository

import com.example.rssreader.data.errors.RssReaderException
import com.example.rssreader.data.opml.OpmlFeedEntry
import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedRepositoryOpmlSupportTest {

    @Test
    fun readImportBytesReturnsFullContentWithinLimit() = runTest {
        val bytes = "<opml></opml>".toByteArray(Charsets.UTF_8)

        val result = OpmlImportSupport.readImportBytes(ByteArrayInputStream(bytes))

        assertEquals(String(bytes, Charsets.UTF_8), String(result, Charsets.UTF_8))
    }

    @Test
    fun readImportBytesReturnsEmptyByteArrayForEmptyInput() = runTest {
        val result = OpmlImportSupport.readImportBytes(ByteArrayInputStream(ByteArray(0)))

        assertEquals(0, result.size)
    }

    @Test
    fun readImportBytesRejectsOversizedInput() = runTest {
        val oversizedBytes = ByteArray(2 * 1024 * 1024 + 1)

        val failure = runCatching {
            OpmlImportSupport.readImportBytes(ByteArrayInputStream(oversizedBytes))
        }.exceptionOrNull()

        assertTrue(failure is RssReaderException.ImportFileTooLarge)
    }

    @Test
    fun readImportBytesRejectsOversizedInputAcrossSmallChunks() = runTest {
        val oversizedBytes = ByteArray(2 * 1024 * 1024 + 1)
        val chunkedStream = object : InputStream() {
            private var index = 0

            override fun read(): Int {
                if (index >= oversizedBytes.size) return -1
                return oversizedBytes[index++].toInt() and 0xFF
            }

            override fun read(buffer: ByteArray, off: Int, len: Int): Int {
                if (index >= oversizedBytes.size) return -1
                val toRead = minOf(len, 17, oversizedBytes.size - index)
                System.arraycopy(oversizedBytes, index, buffer, off, toRead)
                index += toRead
                return toRead
            }
        }

        val failure = runCatching {
            OpmlImportSupport.readImportBytes(chunkedStream)
        }.exceptionOrNull()

        assertTrue(failure is RssReaderException.ImportFileTooLarge)
    }

    @Test
    fun readImportBytesAcceptsInputAtExactSoftLimit() = runTest {
        val exactLimitBytes = ByteArray(2 * 1024 * 1024)

        val result = OpmlImportSupport.readImportBytes(ByteArrayInputStream(exactLimitBytes))

        assertEquals(exactLimitBytes.size, result.size)
    }

    @Test
    fun parseEntriesOrEmptyReturnsEmptyListForInvalidXml() = runTest {
        val entries = OpmlImportSupport.parseEntriesOrEmpty(
            "not-an-opml-document".toByteArray(Charsets.UTF_8)
        )

        assertTrue(entries.isEmpty())
    }

    @Test
    fun parseEntriesOrEmptyDeduplicatesDuplicateUrlsWhileKeepingFirstTitle() = runTest {
        val entries = OpmlImportSupport.parseEntriesOrEmpty(
            """
                <?xml version="1.0" encoding="UTF-8"?>
                <opml version="1.0">
                  <body>
                    <outline text="Erster Titel" title="Erster Titel" xmlUrl="https://example.com/feed.xml" />
                    <outline text="Zweiter Titel" title="Zweiter Titel" xmlUrl="https://example.com/feed.xml" />
                    <outline text="Dritter Titel" title="Dritter Titel" xmlUrl="https://example.com/other.xml" />
                  </body>
                </opml>
            """.trimIndent().toByteArray(Charsets.UTF_8)
        )

        assertEquals(
            listOf(
                OpmlFeedEntry(url = "https://example.com/feed.xml", title = "Erster Titel"),
                OpmlFeedEntry(url = "https://example.com/other.xml", title = "Dritter Titel")
            ),
            entries
        )
    }

    @Test
    fun parseEntriesOrEmptyUsesTextAttributeWhenTitleIsMissing() = runTest {
        val entries = OpmlImportSupport.parseEntriesOrEmpty(
            """
                <?xml version="1.0" encoding="UTF-8"?>
                <opml version="1.0">
                  <body>
                    <outline text="Nur Text Titel" xmlUrl="https://example.com/feed.xml" />
                  </body>
                </opml>
            """.trimIndent().toByteArray(Charsets.UTF_8)
        )

        assertEquals(
            listOf(OpmlFeedEntry(url = "https://example.com/feed.xml", title = "Nur Text Titel")),
            entries
        )
    }

    @Test
    fun parseEntriesOrEmptySkipsOutlinesWithoutUsableXmlUrl() = runTest {
        val entries = OpmlImportSupport.parseEntriesOrEmpty(
            """
                <?xml version="1.0" encoding="UTF-8"?>
                <opml version="1.0">
                  <body>
                    <outline text="Ohne URL" />
                    <outline text="Leere URL" xmlUrl="   " />
                    <outline text="Mit URL" xmlUrl="https://example.com/feed.xml" />
                  </body>
                </opml>
            """.trimIndent().toByteArray(Charsets.UTF_8)
        )

        assertEquals(
            listOf(OpmlFeedEntry(url = "https://example.com/feed.xml", title = "Mit URL")),
            entries
        )
    }

    @Test
    fun parseEntriesOrEmptyParsesNestedOutlinesAndTrimsUrlsBeforeDeduplication() = runTest {
        val entries = OpmlImportSupport.parseEntriesOrEmpty(
            """
                <?xml version="1.0" encoding="UTF-8"?>
                <opml version="1.0">
                  <body>
                    <outline text="Ordner">
                      <outline text="Erster" xmlUrl=" https://example.com/feed.xml " />
                      <outline text="Duplikat" xmlUrl="https://example.com/feed.xml" />
                    </outline>
                    <outline text="Zweiter Ordner">
                      <outline text="Anderer Feed" xmlUrl="https://example.com/other.xml" />
                    </outline>
                  </body>
                </opml>
            """.trimIndent().toByteArray(Charsets.UTF_8)
        )

        assertEquals(
            listOf(
                OpmlFeedEntry(url = "https://example.com/feed.xml", title = "Erster"),
                OpmlFeedEntry(url = "https://example.com/other.xml", title = "Anderer Feed")
            ),
            entries
        )
    }
}
