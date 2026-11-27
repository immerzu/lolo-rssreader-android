package de.lolo.rssreader.data.network

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetSocketAddress
import java.util.concurrent.Executors

/**
 * RSS-DUP-001-004: sichert die gemeinsame HTTP-Konfiguration ab.
 * Timeouts sind direkt pruefbar; der HTTPS-Downgrade-Schutz ist eine
 * Eigenschaft von `followSslRedirects(false)` (kein Getter) und wird hier
 * nicht behavioristisch getestet (dafuer waere ein HTTPS-Testserver noetig).
 */
class HttpClientsTest {

    @Test
    fun defaultClientUsesTheDocumentedTimeouts() {
        assertEquals(15_000, HttpClients.default.connectTimeoutMillis)
        assertEquals(20_000, HttpClients.default.readTimeoutMillis)
        assertEquals(30_000, HttpClients.default.callTimeoutMillis)
    }

    @Test
    fun sharedClientHandlesParallelRequests() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val body = "ok".toByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.executor = Executors.newFixedThreadPool(4)
        server.start()
        try {
            val url = "http://127.0.0.1:${server.address.port}/"
            val codes = (1..8).map {
                async(Dispatchers.IO) {
                    HttpClients.default.newCall(Request.Builder().url(url).build())
                        .execute()
                        .use { response -> response.code }
                }
            }.awaitAll()

            assertEquals(8, codes.size)
            assertTrue("Alle parallelen Abrufe muessen erfolgreich sein", codes.all { it == 200 })
        } finally {
            server.stop(0)
        }
    }
}
