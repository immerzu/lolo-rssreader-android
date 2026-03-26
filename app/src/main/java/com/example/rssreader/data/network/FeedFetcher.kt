package com.example.rssreader.data.network

import android.util.Log
import com.example.rssreader.data.errors.RssReaderException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay

class FeedFetcher(
    private val client: OkHttpClient = defaultClient
) {
    companion object {
        private const val TAG = "FeedFetcher"
        private const val USER_AGENT = "RSS-Reader/1.60 (+Android)"
        private const val MAX_FETCH_ATTEMPTS = 2
        private const val RETRY_DELAY_MS = 650L
        private val xmlEncodingRegex = Regex(
            """<\?xml[^>]*encoding=["']([A-Za-z0-9._\-]+)["']""",
            RegexOption.IGNORE_CASE
        )

        private val defaultClient: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    suspend fun fetch(url: String): String {
        val request = try {
            Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .get()
                .build()
        } catch (throwable: IllegalArgumentException) {
            throw RssReaderException.InvalidUrl(url, throwable)
        }

        repeat(MAX_FETCH_ATTEMPTS) { attempt ->
            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw RssReaderException.HttpError(response.code)
                    }

                    val responseBody = response.body ?: throw RssReaderException.EmptyResponse()
                    val responseBytes = responseBody.bytes()
                    if (responseBytes.isEmpty()) {
                        throw RssReaderException.EmptyResponse()
                    }

                    return decodeResponseBody(
                        responseBytes = responseBytes,
                        contentTypeHeader = response.header("Content-Type")
                    )
                }
            } catch (exception: RssReaderException) {
                if (!shouldRetry(exception, attempt)) {
                    logWarn("Feed konnte nicht geladen werden: $url", exception)
                    throw exception
                }
                logInfo("Temporärer Feed-Fehler, neuer Versuch (${attempt + 1}/$MAX_FETCH_ATTEMPTS): $url")
                delay(RETRY_DELAY_MS)
            } catch (exception: SocketTimeoutException) {
                val mapped = RssReaderException.Timeout(exception)
                if (!shouldRetry(mapped, attempt)) {
                    logWarn("Timeout beim Laden des Feeds: $url", exception)
                    throw mapped
                }
                logInfo("Timeout beim Feed, neuer Versuch (${attempt + 1}/$MAX_FETCH_ATTEMPTS): $url")
                delay(RETRY_DELAY_MS)
            } catch (exception: UnknownHostException) {
                val mapped = RssReaderException.NetworkUnavailable(exception)
                logWarn("Feed-Adresse nicht auflösbar: $url", exception)
                throw mapped
            } catch (exception: ConnectException) {
                val mapped = RssReaderException.ConnectionFailed(exception)
                if (!shouldRetry(mapped, attempt)) {
                    logWarn("Verbindung zum Feed fehlgeschlagen: $url", exception)
                    throw mapped
                }
                logInfo("Verbindungsfehler beim Feed, neuer Versuch (${attempt + 1}/$MAX_FETCH_ATTEMPTS): $url")
                delay(RETRY_DELAY_MS)
            } catch (exception: IOException) {
                val mapped = RssReaderException.ConnectionFailed(exception)
                if (!shouldRetry(mapped, attempt)) {
                    logWarn("I/O-Fehler beim Laden des Feeds: $url", exception)
                    throw mapped
                }
                logInfo("I/O-Fehler beim Feed, neuer Versuch (${attempt + 1}/$MAX_FETCH_ATTEMPTS): $url")
                delay(RETRY_DELAY_MS)
            }
        }

        throw RssReaderException.ConnectionFailed()
    }

    private fun decodeResponseBody(
        responseBytes: ByteArray,
        contentTypeHeader: String?
    ): String {
        val charsetCandidates = buildList {
            extractCharset(contentTypeHeader)?.let(::add)
            extractXmlPrologCharset(responseBytes)?.let(::add)
            add(Charsets.UTF_8)
            add(Charsets.ISO_8859_1)
        }.distinctBy { it.name() }

        charsetCandidates.forEach { charset ->
            try {
                return decodeStrict(responseBytes, charset)
            } catch (_: CharacterCodingException) {
                // Try the next candidate charset before surfacing an encoding error.
            }
        }

        throw RssReaderException.EncodingError()
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

    private fun extractXmlPrologCharset(responseBytes: ByteArray): Charset? {
        val previewLength = minOf(responseBytes.size, 512)
        val preview = String(responseBytes, 0, previewLength, Charsets.ISO_8859_1)
        val charsetName = xmlEncodingRegex.find(preview)?.groupValues?.getOrNull(1).orEmpty()
        if (charsetName.isBlank()) {
            return null
        }
        return runCatching { Charset.forName(charsetName) }.getOrNull()
    }

    private fun decodeStrict(responseBytes: ByteArray, charset: Charset): String {
        return charset
            .newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(responseBytes))
            .toString()
    }

    private fun shouldRetry(exception: RssReaderException, attempt: Int): Boolean {
        if (attempt >= MAX_FETCH_ATTEMPTS - 1) {
            return false
        }
        return when (exception) {
            is RssReaderException.Timeout,
            is RssReaderException.ConnectionFailed -> true

            is RssReaderException.HttpError -> exception.code == 429 || exception.code in 500..599
            else -> false
        }
    }

    private fun logInfo(message: String) {
        runCatching { Log.i(TAG, message) }
            .getOrElse { println("$TAG: $message") }
    }

    private fun logWarn(message: String, throwable: Throwable) {
        runCatching { Log.w(TAG, message, throwable) }
            .getOrElse {
                System.err.println("$TAG: $message")
                System.err.println("$TAG: ${throwable.javaClass.simpleName}: ${throwable.message.orEmpty()}")
            }
    }
}


