package com.example.rssreader.data.errors

sealed class RssReaderException(
    val userMessage: String,
    cause: Throwable? = null
) : RuntimeException(userMessage, cause) {
    class InvalidUrl(url: String, cause: Throwable? = null) : RssReaderException(
        userMessage = "Die Feed-Adresse ist ungueltig.",
        cause = cause
    ) {
        val originalUrl: String = url
    }

    class Timeout(cause: Throwable? = null) : RssReaderException(
        userMessage = "Der Feed hat nicht rechtzeitig geantwortet.",
        cause = cause
    )

    class NetworkUnavailable(cause: Throwable? = null) : RssReaderException(
        userMessage = "Die Feed-Adresse konnte nicht aufgeloest werden.",
        cause = cause
    )

    class ConnectionFailed(cause: Throwable? = null) : RssReaderException(
        userMessage = "Die Verbindung zum Feed konnte nicht aufgebaut werden.",
        cause = cause
    )

    class HttpError(val code: Int) : RssReaderException(
        userMessage = when (code) {
            401, 403 -> "Der Feed verweigert den Zugriff."
            404 -> "Der Feed wurde nicht gefunden."
            429 -> "Der Feed lehnt weitere Anfragen voruebergehend ab."
            in 500..599 -> "Der Feed-Server hat einen Fehler gemeldet."
            else -> "Der Feed konnte nicht geladen werden (HTTP $code)."
        }
    )

    class EmptyResponse : RssReaderException(
        userMessage = "Der Feed hat keinen Inhalt geliefert."
    )

    class FeedTooLarge(val actualSizeBytes: Long, val limitBytes: Long) : RssReaderException(
        userMessage = "Der Feed ist fuer eine sichere Verarbeitung zu gross."
    )

    class InvalidXml(cause: Throwable? = null) : RssReaderException(
        userMessage = "Der Feed enthaelt kein gueltiges RSS- oder Atom-XML.",
        cause = cause
    )

    class EncodingError(cause: Throwable? = null) : RssReaderException(
        userMessage = "Der Feed konnte nicht korrekt gelesen werden.",
        cause = cause
    )

    class DuplicateFeed : RssReaderException(
        userMessage = "Dieser Feed ist bereits vorhanden."
    )

    class UnsupportedImportFile : RssReaderException(
        userMessage = "Die Datei ist kein OPML-Export und enthaelt keine direkt importierbare Feed-Adresse."
    )

    class ImportFileTooLarge(val actualSizeBytes: Long, val limitBytes: Long) : RssReaderException(
        userMessage = "Die Importdatei ist fuer eine sichere Verarbeitung zu gross."
    )

    class FileReadFailed(cause: Throwable? = null) : RssReaderException(
        userMessage = "Die gewaehlte Datei konnte nicht gelesen werden.",
        cause = cause
    )

    class FileWriteFailed(cause: Throwable? = null) : RssReaderException(
        userMessage = "Die Zieldatei konnte nicht geschrieben werden.",
        cause = cause
    )
}

fun Throwable.toUserMessage(defaultMessage: String): String {
    return when (this) {
        is RssReaderException -> userMessage
        else -> defaultMessage
    }
}

