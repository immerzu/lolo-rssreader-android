package com.example.rssreader.data.translation

data class TranslationRequest(
    val title: String,
    val body: String,
    val sourceLanguage: String = "auto",
    val targetLanguage: String
)

data class TranslationResult(
    val translatedTitle: String,
    val translatedBody: String,
    val detectedSourceLanguage: String,
    val targetLanguage: String,
    val providerLabel: String
)

interface TranslationService {
    val providerId: String
    suspend fun translate(request: TranslationRequest): TranslationResult
}

sealed class TranslationException(
    val userMessage: String,
    cause: Throwable? = null
) : RuntimeException(userMessage, cause) {
    class InvalidRequest : TranslationException(
        userMessage = "Der Artikel enthaelt keinen uebersetzbaren Text."
    )

    class HttpFailure(val code: Int) : TranslationException(
        userMessage = when (code) {
            403 -> "Google Translate hat die Anfrage abgelehnt."
            429 -> "Google Translate blockiert weitere Anfragen voruebergehend."
            in 500..599 -> "Google Translate ist momentan nicht erreichbar."
            else -> "Die Uebersetzung konnte nicht geladen werden."
        }
    )

    class InvalidResponse(cause: Throwable? = null) : TranslationException(
        userMessage = "Die Antwort von Google Translate konnte nicht gelesen werden.",
        cause = cause
    )

    class NetworkFailure(cause: Throwable? = null) : TranslationException(
        userMessage = "Die Verbindung zu Google Translate konnte nicht aufgebaut werden.",
        cause = cause
    )
}
