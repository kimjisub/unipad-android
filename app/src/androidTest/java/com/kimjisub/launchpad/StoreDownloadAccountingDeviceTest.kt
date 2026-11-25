package com.kimjisub.launchpad

import android.app.NotificationManager
import android.app.Notification
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import com.kimjisub.launchpad.activity.FBStoreActivity
import com.kimjisub.launchpad.manager.PreferenceManager
import com.kimjisub.launchpad.network.StoreCatalog
import com.kimjisub.launchpad.network.accounting.DownloadAccounting
import com.kimjisub.launchpad.network.accounting.DownloadAccountingApi
import com.kimjisub.launchpad.network.accounting.DownloadReceipt
import com.kimjisub.launchpad.network.accounting.FilePendingDownloadStore
import com.kimjisub.launchpad.network.accounting.HttpDownloadAccountingApi
import com.kimjisub.launchpad.network.accounting.PendingDownload
import com.kimjisub.launchpad.network.accounting.PendingDownloadStore
import com.google.gson.JsonParser
import com.kimjisub.launchpad.network.accounting.StoreDownloadAccounting
import com.kimjisub.launchpad.network.fb.StoreVO
import com.kimjisub.launchpad.unipack.UniPackFolder
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.loadKoinModules
import org.koin.core.context.unloadKoinModules
import org.koin.dsl.module
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Exercises the actual store download callback, disk persistence and HTTP requests. */
@RunWith(AndroidJUnit4::class)
class StoreDownloadAccountingDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun savedStorePackCountsOnceAndLeavesTheDisplayedNumberUnchanged() = scenario(validZip = true)
    @Test fun invalidArchiveNeverSendsCompletion() = scenario(validZip = false)
    @Test fun disabledAccountingDoesNotPreventSavingThePack() = scenario(validZip = true, enabled = false)
    @Test fun unwritableWorkspaceNeverSendsCompletion() = scenario(validZip = true, storageFailure = true)

    private fun scenario(validZip: Boolean, enabled: Boolean = true, storageFailure: Boolean = false) {
        val name = when {
            storageFailure -> "storage-error"
            !enabled -> "disabled"
            validZip -> "saved"
            else -> "invalid"
        }
        val savesPack = validZip && !storageFailure
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        val id = "accounting-${UUID.randomUUID()}"
        val prefs = PreferenceManager(context)
        val oldPath = prefs.downloadStoragePath
        val oldCount = prefs.prevStoreCount
        // The internal workspace is a listed download target while it holds a pack, and unlike
        // shared storage it honours file permissions, so the storage failure is real on every API.
        val workspace = File(context.filesDir, "UniPack")
        val workspaceExisted = workspace.isDirectory
        File(workspace, "$id-listed").mkdirs()
        val queueFile = File(context.noBackupFilesDir, "$id.json")
        val store = FilePendingDownloadStore(queueFile)
        val issued = CountDownLatch(1)
        val backend = Backend(if (validZip) zip() else byteArrayOf(1, 2, 3), enabled)
        val realEmulator = InstrumentationRegistry.getArguments().getString("downloadAccountingBackend") == "demo-unipad"
        val base = if (realEmulator) "http://10.0.2.2:5051/demo-unipad/us-central1/" else backend.url
        if (realEmulator) {
            emulatorWrite("downloadAccounting/config/enabled", enabled.toString())
            emulatorWrite("store/$id", """{"code":"$id","downloadCount":50}""")
        }
        val http = HttpDownloadAccountingApi { base }
        val completions = AtomicInteger()
        val requests = CopyOnWriteArrayList<String>()
        val api = object : DownloadAccountingApi {
            override suspend fun issue(code: String): DownloadReceipt? = try {
                requests += "POST issueDownloadReceipt code=$code destination=$base"
                http.issue(code)
            } finally { issued.countDown() }
            override suspend fun complete(code: String, receipt: String): String {
                assertTrue("completion preceded installed folder", File(workspace, id).isDirectory)
                assertEquals("Accounting fixture", UniPackFolder(File(workspace, id)).load().title)
                completions.incrementAndGet()
                requests += "POST completeDownload code=$code destination=$base"
                return http.complete(code, receipt)
            }
        }
        val acknowledged = CountDownLatch(1)
        val observedStore = object : PendingDownloadStore {
            private var hadPending = false
            override fun load() = store.load()
            override fun save(downloads: List<PendingDownload>) {
                store.save(downloads)
                if (downloads.isNotEmpty()) hadPending = true
                else if (hadPending) acknowledged.countDown()
            }
        }
        val engine = DownloadAccounting(api, observedStore)
        val accounting = StoreDownloadAccounting(engine)
        val override = module {
            single<StoreDownloadAccounting> { accounting }
            single<StoreCatalog> {
                object : StoreCatalog {
                    override fun attach(listener: StoreCatalog.Listener) {
                        listener.onAdded(StoreVO(code = id, title = "Accounting fixture", producerName = "Device test",
                            downloadCount = 50, URL = "${backend.url}pack.zip"), id)
                        listener.onCount(1)
                    }
                    override fun detach() {}
                }
            }
        }
        prefs.downloadStoragePath = workspace.path
        if (storageFailure) assertTrue(workspace.setWritable(false, false))
        loadKoinModules(override)
        var screen: ActivityScenario<FBStoreActivity>? = null
        try {
            val activity = ActivityScenario.launch<FBStoreActivity>(Intent(context, FBStoreActivity::class.java))
            screen = activity
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("store_pack_$id")
                .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
            compose.onNodeWithTag("store_pack_$id").performClick()
            compose.waitForIdle()
            assertTrue(TestCaptures.screenshot(device, "download-accounting/before-$name.png"))
            compose.onNodeWithTag("store_detail").assertIsDisplayed()
            var permissionPrompt = false
            activity.onActivity { activity ->
                permissionPrompt = android.os.Build.VERSION.SDK_INT >= 33 &&
                    context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED &&
                    !activity.shouldShowRequestPermissionRationale(android.Manifest.permission.POST_NOTIFICATIONS)
            }
            compose.onNode(hasText(context.getString(R.string.download)) and
                hasAnyAncestor(hasTestTag("store_detail"))).performClick()
            if (permissionPrompt) {
                assertNotNull(device.wait(Until.findObject(By.res("com.android.permissioncontroller", "permission_deny_button")), 10_000))
                device.executeShellCommand("input keyevent KEYCODE_BACK")
                assertTrue(device.wait(Until.gone(By.res("com.android.permissioncontroller", "permission_deny_button")), 2_000))
            }
            // The permission prompt must not gate the download or its accounting.
            val expected = context.getString(if (savesPack) R.string.downloaded else R.string.failed)
            compose.waitUntil(20_000) { compose.onAllNodes(hasText(expected))
                .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
            if (!storageFailure) assertTrue(issued.await(10, TimeUnit.SECONDS))
            if (savesPack) {
                if (enabled) {
                    assertTrue("completion was not acknowledged within the retry interval", acknowledged.await(75, TimeUnit.SECONDS))
                    assertTrue(completions.get() >= 1)
                } else assertEquals(0, completions.get())
                assertTrue(store.load().isEmpty())
                assertTrue(File(workspace, id).isDirectory)
                val count = if (realEmulator) emulatorRead("store/$id/downloadCount").trim().toInt() else backend.count.get()
                assertEquals(if (enabled) 51 else 50, count)
            } else {
                assertEquals(0, completions.get())
                assertTrue(store.load().isEmpty())
                assertFalse(File(workspace, id).exists())
                if (realEmulator) assertEquals("50", emulatorRead("store/$id/downloadCount").trim())
            }
            // The catalogue still says 50: completion does not add one on the screen.
            compose.onNode(hasText("50")).assertIsDisplayed()
            assertTrue(requests.none { it.contains("increaseDownloadCount") || it.contains("downloadUniPackLegacy") })
            assertTrue(TestCaptures.screenshot(device, "download-accounting/after-$name.png"))
            assertTrue(TestCaptures.save(device, "download-accounting/requests-$name.txt") {
                it.writeText(requests.joinToString("\n") + "\ncompletions=${completions.get()}\n"); true
            })
            if (realEmulator && savesPack && enabled) verifyEmulatorRetryBoundaries(http, id, workspace, device)
        } finally {
            screen?.close()
            accounting.close()
            backend.close()
            unloadKoinModules(override)
            prefs.downloadStoragePath = oldPath
            prefs.prevStoreCount = oldCount
            workspace.setWritable(true)
            workspace.listFiles().orEmpty().filter { it.name.startsWith(id) }.forEach { check(it.deleteRecursively()) }
            if (!workspaceExisted) workspace.delete()
            queueFile.delete()
            val notifications = context.getSystemService(NotificationManager::class.java)
            notifications.activeNotifications.filter {
                it.notification.extras.getString(Notification.EXTRA_TITLE) == "Accounting fixture"
            }.forEach { notifications.cancel(it.tag, it.id) }
            if (realEmulator) emulatorWrite("store/$id", "null")
        }
    }

    /** The demo server's actual receipts, with real atomic files and an injectable device clock. */
    private fun verifyEmulatorRetryBoundaries(http: HttpDownloadAccountingApi, id: String, workspace: File, device: UiDevice) = runBlocking {
        val file = File(workspace, "$id-retry-boundaries.json")
        val store = FilePendingDownloadStore(file)
        val trace = mutableListOf<String>()
        var time = System.currentTimeMillis()
        var loseResponse = true
        val sent = mutableListOf<String>()
        val api = object : DownloadAccountingApi {
            override suspend fun issue(code: String) = http.issue(code)
            override suspend fun complete(code: String, receipt: String): String {
                sent += receipt
                val result = http.complete(code, receipt)
                trace += "complete $code $receipt $result"
                if (loseResponse) { loseResponse = false; throw java.io.IOException("Response lost after commit") }
                return result
            }
        }
        fun engine() = DownloadAccounting(api, FilePendingDownloadStore(file)) { time }
        fun count() = emulatorRead("store/$id/downloadCount").trim()
        try {
            val first = requireNotNull(engine().issue(id))
            engine().completed(id, first)
            engine().retryPending()
            assertEquals("52", count())
            assertEquals(first.receipt, store.load().single().receipt)
            time += DownloadAccounting.RETRY_INTERVAL_MS
            // A new engine and store read exactly the record left before an uncertain response.
            engine().retryPending()
            assertEquals(listOf(first.receipt, first.receipt), sent)
            assertTrue(store.load().isEmpty())
            assertEquals("52", count())
            trace += "response-loss: same receipt, count=52 after recreation"

            val next = requireNotNull(engine().issue(id))
            assertNotEquals(first.receipt, next.receipt)
            engine().completed(id, next)
            engine().retryPending()
            assertEquals("53", count())
            trace += "separate completion: new receipt, count=53"

            val deferred = requireNotNull(engine().issue(id))
            engine().completed(id, deferred)
            for (value in listOf("null", "\"invalid-count\"")) {
                emulatorWrite("store/$id/downloadCount", value)
                engine().retryPending()
                assertEquals(value, count())
                assertEquals(deferred.receipt, store.load().single().receipt)
                time += DownloadAccounting.RETRY_INTERVAL_MS
            }
            emulatorWrite("store/$id/downloadCount", "53")
            engine().retryPending()
            assertEquals("54", count())
            assertTrue(store.load().isEmpty())
            trace += "missing/non-numeric count: deferred, never initialized; restored count=54"

            emulatorWrite("downloadAccounting/config/enabled", "false")
            assertNull(engine().issue(id))
            assertTrue(store.load().isEmpty())
            emulatorWrite("downloadAccounting/config/enabled", "true")
            val expiring = requireNotNull(engine().issue(id))
            engine().completed(id, expiring)
            time = expiring.expiresAt
            val attemptsBeforeExpiry = sent.size
            engine().retryPending()
            time -= 2 * DownloadAccounting.RETENTION_MS
            engine().retryPending()
            assertEquals(attemptsBeforeExpiry, sent.size)
            assertTrue(store.load().isEmpty())
            assertEquals("54", count())
            trace += "disabled: no queue; expiry then clock rewind: no resurrection"

            time = System.currentTimeMillis()
            store.save(List(1_001) { index -> PendingDownload(id, "bounded-$index", time + index,
                time + DownloadAccounting.RETENTION_MS, time, time, DownloadAccounting.MAX_DAILY_ATTEMPTS) })
            engine().retryPending()
            assertEquals(1_000, store.load().size)
            assertEquals("bounded-1", store.load().first().receipt)
            time += DownloadAccounting.RETENTION_MS
            engine().retryPending()
            assertTrue(store.load().isEmpty())
            assertTrue(File(workspace, id).isDirectory)
            assertEquals("54", count())
            trace += "1001 pending: oldest removed; seven-day boundary: queue empty, pack retained"
        } finally {
            assertTrue(TestCaptures.save(device, "download-accounting/emulator-boundaries.txt") {
                it.writeText(trace.joinToString("\n") + "\n"); true
            })
            file.delete()
            emulatorWrite("downloadAccounting/config/enabled", "true")
        }
    }

    private fun emulatorWrite(path: String, json: String) {
        val connection = URL("http://10.0.2.2:9051/$path.json?ns=demo-unipad").openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 5_000; connection.readTimeout = 5_000
            connection.requestMethod = "PUT"
            connection.setRequestProperty("Authorization", "Bearer owner")
            connection.doOutput = true
            connection.outputStream.use { it.write(json.toByteArray()) }
            assertEquals(200, connection.responseCode)
        } finally { connection.disconnect() }
    }

    private fun emulatorRead(path: String): String {
        val connection = URL("http://10.0.2.2:9051/$path.json?ns=demo-unipad").openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 5_000; connection.readTimeout = 5_000
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally { connection.disconnect() }
    }

    private fun zip(): ByteArray = ByteArrayOutputStream().also { bytes ->
        ZipOutputStream(bytes).use { zip ->
            mapOf("info" to "title=Accounting fixture\nproducerName=Device test\nchain=1\nbuttonX=8\nbuttonY=8\n",
                "keySound" to "", "keyLED" to "").forEach { (path, text) ->
                zip.putNextEntry(ZipEntry(path)); zip.write(text.toByteArray()); zip.closeEntry()
            }
        }
    }.toByteArray()

    private class Backend(private val pack: ByteArray, private val enabled: Boolean) : AutoCloseable {
        private val socket = ServerSocket(0, 8, java.net.InetAddress.getByName("127.0.0.1"))
        val url = "http://127.0.0.1:${socket.localPort}/"
        val count = AtomicInteger(50)
        private val counted = ConcurrentHashMap.newKeySet<String>()
        private val executor = Executors.newCachedThreadPool()
        private val connections = CopyOnWriteArrayList<Socket>()
        init {
            executor.submit {
                while (!socket.isClosed) {
                    val peer = try { socket.accept() } catch (_: java.io.IOException) { break }
                    connections += peer
                    executor.submit {
                        peer.use {
                            peer.soTimeout = 10_000
                            val reader = peer.getInputStream().bufferedReader()
                            val first = reader.readLine()
                            val headers = generateSequence { reader.readLine().takeIf(String::isNotEmpty) }.toList()
                            val length = headers.firstOrNull { it.startsWith("Content-Length:", true) }
                                ?.substringAfter(':')?.trim()?.toInt() ?: 0
                            val body = CharArray(length)
                            var offset = 0
                            while (offset < length) offset += reader.read(body, offset, length - offset)
                            val bytes = when {
                                first.startsWith("GET /pack.zip") -> pack
                                first.startsWith("POST /issueDownloadReceipt") ->
                                    (if (enabled) """{"status":"issued","receipt":"local-receipt","expiresAt":${System.currentTimeMillis() + 600_000}}"""
                                    else """{"status":"disabled"}""").toByteArray()
                                first.startsWith("POST /completeDownload") -> {
                                    val receipt = JsonParser.parseString(String(body)).asJsonObject.get("receipt").asString
                                    val status = if (counted.add(receipt)) { count.incrementAndGet(); "counted" } else "already_counted"
                                    """{"status":"$status"}""".toByteArray()
                                }
                                else -> error("Unexpected request $first")
                            }
                            peer.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n").toByteArray() + bytes)
                        }
                    }
                }
            }
        }
        override fun close() {
            socket.close(); connections.forEach { it.close() }; executor.shutdownNow()
            check(executor.awaitTermination(10, TimeUnit.SECONDS))
        }
    }
}
