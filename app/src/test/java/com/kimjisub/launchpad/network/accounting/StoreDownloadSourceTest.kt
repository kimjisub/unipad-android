package com.kimjisub.launchpad.network.accounting

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import java.net.ServerSocket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class StoreDownloadSourceTest {
    @get:Rule val firebaseNetwork = FirebaseNetworkRule()
    @Test(timeout = 20_000) fun legacyFallbackUsesOnlyHeadThenReturnsTheDirectFileLocation() = runBlocking {
        val paths = listOf("downloadUniPackLegacy", "downloadUniPackLegacy/", "downloadUniPackLegacy/extra")
        ServerSocket(0).use { server ->
            val executor = Executors.newSingleThreadExecutor()
            val request = executor.submit<List<String>> {
                paths.map {
                    server.accept().use { socket ->
                        socket.soTimeout = 5_000
                        val reader = socket.getInputStream().bufferedReader()
                        val line = reader.readLine()
                        while (!reader.readLine().isNullOrEmpty()) { /* consume headers */ }
                        socket.getOutputStream().write("HTTP/1.1 302 Found\r\nLocation: https://files.example.invalid/pack.zip\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                        line
                    }
                }
            }
            try {
                val resolver = StoreDownloadSource(legacyHost = "127.0.0.1")
                paths.forEach { path ->
                    assertEquals("https://files.example.invalid/pack.zip", resolver.resolve(
                        "http://127.0.0.1:${server.localPort}/$path?code=pack"))
                }
                assertEquals(paths.map { "HEAD /$it?code=pack HTTP/1.1" }, request.get(10, TimeUnit.SECONDS))
            } finally { executor.shutdownNow() }
        }
    }

    @Test(timeout = 20_000) fun directPackUrlIsUsedUnchangedWithoutAnyLookup() = runBlocking {
        val url = "https://files.example.invalid/pack.zip"
        assertEquals(url, StoreDownloadSource().resolve(url))
    }
}
