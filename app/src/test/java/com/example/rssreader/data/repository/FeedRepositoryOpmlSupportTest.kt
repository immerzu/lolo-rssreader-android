package com.example.rssreader.data.repository

import com.example.rssreader.data.errors.RssReaderException
import java.io.ByteArrayInputStream
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
    fun readImportBytesRejectsOversizedInput() = runTest {
        val oversizedBytes = ByteArray(2 * 1024 * 1024 + 1)

        val failure = runCatching {
            OpmlImportSupport.readImportBytes(ByteArrayInputStream(oversizedBytes))
        }.exceptionOrNull()

        assertTrue(failure is RssReaderException.ImportFileTooLarge)
    }

    @Test
    fun parseEntriesOrEmptyReturnsEmptyListForInvalidXml() = runTest {
        val entries = OpmlImportSupport.parseEntriesOrEmpty(
            "not-an-opml-document".toByteArray(Charsets.UTF_8)
        )

        assertTrue(entries.isEmpty())
    }
}
