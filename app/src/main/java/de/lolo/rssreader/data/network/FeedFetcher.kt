package de.lolo.rssreader.data.network

import android.util.Log
import de.lolo.rssreader.BuildConfig
import de.lolo.rssreader.data.errors.RssReaderException
import de.lolo.rssreader.debug.DebugLogger
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.URI
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
    suspend fun fetch(url: String, etag: String? = null, lastModified: String? = null): FeedFetchResult {
        val ifNoneMatchPresent = !etag.isNullOrBlank()
        val ifModifiedSincePresent = !lastModified.isNullOrBlank()
        DebugLogger.i(
            TAG,
            "feed_fetch_request url=$url ifNoneMatch=$ifNoneMatchPresent ifModifiedSince=$ifModifiedSincePresent" +
                if (ifNoneMatchPresent) " etagPrefix=${etag.prefixHash()}" else "" +
                if (ifModifiedSincePresent) " lastModified=$lastModified" else ""
        )
        if (!isSupportedFeedUrl(url)) {
            throw RssReaderException.InvalidUrl(url)
        }
        val request = try {
            val builder = Request.Builder()
                .url(url)
                .header("User-Agent", AppUserAgent.value)
            if (ifNoneMatchPresent) {
                builder.header("If-None-Match", etag!!)
            }
            if (ifModifiedSincePresent) {
                builder.header("If-Modified-Since", lastModified!!)
            }
            builder.get().build()
        } catch (throwable: IllegalArgumentException) {
            throw RssReaderException.InvalidUrl(url, throwable)
        }

        repeat(MAX_FETCH_ATTEMPTS) { attempt ->
            try {
                client.newCall(request).execute().use { response ->
                    if (response.code == 304) {
                        DebugLogger.i(TAG, "feed_fetch_304 url=$url etagSent=$ifNoneMatchPresent lastModSent=$ifModifiedSincePresent")
                        return FeedFetchResult.NotModified
                    }
                    if (!response.isSuccessful) {
                        throw RssReaderException.HttpError(response.code)
                    }

                    val responseBody = response.body ?: throw RssReaderException.EmptyResponse()
                    val responseBytes = readResponseBodyBounded(
                        inputStream = responseBody.byteStream(),
                        declaredContentLength = responseBody.contentLength()
                    )
                    if (responseBytes.isEmpty()) {
                        throw RssReaderException.EmptyResponse()
                    }

                    val defensiveMode = responseBytes.size.toLong() > LARGE_FEED_SOFT_LIMIT_BYTES
                    if (defensiveMode) {
                        logWarn(
                            "feed_fetch_defensive url=$url bytes=${responseBytes.size} softLimit=$LARGE_FEED_SOFT_LIMIT_BYTES",
                            null
                        )
                    }

                    val charset = resolveResponseCharset(
                        responseBytes = responseBytes,
                        contentTypeHeader = response.header("Content-Type")
                    )

                    val responseEtagRaw = response.header("ETag").orEmpty()
                        .replace("\"", "")
                        .takeIf { it.isNotBlank() }
                    val responseLastModified = response.header("Last-Modified")
                        .takeIf { !it.isNullOrBlank() }
                    DebugLogger.i(
                        TAG,
                        "feed_fetch_response url=$url code=200" +
                            " etag=${responseEtagRaw.prefixHash() ?: "null"}" +
                            " lastModified=${responseLastModified ?: "null"}" +
                            " cacheControl=${response.header("Cache-Control").orEmpty().takeIf { it.isNotBlank() } ?: "null"}" +
                            " age=${response.header("Age") ?: "null"}" +
                            " date=${response.header("Date") ?: "null"}"
                    )
                    return FeedFetchResult.Success(
                        payload = FetchedFeedPayload.fromBytes(
                            responseBytes = responseBytes,
                            charset = charset,
                            defensiveMode = defensiveMode
                        ),
                        etag = responseEtagRaw,
                        lastModified = responseLastModified
                    ).also {
                        DebugLogger.i(
                            TAG,
                            "feed_fetch url=$url bytes=${responseBytes.size} defensive=$defensiveMode hardReject=false etag=${responseEtagRaw != null} lastModified=${responseLastModified != null}"
                        )
                    }
                }
            } catch (exception: RssReaderException) {
                if (!shouldRetry(exception, attempt)) {
                    if (exception is RssReaderException.FeedTooLarge) {
                        logWarn(
                            "feed_fetch_reject url=$url bytes=${exception.actualSizeBytes} hardLimit=${exception.limitBytes}",
                            exception
                        )
                    }
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

    private fun isSupportedFeedUrl(url: String): Boolean {
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase() ?: return false
        if (scheme == "http") {
            throw RssReaderException.HttpNotAllowed(url)
        }
        if (scheme != "https") {
            return false
        }
        return !uri.host.isNullOrBlank()
    }

    private fun readResponseBodyBounded(
        inputStream: InputStream,
        declaredContentLength: Long
    ): ByteArray {
        if (declaredContentLength > LARGE_FEED_HARD_LIMIT_BYTES) {
            throw RssReaderException.FeedTooLarge(
                actualSizeBytes = declaredContentLength,
                limitBytes = LARGE_FEED_HARD_LIMIT_BYTES
            )
        }

        if (declaredContentLength in 1..Int.MAX_VALUE.toLong()) {
            val expectedSize = declaredContentLength.toInt()
            val result = ByteArray(expectedSize)
            var totalRead = 0

            inputStream.use { stream ->
                while (totalRead < expectedSize) {
                    val read = stream.read(result, totalRead, expectedSize - totalRead)
                    if (read <= 0) {
                        break
                    }
                    totalRead += read
                }

                val extraByte = stream.read()
                if (extraByte == -1) {
                    return if (totalRead == expectedSize) {
                        result
                    } else {
                        result.copyOf(totalRead)
                    }
                }

                val output = ByteArrayOutputStream(
                    minOf(
                        LARGE_FEED_HARD_LIMIT_BYTES,
                        maxOf(totalRead.toLong() + FEED_STREAM_BUFFER_SIZE, LARGE_FEED_SOFT_LIMIT_BYTES)
                    ).toInt()
                )
                output.write(result, 0, totalRead)
                output.write(extraByte)
                copyRemainingBounded(stream, output, totalRead.toLong() + 1L)
                return output.toByteArray()
            }
        }

        inputStream.use { stream ->
            val output = ByteArrayOutputStream(FEED_STREAM_BUFFER_SIZE)
            copyRemainingBounded(stream, output, 0L)
            return output.toByteArray()
        }
    }

    private fun copyRemainingBounded(
        inputStream: InputStream,
        outputStream: ByteArrayOutputStream,
        initialBytesRead: Long
    ) {
        val buffer = ByteArray(FEED_STREAM_BUFFER_SIZE)
        var totalRead = initialBytesRead

        while (true) {
            val read = inputStream.read(buffer)
            if (read <= 0) {
                return
            }
            totalRead += read
            if (totalRead > LARGE_FEED_HARD_LIMIT_BYTES) {
                throw RssReaderException.FeedTooLarge(
                    actualSizeBytes = totalRead,
                    limitBytes = LARGE_FEED_HARD_LIMIT_BYTES
                )
            }
            outputStream.write(buffer, 0, read)
        }
    }

    private fun resolveResponseCharset(
        responseBytes: ByteArray,
        contentTypeHeader: String?
    ): Charset {
        val charsetCandidates = buildList {
            extractCharset(contentTypeHeader)?.let(::add)
            extractXmlPrologCharset(responseBytes)?.let(::add)
            add(Charsets.UTF_8)
            add(Charsets.ISO_8859_1)
        }.distinctBy { it.name() }

        charsetCandidates.forEach { charset ->
            try {
                decodeStrict(responseBytes, charset)
                return charset
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

    companion object {
        private const val TAG = "FeedFetcher"
        private const val MAX_FETCH_ATTEMPTS = 2
        private const val RETRY_DELAY_MS = 650L
        private const val LARGE_FEED_SOFT_LIMIT_BYTES = 5L * 1024L * 1024L
        private const val LARGE_FEED_HARD_LIMIT_BYTES = 12L * 1024L * 1024L
        private const val FEED_STREAM_BUFFER_SIZE = 16 * 1024
        private val xmlEncodingRegex = Regex(
            """<\?xml[^>]*encoding=["']([A-Za-z0-9._\-]+)["']""",
            RegexOption.IGNORE_CASE
        )

        private val defaultClient: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .build()

        /**
         * Kürzt einen ETag-/Header-Wert auf ersten 12 + letzten 8 Zeichen,
         * um Änderungen zwischen zwei Abrufen zu erkennen, ohne den
         * vollständigen Wert zu loggen.
         */
        private fun String?.prefixHash(): String? {
            val value = this ?: return null
            return if (value.length > 24) {
                value.take(12) + "…" + value.takeLast(8)
            } else {
                value.take(12)
            }
        }
    }

    private fun shouldRetry(exception: RssReaderException, attempt: Int): Boolean =
        attempt < MAX_FETCH_ATTEMPTS - 1 && when (exception) {
            is RssReaderException.Timeout,
            is RssReaderException.ConnectionFailed -> true

            is RssReaderException.HttpError -> exception.code == 429 || exception.code in 500..599
            else -> false
        }

    private fun logInfo(message: String) {
        DebugLogger.i(TAG, message)
        if (!BuildConfig.DEBUG) {
            return
        }
        runCatching { Log.i(TAG, message) }
            .getOrElse { println("$TAG: $message") }
    }

    private fun logWarn(message: String, throwable: Throwable?) {
        DebugLogger.w(TAG, message, throwable)
        if (!BuildConfig.DEBUG) {
            return
        }
        runCatching { Log.w(TAG, message, throwable) }
            .getOrElse {
                System.err.println("$TAG: $message")
                if (throwable != null) {
                    System.err.println("$TAG: ${throwable.javaClass.simpleName}: ${throwable.message.orEmpty()}")
                }
            }
    }
}

data class FetchedFeedPayload(
    val responseBytes: ByteArray,
    val charset: Charset,
    val byteSize: Int,
    val defensiveMode: Boolean
) {
    companion object {
        fun fromBytes(
            responseBytes: ByteArray,
            charset: Charset,
            defensiveMode: Boolean
        ): FetchedFeedPayload =
            FetchedFeedPayload(
                responseBytes = responseBytes,
                charset = charset,
                byteSize = responseBytes.size,
                defensiveMode = defensiveMode
            )
    }

    // Keep the payload as one bounded in-memory snapshot for now. This preserves the current
    // charset detection and lets parser/tests open fresh readers without coupling callers to
    // ByteArray-specific details, which keeps a later streaming migration localized.
    // Each call returns a fresh stream so future parser work can consume the payload multiple
    // times without exposing ByteArray handling at call sites.
    fun openStream(): ByteArrayInputStream = ByteArrayInputStream(responseBytes)

    fun openReader(): InputStreamReader = InputStreamReader(openStream(), charset)
}

sealed class FeedFetchResult {
    data class Success(
        val payload: FetchedFeedPayload,
        val etag: String?,
        val lastModified: String?
    ) : FeedFetchResult()
    data object NotModified : FeedFetchResult()
}
