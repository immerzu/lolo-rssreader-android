package de.lolo.rssreader.data.repository

import de.lolo.rssreader.data.errors.RssReaderException
import de.lolo.rssreader.data.opml.OpmlFeedEntry
import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
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
    fun parseEntriesOrEmptyThrowsInvalidXmlForInvalidXml() = runTest {
        val failure = runCatching {
            OpmlImportSupport.parseEntriesOrEmpty(
            "not-an-opml-document".toByteArray(Charsets.UTF_8)
            )
        }.exceptionOrNull()

        assertTrue(failure is RssReaderException.InvalidXml)
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

    @Test
    fun runOpmlTasksBoundedLimitsConcurrencyAndPreservesInputOrder() = runTest {
        val gates = List(4) { CompletableDeferred<Unit>() }
        val started = mutableListOf<Int>()
        var currentConcurrency = 0
        var peakConcurrency = 0

        val deferred = async {
            runOpmlTasksBounded(listOf(0, 1, 2, 3), parallelism = 2) { index ->
                started += index
                currentConcurrency += 1
                peakConcurrency = maxOf(peakConcurrency, currentConcurrency)
                gates[index].await()
                currentConcurrency -= 1
                "done-$index"
            }
        }

        advanceUntilIdle()
        assertEquals(listOf(0, 1), started)

        gates[0].complete(Unit)
        advanceUntilIdle()
        assertEquals(listOf(0, 1, 2), started)

        gates[1].complete(Unit)
        advanceUntilIdle()
        assertEquals(listOf(0, 1, 2, 3), started)

        gates[2].complete(Unit)
        gates[3].complete(Unit)

        assertEquals(listOf("done-0", "done-1", "done-2", "done-3"), deferred.await())
        assertEquals(2, peakConcurrency)
    }
}
