package com.kimjisub.launchpad

import android.app.NotificationManager
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.Configurator
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.kimjisub.launchpad.activity.MainActivity
import com.kimjisub.launchpad.activity.FBStoreActivity
import com.kimjisub.launchpad.analytics.PackImportSource
import com.kimjisub.launchpad.analytics.UsageAnalytics
import com.kimjisub.launchpad.api.file.FileApi
import com.kimjisub.launchpad.manager.PreferenceManager
import com.kimjisub.launchpad.network.StoreCatalog
import com.kimjisub.launchpad.network.fb.StoreVO
import com.kimjisub.launchpad.tool.UniPackDownloader
import com.kimjisub.launchpad.unipack.UniPack
import com.kimjisub.launchpad.unipack.UniPackFolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.core.context.loadKoinModules
import org.koin.core.context.unloadKoinModules
import org.koin.dsl.module
import retrofit2.Call
import retrofit2.Retrofit
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.Random
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Real store lifecycle, filesystem and OkHttp socket; only the catalogue and server are local. */
@RunWith(AndroidJUnit4::class)
class StoreCancellationDeviceTest {
    @Test fun leavingStoreStopsReadCleansPartialAndPreservesOtherPacks() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        val configurator = Configurator.getInstance()
        val oldIdle = configurator.waitForIdleTimeout
        configurator.waitForIdleTimeout = 0
        val id = "jis294-${UUID.randomUUID()}"
        val workspace = File(context.getExternalFilesDir(null), "UniPack").apply { mkdirs() }
        val existing = File(workspace, "$id-existing").apply { mkdirs() }
        files("Existing").forEach { (path, bytes) -> File(existing, path).apply { parentFile!!.mkdirs(); writeBytes(bytes) } }
        val before = hashes(existing)
        val server = LocalServer(zip("Retried"))
        val client = OkHttpClient.Builder().readTimeout(30, TimeUnit.SECONDS).build()
        val service = Retrofit.Builder().baseUrl(server.url).client(client).build().create(FileApi.FileService::class.java)
        val cancelled = CountDownLatch(1)
        val override = module {
            single<FileApi.FileService> {
                object : FileApi.FileService {
                    override fun download(url: String): Call<okhttp3.ResponseBody> {
                        val call = service.download(url)
                        return object : Call<okhttp3.ResponseBody> by call {
                            override fun cancel() { call.cancel(); if (url.endsWith("/partial")) cancelled.countDown() }
                        }
                    }
                }
            }
            single<StoreCatalog> {
                object : StoreCatalog {
                    override fun attach(listener: StoreCatalog.Listener) {
                        listener.onAdded(StoreVO(code = id, title = "Cancellation fixture", producerName = "Device test", URL = "${server.url}partial"), id)
                        listener.onCount(1)
                    }
                    override fun detach() {}
                }
            }
        }
        val prefs = PreferenceManager(context)
        val oldCount = prefs.prevStoreCount
        val oldPath = prefs.downloadStoragePath
        prefs.downloadStoragePath = workspace.path
        val otherScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        var main: ActivityScenario<MainActivity>? = null
        var screen: ActivityScenario<FBStoreActivity>? = null
        loadKoinModules(override)
        try {
            main = ActivityScenario.launch(Intent(context, MainActivity::class.java))
            screen = ActivityScenario.launch(Intent(context, FBStoreActivity::class.java))
            val row = device.wait(Until.findObject(By.text("Cancellation fixture")), 10000)
            assertNotNull("fixture not shown", row)
            row!!.click()
            fun startDownloadWithoutChangingNotificationPermission() {
                var notificationDialogExpected = false
                screen!!.onActivity { activity ->
                    notificationDialogExpected = android.os.Build.VERSION.SDK_INT >= 33 &&
                        context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED &&
                        !activity.shouldShowRequestPermissionRationale(android.Manifest.permission.POST_NOTIFICATIONS)
                }
                device.wait(Until.findObject(By.text(context.getString(R.string.download))), 10000)!!.click()
                if (notificationDialogExpected) {
                    assertNotNull(device.wait(Until.findObject(By.res("com.android.permissioncontroller", "permission_deny_button")), 10000))
                    // Dismiss without choosing allow/deny, so subsequent tests retain their
                    // original permission state and may still request it on a real download.
                    device.executeShellCommand("input keyevent KEYCODE_BACK")
                    assertTrue(device.wait(Until.gone(By.res("com.android.permissioncontroller", "permission_deny_button")), 2000))
                    assertEquals(android.content.pm.PackageManager.PERMISSION_DENIED,
                        context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS))
                    screen!!.onActivity { activity ->
                        assertFalse("dismissing the prompt must not record a denial",
                            activity.shouldShowRequestPermissionRationale(android.Manifest.permission.POST_NOTIFICATIONS))
                    }
                }
            }
            startDownloadWithoutChangingNotificationPermission()
            val partial = File(workspace, "$id.zip")
            await("partial chunk not written") { partial.length() == 4096L }
            capture("downloading")
            evidence("partial-before=${partial.length()} existing=$before")
            val other = Recorder()
            UniPackDownloader(context, "$id-other", "${server.url}other", workspace, "$id-other", listener = other,
                usage = GlobalContext.get().get<UsageAnalytics>().packImport(PackImportSource.STORE), scope = otherScope)
            finish(otherScope)
            assertNull(other.error)
            val otherFolder = requireNotNull(other.folder)
            val otherBefore = hashes(otherFolder)
            // Closing the real Activity destroys the same lifecycleScope as the store's top arrow.
            screen.close()
            screen = null
            val start = SystemClock.elapsedRealtime()
            val cancelledPromptly = cancelled.await(1, TimeUnit.SECONDS)
            evidence("cancelled-before-server-error=$cancelledPromptly")
            server.breakRead.countDown() // EOF/IO error after the screen has cancelled its scope.
            capture("after-cancel")
            await("cancelled ZIP remained after read error") { !partial.exists() }
            evidence("cleanup-ms=${SystemClock.elapsedRealtime() - start} partial-after=${partial.exists()} cancelled=${cancelled.count}")
            assertTrue("cancellation did not reach the real HTTP call", cancelledPromptly)
            assertEquals(before, hashes(existing))
            assertEquals(otherBefore, hashes(otherFolder))
            // The local server supplies a complete ZIP on the next request from the real screen.
            screen = ActivityScenario.launch(Intent(context, FBStoreActivity::class.java))
            device.wait(Until.findObject(By.text("Cancellation fixture")), 10000)!!.click()
            startDownloadWithoutChangingNotificationPermission()
            assertNotNull(device.wait(Until.findObject(By.text(context.getString(R.string.downloaded))), 10000))
            val retried = File(workspace, id)
            assertEquals("Retried", UniPackFolder(retried).load().title)
            assertEquals(hashesFrom(files("Retried")), hashes(retried))
            assertEquals(before, hashes(existing))
            assertEquals(otherBefore, hashes(otherFolder))
            assertFalse(partial.exists())
            capture("retried")
            evidence("PASS retried=${hashes(retried)} other=${hashes(otherFolder)} existing=${hashes(existing)}")
        } finally {
            evidence("final-files=" + workspace.listFiles().orEmpty().filter { it.name.startsWith(id) }.map { "${it.name}:${it.length()}" })
            server.breakRead.countDown()
            screen?.close()
            main?.close()
            server.close()
            otherScope.cancel()
            finish(otherScope)
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
            unloadKoinModules(override)
            prefs.prevStoreCount = oldCount
            prefs.downloadStoragePath = oldPath
            configurator.waitForIdleTimeout = oldIdle
            workspace.listFiles().orEmpty().filter { it.name.startsWith(id) }.forEach { check(it.deleteRecursively()) }
            context.getSystemService(NotificationManager::class.java).activeNotifications.filter {
                it.notification.extras.getString(android.app.Notification.EXTRA_TITLE) in listOf("Cancellation fixture", "$id-other")
            }.forEach { context.getSystemService(NotificationManager::class.java).cancel(it.tag, it.id) }
        }
    }

    private class LocalServer(val zip: ByteArray) {
        private val socket = ServerSocket(0, 8, java.net.InetAddress.getByName("127.0.0.1"))
        val url = "http://127.0.0.1:${socket.localPort}/"
        val breakRead = CountDownLatch(1)
        private val workers = Executors.newCachedThreadPool()
        private val connections = CopyOnWriteArrayList<Socket>()
        private val partialSent = java.util.concurrent.atomic.AtomicBoolean(false)
        init {
            workers.submit {
                while (!socket.isClosed) {
                    val connection = try { socket.accept() } catch (_: java.io.IOException) { break }
                    connections += connection
                    workers.submit {
                        connection.use { peer ->
                            peer.soTimeout = 10000
                            val reader = peer.getInputStream().bufferedReader()
                            val request = reader.readLine()
                            while (!reader.readLine().isNullOrEmpty()) { /* consume headers */ }
                            val partial = request.contains("/partial") && partialSent.compareAndSet(false, true)
                            val output = peer.getOutputStream()
                            output.write("HTTP/1.1 200 OK\r\nContent-Length: ${zip.size}\r\nConnection: close\r\n\r\n".toByteArray())
                            output.flush()
                            SystemClock.sleep(30)
                            output.write(zip, 0, if (partial) 4096 else zip.size)
                            output.flush()
                            if (partial) check(breakRead.await(15, TimeUnit.SECONDS))
                        }
                    }
                }
            }
        }
        fun close() {
            socket.close()
            connections.forEach { runCatching { it.close() } }
            workers.shutdown()
            check(workers.awaitTermination(15, TimeUnit.SECONDS))
        }
    }

    private class Recorder : UniPackDownloader.Listener {
        @Volatile var folder: File? = null
        @Volatile var error: Throwable? = null
        override fun onInstallStart() {}
        override fun onGetFileSize(fileSize: Long, contentLength: Long, preKnownFileSize: Long) {}
        override fun onDownloadProgress(percent: Int, downloadedSize: Long, fileSize: Long) {}
        override fun onDownloadProgressPercent(percent: Int, downloadedSize: Long, fileSize: Long) {}
        override fun onImportStart(zip: File) {}
        override fun onInstallComplete(folder: File, unipack: UniPack) { this.folder = folder }
        override fun onException(throwable: Throwable) { error = throwable }
    }
    private fun finish(scope: CoroutineScope) = runBlocking { withTimeout(15000) { scope.coroutineContext.job.children.toList().joinAll() } }
    private fun await(message: String, condition: () -> Boolean) {
        val until = SystemClock.elapsedRealtime() + 2000
        while (!condition() && SystemClock.elapsedRealtime() < until) SystemClock.sleep(10)
        assertTrue(message, condition())
    }
    private fun capture(name: String) {
        val prefix = InstrumentationRegistry.getArguments().getString("capturePrefix", "fixed")
        val folder = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "jis294-captures/$prefix").apply { mkdirs() }
        assertTrue(UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).takeScreenshot(File(folder, "$name.png")))
    }
    private fun evidence(text: String) = InstrumentationRegistry.getInstrumentation().sendStatus(2, Bundle().apply { putString("stream", "\njis294 $text\n") })
    private fun files(title: String): Map<String, ByteArray> = mapOf(
        "info" to "title=$title\nproducerName=Device test\nbuttonX=8\nbuttonY=8\nchain=1\n".toByteArray(),
        "keySound" to "1 1 1 silence.wav\n".toByteArray(),
        "sounds/silence.wav" to ByteBuffer.allocate(44 + 3200).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + 3200); put("WAVEfmt ".toByteArray())
            putInt(16); putShort(1); putShort(1); putInt(8000); putInt(16000); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(3200)
        }.array(),
        "fixture.bin" to ByteArray(16384).also { Random(294).nextBytes(it) },
    )
    private fun zip(title: String): ByteArray = ByteArrayOutputStream().also { bytes ->
        ZipOutputStream(bytes).use { zip -> files(title).forEach { (name, data) -> zip.putNextEntry(ZipEntry(name)); zip.write(data); zip.closeEntry() } }
    }.toByteArray()
    private fun hashes(folder: File): Map<String, String> = hashesFrom(folder.walkTopDown().filter { it.isFile }.associate { it.relativeTo(folder).invariantSeparatorsPath to it.readBytes() })
    private fun hashesFrom(files: Map<String, ByteArray>) = files.mapValues { (_, bytes) -> MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) } }
}
