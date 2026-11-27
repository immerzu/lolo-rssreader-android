package de.lolo.rssreader.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.concurrent.thread

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
        val serverSocket = ServerSocket(0, 50, InetAddress.getLoopbackAddress())
        val acceptor = thread(isDaemon = true, name = "tiny-http") {
            while (!serverSocket.isClosed) {
                val client = runCatching { serverSocket.accept() }.getOrNull() ?: break
                thread(isDaemon = true) { answerOk(client) }
            }
        }
        try {
            val url = "http://127.0.0.1:${serverSocket.localPort}/"
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
            serverSocket.close()
            acceptor.join(2_000)
        }
    }

    private fun answerOk(client: java.net.Socket) {
        runCatching {
            val reader = client.getInputStream().bufferedReader()
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isEmpty()) break
            }
            val body = "ok".toByteArray()
            val header = "HTTP/1.1 200 OK\r\n" +
                "Content-Length: ${body.size}\r\n" +
                "Content-Type: text/plain\r\n" +
                "Connection: close\r\n\r\n"
            client.getOutputStream().apply {
                write(header.toByteArray())
                write(body)
                flush()
            }
        }
        client.close()
    }
}
