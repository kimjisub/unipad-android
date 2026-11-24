package com.kimjisub.launchpad.network.accounting

import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import java.net.ServerSocket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class DownloadAccountingHttpTest {
    @get:Rule val firebaseNetwork = FirebaseNetworkRule()
    @Test(timeout = 20_000) fun requestsCarryOnlyCodeAndReceiptAndUsePostJson() = runBlocking {
        ServerSocket(0).use { server ->
            val executor = Executors.newSingleThreadExecutor()
            val received = executor.submit<List<Pair<String, String>>> {
                listOf("""{"status":"issued","receipt":"opaque-receipt","expiresAt":900000}""",
                    """{"status":"counted"}""").map { json ->
                    server.accept().use { socket ->
                        socket.soTimeout = 10_000
                        val reader = socket.getInputStream().bufferedReader()
                        val first = reader.readLine()
                        val headers = generateSequence { reader.readLine().takeIf(String::isNotEmpty) }.toList()
                        val length = headers.first { it.startsWith("Content-Length:", true) }.substringAfter(':').trim().toInt()
                        val body = CharArray(length)
                        var offset = 0
                        while (offset < length) { offset += reader.read(body, offset, length - offset) }
                        assertTrue(headers.any { it.equals("Content-Type: application/json; charset=utf-8", true) })
                        assertFalse(headers.any { it.startsWith("Authorization:", true) || it.startsWith("Cookie:", true) })
                        val bytes = json.toByteArray()
                        socket.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n").toByteArray() + bytes)
                        first to String(body)
                    }
                }
            }
            try {
                val api = HttpDownloadAccountingApi { "http://127.0.0.1:${server.localPort}/demo-unipad/us-central1/" }
                val receipt = api.issue("pack")!!
                assertEquals("opaque-receipt", receipt.receipt)
                assertEquals(900_000, receipt.expiresAt)
                assertEquals("counted", api.complete("pack", receipt.receipt))
                val calls = received.get(10, TimeUnit.SECONDS)
                assertEquals("POST /demo-unipad/us-central1/issueDownloadReceipt HTTP/1.1", calls[0].first)
                assertEquals("POST /demo-unipad/us-central1/completeDownload HTTP/1.1", calls[1].first)
                assertEquals(setOf("code"), JsonParser.parseString(calls[0].second).asJsonObject.keySet())
                assertEquals(setOf("code", "receipt"), JsonParser.parseString(calls[1].second).asJsonObject.keySet())
            } finally { executor.shutdownNow() }
        }
    }

    @Test(timeout = 20_000) fun disabledRouteNeverOpensANetworkConnection() = runBlocking {
        val api = HttpDownloadAccountingApi { null }
        assertNull(api.issue("pack"))
        assertEquals("deferred", api.complete("pack", "receipt"))
    }
}
