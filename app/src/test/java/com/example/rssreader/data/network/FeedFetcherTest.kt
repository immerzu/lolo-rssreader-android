package com.example.rssreader.data.network

import com.example.rssreader.data.errors.RssReaderException
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import okio.Buffer

class FeedFetcherTest {

    @Test
    fun fetchRejectsInvalidUrlBeforeAnyNetworkCall() {
        var callCount = 0
        val client = OkHttpClient.Builder()
            .addInterceptor {
                callCount += 1
                error("Network should not be reached for invalid URLs")
            }
            .build()

        val failure = runCatching {
            runBlocking { FeedFetcher(client).fetch("not a url") }
        }.exceptionOrNull()

        assertTrue(failure is RssReaderException.InvalidUrl)
        assertEquals(0, callCount)
    }

    @Test
    fun fetchRejectsUnsupportedUrlSchemeBeforeAnyNetworkCall() {
        var callCount = 0
        val client = OkHttpClient.Builder()
            .addInterceptor {
                callCount += 1
                error("Network should not be reached for unsupported URL schemes")
            }
            .build()

        val failure = runCatching {
            runBlocking { FeedFetcher(client).fetch("ftp://example.com/feed.xml") }
        }.exceptionOrNull()

        assertTrue(failure is RssReaderException.InvalidUrl)
        assertEquals(0, callCount)
    }

    @Test
    fun fetchRejectsHttpUrlBeforeAnyNetworkCall() {
        var callCount = 0
        val client = OkHttpClient.Builder()
            .addInterceptor {
                callCount += 1
                error("Network should not be reached for cleartext feed URLs")
            }
            .build()

        val failure = runCatching {
            runBlocking { FeedFetcher(client).fetch("http://example.com/feed.xml") }
        }.exceptionOrNull()

        assertTrue(failure is RssReaderException.InvalidUrl)
        assertEquals(0, callCount)
    }

    @Test
    fun fetchRetriesOnceForTemporaryServerError() {
        var callCount = 0
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                callCount += 1
                val code = if (callCount == 1) 503 else 200
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message(if (code == 200) "OK" else "Server Error")
                    .body(
                        if (code == 200) {
                            """<?xml version="1.0" encoding="UTF-8"?><rss version="2.0"><channel><title>ok</title></channel></rss>"""
                                .toResponseBody("application/rss+xml; charset=UTF-8".toMediaType())
                        } else {
                            "".toResponseBody(null)
                        }
                    )
                    .build()
            }
            .build()

        val result = runCatching {
            runBlocking { FeedFetcher(client).fetch("https://example.com/feed.xml") }
        }

        assertTrue(result.isSuccess)
        assertEquals(2, callCount)
    }

    @Test
    fun fetchStopsAfterSecondTemporaryServerError() {
        var callCount = 0
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                callCount += 1
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(503)
                    .message("Server Error")
                    .body("".toResponseBody(null))
                    .build()
            }
            .build()

        val failure = runCatching {
            runBlocking { FeedFetcher(client).fetch("https://example.com/feed.xml") }
        }.exceptionOrNull()

        assertTrue(failure is RssReaderException.HttpError)
        assertEquals(503, (failure as RssReaderException.HttpError).code)
        assertEquals(2, callCount)
    }

    @Test
    fun fetchDoesNotRetryForNotFound() {
        var callCount = 0
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                callCount += 1
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(404)
                    .message("Not Found")
                    .body("".toResponseBody(null))
                    .build()
            }
            .build()

        val result = runCatching {
            runBlocking { FeedFetcher(client).fetch("https://example.com/feed.xml") }
        }

        assertTrue(result.exceptionOrNull() is RssReaderException.HttpError)
        assertEquals(1, callCount)
    }

    @Test
    fun fetchDoesNotRetryForUnauthorized() {
        var callCount = 0
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                callCount += 1
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(401)
                    .message("Unauthorized")
                    .body("".toResponseBody(null))
                    .build()
            }
            .build()

        val failure = runCatching {
            runBlocking { FeedFetcher(client).fetch("https://example.com/feed.xml") }
        }.exceptionOrNull()

        assertTrue(failure is RssReaderException.HttpError)
        assertEquals(401, (failure as RssReaderException.HttpError).code)
        assertEquals(1, callCount)
    }

    @Test
    fun fetchDoesNotRetryForEmptyResponseBody() {
        var callCount = 0
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                callCount += 1
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("".toResponseBody("application/rss+xml; charset=UTF-8".toMediaType()))
                    .build()
            }
            .build()

        val failure = runCatching {
            runBlocking { FeedFetcher(client).fetch("https://example.com/feed.xml") }
        }.exceptionOrNull()

        assertTrue(failure is RssReaderException.EmptyResponse)
        assertEquals(1, callCount)
    }

    @Test
    fun fetchRetriesOnceForTooManyRequestsThenSucceeds() {
        var callCount = 0
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                callCount += 1
                val code = if (callCount == 1) 429 else 200
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message(if (code == 200) "OK" else "Too Many Requests")
                    .body(
                        if (code == 200) {
                            """<?xml version="1.0" encoding="UTF-8"?><rss version="2.0"><channel><title>ok</title></channel></rss>"""
                                .toResponseBody("application/rss+xml; charset=UTF-8".toMediaType())
                        } else {
                            "".toResponseBody(null)
                        }
                    )
                    .build()
            }
            .build()

        val result = runCatching {
            runBlocking { FeedFetcher(client).fetch("https://example.com/feed.xml") }
        }

        assertTrue(result.isSuccess)
        assertEquals(2, callCount)
    }

    @Test
    fun fetchStopsAfterSecondTooManyRequests() {
        var callCount = 0
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                callCount += 1
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(429)
                    .message("Too Many Requests")
                    .body("".toResponseBody(null))
                    .build()
            }
            .build()

        val failure = runCatching {
            runBlocking { FeedFetcher(client).fetch("https://example.com/feed.xml") }
        }.exceptionOrNull()

        assertTrue(failure is RssReaderException.HttpError)
        assertEquals(429, (failure as RssReaderException.HttpError).code)
        assertEquals(2, callCount)
    }

    @Test
    fun fetchedFeedPayloadOpensFreshReadersWithResolvedCharset() {
        val payload = FetchedFeedPayload(
            responseBytes = "Straße".toByteArray(Charsets.ISO_8859_1),
            charset = Charsets.ISO_8859_1,
            byteSize = 6,
            defensiveMode = false
        )

        val firstRead = payload.openReader().use { it.readText() }
        val secondRead = payload.openReader().use { it.readText() }

        assertEquals("Straße", firstRead)
        assertEquals("Straße", secondRead)
    }

    @Test
    fun fetchedFeedPayloadOpensFreshStreamsForFutureParserWork() {
        val bytes = byteArrayOf(1, 2, 3, 4)
        val payload = FetchedFeedPayload(
            responseBytes = bytes,
            charset = Charsets.UTF_8,
            byteSize = bytes.size,
            defensiveMode = false
        )

        val firstRead = payload.openStream().readBytes()
        val secondRead = payload.openStream().readBytes()

        assertArrayEquals(bytes, firstRead)
        assertArrayEquals(bytes, secondRead)
    }

    @Test
    fun fetchedFeedPayloadReaderAndStreamAccessRemainIndependent() {
        val bytes = "Feed Inhalt".toByteArray(Charsets.UTF_8)
        val payload = FetchedFeedPayload(
            responseBytes = bytes,
            charset = Charsets.UTF_8,
            byteSize = bytes.size,
            defensiveMode = false
        )

        val textFromReader = payload.openReader().use { it.readText() }
        val bytesFromStream = payload.openStream().readBytes()

        assertEquals("Feed Inhalt", textFromReader)
        assertArrayEquals(bytes, bytesFromStream)
    }

    @Test
    fun fetchedFeedPayloadReaderStillSeesFullPayloadAfterStreamWasConsumed() {
        val bytes = "Großer Feed".toByteArray(Charsets.UTF_8)
        val payload = FetchedFeedPayload(
            responseBytes = bytes,
            charset = Charsets.UTF_8,
            byteSize = bytes.size,
            defensiveMode = true
        )

        payload.openStream().use { it.readBytes() }
        val textFromReader = payload.openReader().use { it.readText() }

        assertEquals("Großer Feed", textFromReader)
    }

    @Test
    fun fetchedFeedPayloadStreamStillSeesFullPayloadAfterReaderWasConsumed() {
        val bytes = "Noch ein Feed".toByteArray(Charsets.UTF_8)
        val payload = FetchedFeedPayload(
            responseBytes = bytes,
            charset = Charsets.UTF_8,
            byteSize = bytes.size,
            defensiveMode = false
        )

        payload.openReader().use { it.readText() }
        val bytesFromStream = payload.openStream().readBytes()

        assertArrayEquals(bytes, bytesFromStream)
    }

    @Test
    fun fetchedFeedPayloadFromBytesDerivesByteSizeFromPayload() {
        val bytes = "Feed Inhalt".toByteArray(Charsets.UTF_8)

        val payload = FetchedFeedPayload.fromBytes(
            responseBytes = bytes,
            charset = Charsets.UTF_8,
            defensiveMode = true
        )

        assertEquals(bytes.size, payload.byteSize)
        assertTrue(payload.defensiveMode)
        assertArrayEquals(bytes, payload.openStream().readBytes())
    }

    @Test
    fun fetchUsesVersionDerivedUserAgent() {
        var capturedUserAgent: String? = null
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                capturedUserAgent = chain.request().header("User-Agent")
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(
                        """<?xml version="1.0" encoding="UTF-8"?><rss version="2.0"><channel><title>ok</title></channel></rss>"""
                            .toResponseBody("application/rss+xml; charset=UTF-8".toMediaType())
                    )
                    .build()
            }
            .build()

        runBlocking { FeedFetcher(client).fetch("https://example.com/feed.xml") }

        assertEquals(AppUserAgent.value, capturedUserAgent)
        assertTrue(capturedUserAgent.orEmpty().startsWith("RSS-Reader/"))
    }

    @Test
    fun fetchPreservesLatin1PayloadWhenContentTypeDeclaresCharset() {
        val xml = """
            <?xml version="1.0" encoding="ISO-8859-1"?>
            <rss version="2.0">
              <channel>
                <title>Straße</title>
              </channel>
            </rss>
        """.trimIndent()
        val payloadBytes = xml.toByteArray(Charsets.ISO_8859_1)
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(
                        payloadBytes.toResponseBody(
                            "application/rss+xml; charset=ISO-8859-1".toMediaType()
                        )
                    )
                    .build()
            }
            .build()

        val payload = runBlocking { FeedFetcher(client).fetch("https://example.com/feed.xml") }

        assertEquals(Charsets.ISO_8859_1, payload.charset)
        assertTrue(payload.openReader().use { it.readText() }.contains("Straße"))
    }

    @Test
    fun fetchPreservesLatin1PayloadWhenContentTypeCharsetIsQuoted() {
        val xml = """
            <?xml version="1.0" encoding="ISO-8859-1"?>
            <rss version="2.0">
              <channel>
                <title>Straße</title>
              </channel>
            </rss>
        """.trimIndent()
        val payloadBytes = xml.toByteArray(Charsets.ISO_8859_1)
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(
                        payloadBytes.toResponseBody(
                            "application/rss+xml; charset=\"ISO-8859-1\"".toMediaType()
                        )
                    )
                    .build()
            }
            .build()

        val payload = runBlocking { FeedFetcher(client).fetch("https://example.com/feed.xml") }

        assertEquals(Charsets.ISO_8859_1, payload.charset)
        assertTrue(payload.openReader().use { it.readText() }.contains("Straße"))
    }

    @Test
    fun fetchPreservesLatin1PayloadWhenContentTypeHasTrailingParameters() {
        val xml = """
            <?xml version="1.0" encoding="ISO-8859-1"?>
            <rss version="2.0">
              <channel>
                <title>Straße</title>
              </channel>
            </rss>
        """.trimIndent()
        val payloadBytes = xml.toByteArray(Charsets.ISO_8859_1)
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(
                        payloadBytes.toResponseBody(
                            "application/rss+xml; charset=ISO-8859-1; profile=feed".toMediaType()
                        )
                    )
                    .build()
            }
            .build()

        val payload = runBlocking { FeedFetcher(client).fetch("https://example.com/feed.xml") }

        assertEquals(Charsets.ISO_8859_1, payload.charset)
        assertTrue(payload.openReader().use { it.readText() }.contains("Straße"))
    }

    @Test
    fun fetchPreservesLatin1PayloadWhenXmlPrologDeclaresCharset() {
        val xml = """
            <?xml version="1.0" encoding="ISO-8859-1"?>
            <rss version="2.0">
              <channel>
                <title>Straße</title>
              </channel>
            </rss>
        """.trimIndent()
        val payloadBytes = xml.toByteArray(Charsets.ISO_8859_1)
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(
                        payloadBytes.toResponseBody("application/rss+xml".toMediaType())
                    )
                    .build()
            }
            .build()

        val payload = runBlocking { FeedFetcher(client).fetch("https://example.com/feed.xml") }

        assertEquals(Charsets.ISO_8859_1, payload.charset)
        assertTrue(payload.openReader().use { it.readText() }.contains("Straße"))
    }

    @Test
    fun fetchFallsBackFromUnknownContentTypeCharsetToXmlPrologCharset() {
        val xml = """
            <?xml version="1.0" encoding="ISO-8859-1"?>
            <rss version="2.0">
              <channel>
                <title>Straße</title>
              </channel>
            </rss>
        """.trimIndent()
        val payloadBytes = xml.toByteArray(Charsets.ISO_8859_1)
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(
                        payloadBytes.toResponseBody(
                            "application/rss+xml; charset=X-UNKNOWN-CHARSET".toMediaType()
                        )
                    )
                    .build()
            }
            .build()

        val payload = runBlocking { FeedFetcher(client).fetch("https://example.com/feed.xml") }

        assertEquals(Charsets.ISO_8859_1, payload.charset)
        assertTrue(payload.openReader().use { it.readText() }.contains("Straße"))
    }

    @Test
    fun fetchRetriesOnceForSocketTimeoutThenSucceeds() {
        var callCount = 0
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                callCount += 1
                if (callCount == 1) {
                    throw SocketTimeoutException("timeout")
                }
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(
                        """<?xml version="1.0" encoding="UTF-8"?><rss version="2.0"><channel><title>ok</title></channel></rss>"""
                            .toResponseBody("application/rss+xml; charset=UTF-8".toMediaType())
                    )
                    .build()
            }
            .build()

        val result = runCatching {
            runBlocking { FeedFetcher(client).fetch("https://example.com/feed.xml") }
        }

        assertTrue(result.isSuccess)
        assertEquals(2, callCount)
    }

    @Test
    fun fetchRetriesOnceForConnectExceptionThenSucceeds() {
        var callCount = 0
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                callCount += 1
                if (callCount == 1) {
                    throw ConnectException("refused")
                }
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(
                        """<?xml version="1.0" encoding="UTF-8"?><rss version="2.0"><channel><title>ok</title></channel></rss>"""
                            .toResponseBody("application/rss+xml; charset=UTF-8".toMediaType())
                    )
                    .build()
            }
            .build()

        val result = runCatching {
            runBlocking { FeedFetcher(client).fetch("https://example.com/feed.xml") }
        }

        assertTrue(result.isSuccess)
        assertEquals(2, callCount)
    }

    @Test
    fun fetchRetriesOnceForGenericIOExceptionThenSucceeds() {
        var callCount = 0
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                callCount += 1
                if (callCount == 1) {
                    throw IOException("broken pipe")
                }
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(
                        """<?xml version="1.0" encoding="UTF-8"?><rss version="2.0"><channel><title>ok</title></channel></rss>"""
                            .toResponseBody("application/rss+xml; charset=UTF-8".toMediaType())
                    )
                    .build()
            }
            .build()

        val result = runCatching {
            runBlocking { FeedFetcher(client).fetch("https://example.com/feed.xml") }
        }

        assertTrue(result.isSuccess)
        assertEquals(2, callCount)
    }

    @Test
    fun fetchMapsUnknownHostWithoutRetry() {
        var callCount = 0
        val client = OkHttpClient.Builder()
            .addInterceptor {
                callCount += 1
                throw UnknownHostException("unresolved")
            }
            .build()

        val failure = runCatching {
            runBlocking { FeedFetcher(client).fetch("https://example.com/feed.xml") }
        }.exceptionOrNull()

        assertTrue(failure is RssReaderException.NetworkUnavailable)
        assertEquals(1, callCount)
    }

    @Test
    fun fetchFallsBackToLatin1WhenUtf8HeaderCannotDecodePayload() {
        val xml = """
            <rss version="2.0">
              <channel>
                <title>Straße</title>
              </channel>
            </rss>
        """.trimIndent()
        val payloadBytes = xml.toByteArray(Charsets.ISO_8859_1)
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(
                        payloadBytes.toResponseBody(
                            "application/rss+xml; charset=UTF-8".toMediaType()
                        )
                    )
                    .build()
            }
            .build()

        val payload = runBlocking { FeedFetcher(client).fetch("https://example.com/feed.xml") }

        assertEquals(Charsets.ISO_8859_1, payload.charset)
        assertTrue(payload.openReader().use { it.readText() }.contains("Straße"))
    }

    @Test
    fun fetchRejectsDeclaredOversizedBodyWithoutRetry() {
        var callCount = 0
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                callCount += 1
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(
                        object : ResponseBody() {
                            override fun contentType() = "application/rss+xml".toMediaType()

                            override fun contentLength(): Long = 13L * 1024L * 1024L

                            override fun source() = Buffer().writeUtf8("x")
                        }
                    )
                    .build()
            }
            .build()

        val failure = runCatching {
            runBlocking { FeedFetcher(client).fetch("https://example.com/feed.xml") }
        }.exceptionOrNull()

        assertTrue(failure is RssReaderException.FeedTooLarge)
        assertEquals(1, callCount)
    }

    @Test
    fun fetchRejectsStreamedOversizedBodyWithoutRetry() {
        var callCount = 0
        val oversizedBytes = ByteArray(13 * 1024 * 1024) { 'a'.code.toByte() }
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                callCount += 1
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(
                        object : ResponseBody() {
                            override fun contentType() = "application/rss+xml".toMediaType()

                            override fun contentLength(): Long = -1L

                            override fun source() = Buffer().write(oversizedBytes)
                        }
                    )
                    .build()
            }
            .build()

        val failure = runCatching {
            runBlocking { FeedFetcher(client).fetch("https://example.com/feed.xml") }
        }.exceptionOrNull()

        assertTrue(failure is RssReaderException.FeedTooLarge)
        assertEquals(1, callCount)
    }

    @Test
    fun fetchIgnoresUnknownXmlPrologCharsetAndFallsBackToLatin1() {
        val xml = """
            <?xml version="1.0" encoding="X-UNKNOWN-CHARSET"?>
            <rss version="2.0">
              <channel>
                <title>Straße</title>
              </channel>
            </rss>
        """.trimIndent()
        val payloadBytes = xml.toByteArray(Charsets.ISO_8859_1)
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(
                        payloadBytes.toResponseBody(
                            "application/rss+xml; charset=UTF-8".toMediaType()
                        )
                    )
                    .build()
            }
            .build()

        val failure = runCatching {
            runBlocking { FeedFetcher(client).fetch("https://example.com/feed.xml") }
        }.getOrThrow()

        assertEquals(Charsets.ISO_8859_1, failure.charset)
        assertTrue(failure.openReader().use { it.readText() }.contains("Straße"))
    }
}
