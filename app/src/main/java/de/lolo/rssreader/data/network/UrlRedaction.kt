package de.lolo.rssreader.data.network

import java.net.URI

/**
 * Bereinigt Feed-URLs fuer Log-Ausgaben.
 *
 * Die tatsaechlich verwendete URL bleibt unveraendert; nur die Protokollierung
 * verliert Zugangsdaten (user:pass@), sensible Query-Parameter (Token, Schluessel,
 * Passwoerter) und das Fragment, das ebenfalls Tokens tragen kann
 * (Audit RSS-A24).
 */
internal object UrlRedaction {

    private val SENSITIVE_QUERY_NAMES = setOf(
        "token",
        "access_token",
        "key",
        "apikey",
        "api_key",
        "password",
        "passwd",
        "pwd",
        "secret",
        "sig",
        "signature",
        "auth",
        "session",
        "sessionid",
        "code"
    )

    fun redactForLog(rawUrl: String?): String {
        val url = rawUrl?.trim().orEmpty()
        if (url.isEmpty()) {
            return ""
        }
        val uri = runCatching { URI(url) }.getOrNull() ?: return "<url>"
        val scheme = uri.scheme ?: return "<url>"
        val host = uri.host.orEmpty()
        val path = uri.rawPath.orEmpty()
        val query = redactQuery(uri.rawQuery)
        return buildString {
            append(scheme)
            append("://")
            append(host)
            append(path)
            if (!query.isNullOrEmpty()) {
                append('?')
                append(query)
            }
        }
    }

    private fun redactQuery(rawQuery: String?): String? {
        if (rawQuery.isNullOrEmpty()) {
            return rawQuery
        }
        return rawQuery.split('&').joinToString("&") { pair ->
            val name = pair.substringBefore('=')
            if (name.lowercase() in SENSITIVE_QUERY_NAMES) {
                "$name=***"
            } else {
                pair
            }
        }
    }
}
