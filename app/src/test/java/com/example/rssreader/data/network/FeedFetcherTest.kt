package com.example.rssreader.data.network

import com.example.rssreader.data.errors.RssReaderException
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedFetcherTest {

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
}
