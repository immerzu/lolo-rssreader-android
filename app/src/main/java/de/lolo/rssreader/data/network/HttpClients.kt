package de.lolo.rssreader.data.network

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Einheitliche HTTP-Konfiguration fuer Feed- und Volltextabrufe (RSS-DUP-001-004):
 * gleiche Timeouts und kein HTTPS->HTTP-Downgrade bei Weiterleitungen.
 * Tests injizieren weiterhin eigene Clients.
 */
internal object HttpClients {
    val default: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        // Ein Feed-/Artikel-Server darf nicht auf Klartext-HTTP herunterstufen.
        .followSslRedirects(false)
        .build()
}
