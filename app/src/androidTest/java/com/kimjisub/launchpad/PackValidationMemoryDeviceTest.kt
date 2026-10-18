package com.kimjisub.launchpad

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.net.Uri
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.kimjisub.launchpad.analytics.PackImportSource
import com.kimjisub.launchpad.analytics.UsageAnalytics
import com.kimjisub.launchpad.tool.UniPackDownloader
import com.kimjisub.launchpad.tool.UniPackImporter
import com.kimjisub.launchpad.unipack.UniPack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.concurrent.thread

/** A real oversized line, rather than an injected error, through both installation entry points. */
@RunWith(AndroidJUnit4::class)
class PackValidationMemoryDeviceTest {
    @get:Rule
    val notificationPermission: GrantPermissionRule = GrantPermissionRule.grant(
        *if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.POST_NOTIFICATIONS) else emptyArray()
    )
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)

    @Test
    fun oversizedDownloadedKeySoundFailsWithoutCrashingOrLeavingFiles() = withPack { archive, workspace, scope ->
        val label = InstrumentationRegistry.getArguments().getString("memoryEvidence")
        if (label != null) assertTrue(TestCaptures.screenshot(device, "pack-memory/$label-start.png"))
        val result = Result()
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
            server.soTimeout = TIMEOUT_MS.toInt()
            var serverFailure: Throwable? = null
            val responder = thread(name = "large-pack-response") {
                try {
                    server.accept().use { socket ->
                        socket.soTimeout = TIMEOUT_MS.toInt()
                        val request = socket.getInputStream().bufferedReader()
                        while (!request.readLine().isNullOrEmpty()) { /* Consume headers. */ }
                        socket.getOutputStream().let { output ->
                            output.write("HTTP/1.1 200 OK\r\nContent-Length: ${archive.length()}\r\nConnection: close\r\n\r\n".toByteArray())
                            archive.inputStream().use { it.copyTo(output) }
                        }
                    }
                } catch (error: Exception) { serverFailure = error }
            }
            UniPackDownloader(context, TITLE, "http://127.0.0.1:${server.localPort}/pack.zip",
                { workspace }, "large-pack", listener = result,
                usage = UsageAnalytics { _, _ -> }.packImport(PackImportSource.STORE), scope = scope)
            assertTrue("download did not finish", result.finished.await(TIMEOUT_MS, TimeUnit.MILLISECONDS))
            responder.join(TIMEOUT_MS)
            assertFalse("HTTP response did not finish", responder.isAlive)
            serverFailure?.let { throw AssertionError("HTTP response failed", it) }
        }
        awaitCleanup(scope)
        assertTrue("${result.error}", result.error is UniPackDownloader.UniPackCriticalErrorException)
        assertTrue("${result.error?.cause}", result.error?.cause is OutOfMemoryError)
        assertNull(result.folder)
        assertEquals(emptyList<String>(), workspace.list()!!.toList())
        val manager = context.getSystemService(NotificationManager::class.java)
        val end = System.currentTimeMillis() + TIMEOUT_MS
        var failure: Notification?
        do {
            failure = manager.activeNotifications.map { it.notification }.singleOrNull {
                it.extras.getString(Notification.EXTRA_TITLE) == TITLE &&
                    it.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() == context.getString(R.string.failed)
            }
            if (failure == null) Thread.sleep(100)
        } while (failure == null && System.currentTimeMillis() < end)
        assertNotNull("failure notification missing", failure)
        assertEquals(0, failure!!.flags and Notification.FLAG_ONGOING_EVENT)
        if (label != null) {
            assertTrue(device.openNotification())
            assertTrue(device.wait(Until.hasObject(By.text(TITLE)), TIMEOUT_MS))
            assertTrue(TestCaptures.screenshot(device, "pack-memory/$label-failed.png"))
            device.pressBack()
        }
        println("400 MiB keySound: failure callback, finished failure notification, empty workspace, process survived")
    }

    @Test
    fun fileImportKeepsItsMetadataOnlyCheckForTheSameLargeKeySound() = withPack { archive, workspace, scope ->
        val result = Result()
        UniPackImporter(context, Uri.fromFile(archive), { workspace }, result,
            UsageAnalytics { _, _ -> }.packImport(PackImportSource.FILE), scope)
        assertTrue("import did not finish", result.finished.await(TIMEOUT_MS, TimeUnit.MILLISECONDS))
        awaitCleanup(scope)
        assertNull(result.error)
        assertEquals(KEY_SOUND_BYTES, File(requireNotNull(result.folder), "keySound").length())
        assertEquals(1, workspace.list()!!.size)
        println("400 MiB keySound: file import retained its metadata-only validation, no staging files")
    }

    private fun withPack(verify: (File, File, CoroutineScope) -> Unit) {
        val root = File(context.cacheDir, "pack-memory").apply { deleteRecursively(); check(mkdirs()) }
        val workspace = File(root, "workspace").apply { check(mkdir()) }
        val archive = File(root, "large-pack.zip")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            ZipOutputStream(archive.outputStream().buffered()).use { zip ->
                zip.putNextEntry(ZipEntry("info"))
                zip.write("title=Large pack\nproducerName=Tester\nbuttonX=8\nbuttonY=8\nchain=1\n".toByteArray())
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("keySound"))
                val chunk = ByteArray(1024 * 1024) { 'a'.code.toByte() }
                repeat(400) { zip.write(chunk) }
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("sounds/a.wav"))
                zip.write(byteArrayOf(0))
                zip.closeEntry()
            }
            println("Fixture: keySound=$KEY_SOUND_BYTES bytes in one line, zip=${archive.length()} bytes")
            verify(archive, workspace, scope)
        } finally {
            scope.cancel()
            awaitCleanup(scope)
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.activeNotifications.filter {
                it.notification.extras.getString(Notification.EXTRA_TITLE) in setOf(TITLE, archive.name)
            }.forEach { manager.cancel(it.id) }
            check(root.deleteRecursively())
        }
    }

    private fun awaitCleanup(scope: CoroutineScope) = runBlocking {
        withTimeout(TIMEOUT_MS) { scope.coroutineContext.job.children.toList().joinAll() }
    }

    private class Result : UniPackDownloader.Listener, UniPackImporter.OnEventListener {
        val finished = CountDownLatch(1)
        @Volatile var error: Throwable? = null
        @Volatile var folder: File? = null
        override fun onInstallStart() {}
        override fun onGetFileSize(fileSize: Long, contentLength: Long, preKnownFileSize: Long) {}
        override fun onDownloadProgress(percent: Int, downloadedSize: Long, fileSize: Long) {}
        override fun onDownloadProgressPercent(percent: Int, downloadedSize: Long, fileSize: Long) {}
        override fun onImportStart(zip: File) {}
        override fun onImportStart() {}
        override fun onInstallComplete(folder: File, unipack: UniPack) { this.folder = folder; finished.countDown() }
        override fun onImportComplete(folder: File, unipack: UniPack, byteSize: Long) { this.folder = folder; finished.countDown() }
        override fun onException(throwable: Throwable) { error = throwable; finished.countDown() }
    }

    private companion object {
        const val TIMEOUT_MS = 120_000L
        const val KEY_SOUND_BYTES = 400L * 1024 * 1024
        const val TITLE = "Large pack.zip"
    }
}
