package com.example.rssreader.data.translation

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit

class GoogleFreeTranslationService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .build(),
    private val preferredDomain: String = "com"
) : TranslationService {

    override val providerId: String = "google-free"

    override suspend fun translate(request: TranslationRequest): TranslationResult = withContext(Dispatchers.IO) {
        val sanitizedTitle = request.title.trim()
        val sanitizedBody = request.body.trim()
        if (sanitizedTitle.isBlank() && sanitizedBody.isBlank()) {
            throw TranslationException.InvalidRequest()
        }

        val sourceLanguage = normalizeLanguageCode(request.sourceLanguage, allowAuto = true)
        val targetLanguage = normalizeLanguageCode(request.targetLanguage)
        val translatedTitle = sanitizedTitle.takeIf { it.isNotBlank() }?.let {
            translateText(
                text = it,
                sourceLanguage = sourceLanguage,
                targetLanguage = targetLanguage
            )
        }
        val translatedBody = translateLongText(
            text = sanitizedBody,
            sourceLanguage = sourceLanguage,
            targetLanguage = targetLanguage
        )

        val detectedLanguage = translatedTitle?.detectedSourceLanguage
            ?.takeIf { it.isNotBlank() && it != "auto" }
            ?: translatedBody.detectedSourceLanguage

        TranslationResult(
            translatedTitle = translatedTitle?.translatedText ?: sanitizedTitle,
            translatedBody = translatedBody.translatedText,
            detectedSourceLanguage = detectedLanguage,
            targetLanguage = targetLanguage,
            providerLabel = "Google"
        )
    }

    private fun translateLongText(
        text: String,
        sourceLanguage: String,
        targetLanguage: String
    ): GoogleTranslationPayload {
        if (text.isBlank()) {
            return GoogleTranslationPayload(
                translatedText = "",
                detectedSourceLanguage = sourceLanguage
            )
        }

        val translatedChunks = mutableListOf<String>()
        var detectedSourceLanguage = sourceLanguage
        splitIntoChunks(text).forEachIndexed { index, chunk ->
            val payload = translateText(
                text = chunk,
                sourceLanguage = sourceLanguage,
                targetLanguage = targetLanguage
            )
            if (index == 0 && payload.detectedSourceLanguage.isNotBlank()) {
                detectedSourceLanguage = payload.detectedSourceLanguage
            }
            translatedChunks += payload.translatedText
        }

        return GoogleTranslationPayload(
            translatedText = translatedChunks.joinToString(separator = "\n\n").trim(),
            detectedSourceLanguage = detectedSourceLanguage
        )
    }

    private fun translateText(
        text: String,
        sourceLanguage: String,
        targetLanguage: String
    ): GoogleTranslationPayload {
        val normalizedText = text.trim()
        if (normalizedText.isBlank()) {
            return GoogleTranslationPayload("", sourceLanguage)
        }

        val request = buildTranslateRequest(
            text = normalizedText,
            sourceLanguage = sourceLanguage,
            targetLanguage = targetLanguage
        )

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw TranslationException.HttpFailure(response.code)
                }
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) {
                    throw TranslationException.InvalidResponse()
                }
                val payload = parseGoogleTranslationResponse(body)
                Log.d(
                    TAG,
                    "Translated article chunk via Google: sl=${payload.detectedSourceLanguage}, tl=$targetLanguage, chars=${normalizedText.length}"
                )
                return payload
            }
        } catch (exception: TranslationException) {
            throw exception
        } catch (exception: IOException) {
            throw TranslationException.NetworkFailure(exception)
        } catch (exception: Exception) {
            throw TranslationException.InvalidResponse(exception)
        }
    }

    private fun buildTranslateRequest(
        text: String,
        sourceLanguage: String,
        targetLanguage: String
    ): Request {
        val token = computeGoogleToken(text)
        val encodedTextLength = java.net.URLEncoder.encode(text, Charsets.UTF_8.name()).length
        val usePost = encodedTextLength > MAX_GET_QUERY_LENGTH
        val urlBuilder = buildHost()
            .newBuilder()
            .addPathSegments("translate_a/single")
            .addQueryParameter("client", "gtx")
            .addQueryParameter("sl", sourceLanguage)
            .addQueryParameter("tl", targetLanguage)
            .addQueryParameter("hl", getUiLanguageCode())
            .addQueryParameter("dt", "t")
            .addQueryParameter("dt", "ld")
            .addQueryParameter("ie", "UTF-8")
            .addQueryParameter("oe", "UTF-8")
            .addQueryParameter("tk", token)

        if (!usePost) {
            urlBuilder.addQueryParameter("q", text)
        }

        val requestBuilder = Request.Builder()
            .url(urlBuilder.build())
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")

        if (usePost) {
            requestBuilder.post(
                FormBody.Builder(Charsets.UTF_8)
                    .add("q", text)
                    .build()
            )
        } else {
            requestBuilder.get()
        }

        return requestBuilder.build()
    }

    private fun buildHost() = "https://translate.google.$preferredDomain/".toHttpUrl()

    private fun getUiLanguageCode(): String {
        return normalizeLanguageCode(Locale.getDefault().language)
    }

    private fun normalizeLanguageCode(
        languageCode: String,
        allowAuto: Boolean = false
    ): String {
        val normalized = languageCode.trim().ifBlank { if (allowAuto) "auto" else "de" }
        return when (normalized.lowercase(Locale.ROOT)) {
            "auto" -> if (allowAuto) "auto" else "de"
            "he", "iw" -> "iw"
            "zh", "zh-cn" -> "zh-CN"
            "zh-tw" -> "zh-TW"
            else -> normalized
        }
    }

    private fun splitIntoChunks(text: String): List<String> {
        val cleanedText = text.trim()
        if (cleanedText.length <= MAX_TRANSLATE_CHARS_PER_REQUEST) {
            return listOf(cleanedText)
        }

        val chunks = mutableListOf<String>()
        val paragraphs = cleanedText.split(chunkSeparatorRegex)
        val currentChunk = StringBuilder()

        paragraphs.forEach { paragraph ->
            val normalizedParagraph = paragraph.trim()
            if (normalizedParagraph.isBlank()) {
                return@forEach
            }

            if (normalizedParagraph.length > MAX_TRANSLATE_CHARS_PER_REQUEST) {
                if (currentChunk.isNotEmpty()) {
                    chunks += currentChunk.toString().trim()
                    currentChunk.clear()
                }
                chunks += splitOversizedParagraph(normalizedParagraph)
                return@forEach
            }

            val separatorLength = if (currentChunk.isEmpty()) 0 else 2
            if (currentChunk.length + separatorLength + normalizedParagraph.length > MAX_TRANSLATE_CHARS_PER_REQUEST) {
                chunks += currentChunk.toString().trim()
                currentChunk.clear()
            }

            if (currentChunk.isNotEmpty()) {
                currentChunk.append("\n\n")
            }
            currentChunk.append(normalizedParagraph)
        }

        if (currentChunk.isNotEmpty()) {
            chunks += currentChunk.toString().trim()
        }

        return chunks.filter { it.isNotBlank() }
    }

    private fun splitOversizedParagraph(paragraph: String): List<String> {
        val chunks = mutableListOf<String>()
        var startIndex = 0
        while (startIndex < paragraph.length) {
            val endIndex = (startIndex + MAX_TRANSLATE_CHARS_PER_REQUEST).coerceAtMost(paragraph.length)
            chunks += paragraph.substring(startIndex, endIndex).trim()
            startIndex = endIndex
        }
        return chunks.filter { it.isNotBlank() }
    }

    // Port der TKK-basierten Token-Idee aus der JS-Referenz, aber lokal auf Kotlin angepasst.
    private fun computeGoogleToken(text: String): String {
        val seedParts = GOOGLE_TKK.split(".")
        val seed = seedParts.firstOrNull()?.toLongOrNull() ?: 0L
        val key = seedParts.getOrNull(1)?.toLongOrNull() ?: 0L
        val values = mutableListOf<Long>()
        var index = 0
        while (index < text.length) {
            val codePoint = text[index].code
            when {
                codePoint < 0x80 -> values += codePoint.toLong()
                codePoint < 0x800 -> {
                    values += (codePoint shr 6 or 0xC0).toLong()
                    values += (codePoint and 0x3F or 0x80).toLong()
                }
                codePoint in 0xD800..0xDBFF && index + 1 < text.length -> {
                    val next = text[index + 1].code
                    if (next in 0xDC00..0xDFFF) {
                        val surrogate = 0x10000 + ((codePoint and 0x3FF) shl 10) + (next and 0x3FF)
                        values += (surrogate shr 18 or 0xF0).toLong()
                        values += (surrogate shr 12 and 0x3F or 0x80).toLong()
                        values += (surrogate shr 6 and 0x3F or 0x80).toLong()
                        values += (surrogate and 0x3F or 0x80).toLong()
                        index += 1
                    } else {
                        values += (codePoint shr 12 or 0xE0).toLong()
                        values += (codePoint shr 6 and 0x3F or 0x80).toLong()
                        values += (codePoint and 0x3F or 0x80).toLong()
                    }
                }
                else -> {
                    values += (codePoint shr 12 or 0xE0).toLong()
                    values += (codePoint shr 6 and 0x3F or 0x80).toLong()
                    values += (codePoint and 0x3F or 0x80).toLong()
                }
            }
            index += 1
        }

        var accumulator = seed
        values.forEach { value ->
            accumulator += value
            accumulator = mix(accumulator, "+-a^+6")
        }
        accumulator = mix(accumulator, "+-3^+b+-f")
        accumulator = accumulator xor key
        if (accumulator < 0) {
            accumulator = (accumulator and Int.MAX_VALUE.toLong()) + Int.MAX_VALUE + 1L
        }
        accumulator %= 1_000_000L
        return "$accumulator.${accumulator xor seed}"
    }

    private fun mix(value: Long, pattern: String): Long {
        var result = value
        var index = 0
        while (index < pattern.length - 2) {
            val raw = pattern[index + 2]
            val shift = if (raw >= 'a') raw.code - 87 else raw.digitToInt()
            val moved = if (pattern[index + 1] == '+') {
                result ushr shift
            } else {
                result shl shift
            }
            result = if (pattern[index] == '+') {
                (result + moved) and 0xFFFFFFFF
            } else {
                result xor moved
            }
            index += 3
        }
        return result
    }

    companion object {
        private const val TAG = "GoogleTranslation"
        private const val USER_AGENT = "RSS-Reader/1.70 (+Android)"
        private const val GOOGLE_TKK = "0.0"
        private const val MAX_GET_QUERY_LENGTH = 1500
        private const val MAX_TRANSLATE_CHARS_PER_REQUEST = 3500
        private val chunkSeparatorRegex = Regex("\\n\\s*\\n")
    }
}

internal data class GoogleTranslationPayload(
    val translatedText: String,
    val detectedSourceLanguage: String
)

internal fun parseGoogleTranslationResponse(body: String): GoogleTranslationPayload {
    try {
        val root = JSONArray(body)
        val sentences = root.optJSONArray(0)
        val translatedText = buildString {
            if (sentences != null) {
                for (index in 0 until sentences.length()) {
                    val sentence = sentences.optJSONArray(index) ?: continue
                    append(sentence.optString(0))
                }
            }
        }.trim()

        if (translatedText.isBlank()) {
            throw TranslationException.InvalidResponse()
        }

        val detectedLanguage = root.optJSONArray(8)
            ?.optJSONArray(0)
            ?.optString(0)
            .orEmpty()
            .ifBlank { "auto" }

        return GoogleTranslationPayload(
            translatedText = translatedText,
            detectedSourceLanguage = detectedLanguage
        )
    } catch (exception: TranslationException) {
        throw exception
    } catch (exception: Exception) {
        throw TranslationException.InvalidResponse(exception)
    }
}
