package com.kimjisub.launchpad.api

import android.content.Intent
import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.kimjisub.launchpad.R
import com.kimjisub.launchpad.TestCaptures
import com.kimjisub.launchpad.activity.FBStoreActivity
import com.kimjisub.launchpad.api.file.FileApi
import com.kimjisub.launchpad.manager.PreferenceManager
import com.kimjisub.launchpad.network.FirebaseStoreCatalog
import com.kimjisub.launchpad.network.StoreCatalog
import com.kimjisub.launchpad.network.fb.StoreVO
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.loadKoinModules
import org.koin.core.context.unloadKoinModules
import org.koin.dsl.module
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket

/**
 * The app's own HTTP client on Android's TLS stack and network security config: a server whose
 * certificate the device does not trust is refused before any request reaches it, and the store
 * reports that download as failed instead of crashing.
 */
@RunWith(AndroidJUnit4::class)
class ServerCertificateDeviceTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val id = "certificate-${UUID.randomUUID()}"
    private val workspace = File(context.getExternalFilesDir(null), "UniPack").apply { mkdirs() }
    private val prefs = PreferenceManager(context)
    private val oldCount = prefs.prevStoreCount
    private val oldPath = prefs.downloadStoragePath
    private val untrusted = HeldCertificate.Builder()
        .commonName("unipad test server")
        .addSubjectAlternativeName("127.0.0.1")
        .build()
    private val server = UntrustedServer(untrusted, packZip())
    private var override: org.koin.core.module.Module? = null
    private var screen: ActivityScenario<FBStoreActivity>? = null

    @After
    fun restore() {
        screen?.close()
        server.close()
        override?.let { unloadKoinModules(it) }
        loadKoinModules(module { factory<StoreCatalog> { FirebaseStoreCatalog() } })
        prefs.prevStoreCount = oldCount
        prefs.downloadStoragePath = oldPath
        workspace.listFiles().orEmpty().filter { it.name.startsWith(id) }.forEach { check(it.deleteRecursively()) }
    }

    @Test
    fun appClientRefusesAServerTheDeviceDoesNotTrust() {
        assertThrows(SSLHandshakeException::class.java) {
            FileApi.service.download("${server.url}pack").execute()
        }
        assertEquals("a request reached the untrusted server", emptyList<String>(), server.requests)
        assertTrue("the server saw no refused handshake", server.refusedHandshakes.get() > 0)
    }

    /** Proves the refusal above comes from the certificate check, not from a broken test server. */
    @Test
    fun sameServerAnswersAClientThatTrustsItsCertificate() {
        val trusting = HandshakeCertificates.Builder().addTrustedCertificate(untrusted.certificate).build()
        val client = OkHttpClient.Builder()
            .sslSocketFactory(trusting.sslSocketFactory(), trusting.trustManager)
            .build()
        try {
            client.newCall(Request.Builder().url("${server.url}pack").build()).execute().use { response ->
                assertEquals(200, response.code)
                assertTrue(response.body.bytes().contentEquals(server.body))
            }
        } finally {
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
        }
    }

    @Test
    fun storeDownloadFromAnUntrustedServerFailsWithoutCrashing() {
        // Only the catalogue is replaced; the download goes through the app's own client.
        val code = id
        override = module {
            factory<StoreCatalog> {
                object : StoreCatalog {
                    override fun attach(listener: StoreCatalog.Listener) {
                        listener.onAdded(StoreVO(code = code, title = TITLE, producerName = "Device test", URL = "${server.url}pack"), code)
                        listener.onCount(1)
                    }
                    override fun detach() {}
                }
            }
        }.also { loadKoinModules(it) }
        prefs.downloadStoragePath = workspace.path
        if (Build.VERSION.SDK_INT >= 33) {
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, android.Manifest.permission.POST_NOTIFICATIONS)
        }
        screen = ActivityScenario.launch(Intent(context, FBStoreActivity::class.java))
        compose.waitUntil(10000) {
            compose.onAllNodesWithTag("store_pack_$id").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
        compose.onNodeWithTag("store_pack_$id").assertIsDisplayed().performClick()
        compose.waitUntil(10000) {
            compose.onAllNodesWithTag("store_detail").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
        compose.onNode(hasText(context.getString(R.string.download)) and hasAnyAncestor(hasTestTag("store_detail")))
            .assertIsDisplayed().performClick()

        // The list row, not the detail panel, reports the result of a download.
        val failedText = hasText(context.getString(R.string.failed))
        val row = hasTestTag("store_pack_$id")
        val failed = (row and failedText) or (failedText and hasAnyAncestor(row))
        val shown = runCatching {
            compose.waitUntil(30000) {
                compose.onAllNodes(failed).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() ||
                    File(workspace, id).isDirectory
            }
        }.isSuccess
        capture("store-untrusted-server")
        assertEquals("a request reached the untrusted server", emptyList<String>(), server.requests)
        assertFalse("a pack was installed from the untrusted server", File(workspace, id).exists())
        assertTrue("the store did not report the download as failed", shown)
        assertEquals(Lifecycle.State.RESUMED, screen!!.state)
    }

    private fun capture(name: String) {
        val prefix = InstrumentationRegistry.getArguments().getString("capturePrefix", "fixed")
        val device = UiDevice.getInstance(instrumentation)
        assertTrue(TestCaptures.screenshot(device, "certificate-captures/$prefix/$name.png"))
    }

    private fun packZip(): ByteArray = ByteArrayOutputStream().also { bytes ->
        ZipOutputStream(bytes).use { zip ->
            fun entry(name: String, data: ByteArray) { zip.putNextEntry(ZipEntry(name)); zip.write(data); zip.closeEntry() }
            entry("info", "title=$TITLE\nproducerName=Device test\nbuttonX=8\nbuttonY=8\nchain=1\n".toByteArray())
            entry("keySound", "1 1 1 a.wav\n".toByteArray())
            entry("sounds/a.wav", ByteArray(4096))
        }
    }.toByteArray()

    /** HTTPS on the loopback address with [certificate]; records each request line it receives. */
    private class UntrustedServer(certificate: HeldCertificate, val body: ByteArray) {
        private val socket = HandshakeCertificates.Builder().heldCertificate(certificate).build()
            .sslContext().serverSocketFactory
            .createServerSocket(0, 8, InetAddress.getByName("127.0.0.1")) as SSLServerSocket
        val url = "https://127.0.0.1:${socket.localPort}/"
        val requests = CopyOnWriteArrayList<String>()
        val refusedHandshakes = AtomicInteger()
        private val workers = Executors.newCachedThreadPool()

        init {
            workers.submit {
                while (!socket.isClosed) {
                    val connection = try { socket.accept() as SSLSocket } catch (_: IOException) { break }
                    workers.submit {
                        connection.use { peer ->
                            peer.soTimeout = 10000
                            try {
                                peer.startHandshake()
                            } catch (_: IOException) {
                                refusedHandshakes.incrementAndGet()
                                return@use
                            }
                            val reader = peer.getInputStream().bufferedReader()
                            val request = reader.readLine() ?: return@use
                            requests += request
                            while (!reader.readLine().isNullOrEmpty()) { /* consume headers */ }
                            val output = peer.getOutputStream()
                            output.write("HTTP/1.1 200 OK\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
                            output.write(body)
                            output.flush()
                        }
                    }
                }
            }
        }

        fun close() {
            socket.close()
            workers.shutdown()
            check(workers.awaitTermination(15, TimeUnit.SECONDS))
        }
    }

    private companion object {
        const val TITLE = "Certificate fixture"
    }
}
