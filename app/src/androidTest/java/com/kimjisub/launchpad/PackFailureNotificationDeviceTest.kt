package com.kimjisub.launchpad

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.content.ContextWrapper
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import androidx.test.uiautomator.UiDevice
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
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** Real download and import failures must leave one dismissible, finished notification. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29) // ContentResolver.wrap for the local file provider.
class PackFailureNotificationDeviceTest {
    @get:Rule
    val notificationPermission: GrantPermissionRule = GrantPermissionRule.grant(
        *if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.POST_NOTIFICATIONS) else emptyArray()
    )

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private lateinit var root: File
    private lateinit var scope: CoroutineScope
    private val titles = mutableSetOf<String>()
    private var shadeOpened = false

    @Before
    fun setUp() {
        root = File.createTempFile("notification-test", "", context.cacheDir).apply {
            check(delete()); check(mkdir())
        }
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    @After
    fun tearDown() {
        scope.cancel()
        runBlocking { withTimeout(TIMEOUT_MS) { scope.coroutineContext.job.children.toList().joinAll() } }
        shown(titles).forEach { notifications.cancel(it.tag, it.id) }
        if (shadeOpened) device.pressBack()
        check(root.deleteRecursively())
    }

    @Test
    fun missingDownloadShowsFailureWithoutProgressAndCanBeDismissed() {
        val result = OneShotServer { socket ->
            socket.getOutputStream().write(
                "HTTP/1.1 404 Not Found\r\nContent-Length: 7\r\nConnection: close\r\n\r\nmissing".toByteArray()
            )
        }.use { server -> download("Missing pack.zip", "${server.url}/missing.zip", server) }
        assertTrue(result.error is UniPackDownloader.HttpStatusException)
        assertEquals(404, (result.error as UniPackDownloader.HttpStatusException).status)
        checkNotification("Missing pack.zip", "download")
    }

    /**
     * Progress changes many times a second, so Android drops further updates to the progress
     * notification for a while. The failure right after must still replace the progress bar.
     */
    @Test
    fun connectionLostDuringFastDownloadShowsFailureWithoutProgress() {
        val result = OneShotServer { socket ->
            val output = socket.getOutputStream()
            output.write(
                "HTTP/1.1 200 OK\r\nContent-Length: $FAST_BODY_BYTES\r\nConnection: close\r\n\r\n".toByteArray()
            )
            val chunk = ByteArray(FAST_CHUNK_BYTES)
            val end = System.currentTimeMillis() + FAST_SEND_MS
            while (System.currentTimeMillis() < end) {
                output.write(chunk)
                output.flush()
                Thread.sleep(FAST_CHUNK_INTERVAL_MS)
            }
            // Reset instead of a clean close: the connection is lost partway through the body.
            socket.setSoLinger(true, 0)
        }.use { server -> download("Interrupted pack.zip", "${server.url}/interrupted.zip", server) }
        assertTrue(result.error is IOException)
        checkNotification("Interrupted pack.zip", "connection-lost")
    }

    private fun download(title: String, url: String, server: OneShotServer): Result {
        titles += title
        val result = Result()
        UniPackDownloader(context, title, url, { root }, "download", listener = result,
            usage = UsageAnalytics { _, _ -> }.packImport(PackImportSource.STORE), scope = scope)
        awaitFailure(result)
        server.finish()
        return result
    }

    @Test
    fun invalidImportShowsFailureWithoutProgressAndCanBeDismissed() {
        val title = "Invalid pack.zip"
        titles += title
        val invalidZip = File(root, title).apply { writeText("not a ZIP archive") }
        val provider = object : ContentProvider() {
            override fun onCreate() = true
            override fun getType(uri: Uri) = "application/zip"
            override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
                               selectionArgs: Array<out String>?, sortOrder: String?): Cursor =
                MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)).apply {
                    addRow(arrayOf<Any>(title, invalidZip.length()))
                }
            override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor =
                ParcelFileDescriptor.open(invalidZip, ParcelFileDescriptor.MODE_READ_ONLY)
            override fun insert(uri: Uri, values: ContentValues?): Uri = error("read only")
            override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = error("read only")
            override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = error("read only")
        }
        val importContext = object : ContextWrapper(context) {
            override fun getContentResolver(): ContentResolver = ContentResolver.wrap(provider)
        }
        val result = Result()
        UniPackImporter(importContext, Uri.parse("content://notification-test/invalid.zip"),
            { File(root, "workspace").apply { mkdirs() } }, result,
            UsageAnalytics { _, _ -> }.packImport(PackImportSource.FILE), scope)
        awaitFailure(result)
        checkNotification(title, "import")
    }

    private fun awaitFailure(result: Result) {
        assertTrue("failure callback timed out", result.finished.await(TIMEOUT_MS, TimeUnit.MILLISECONDS))
        runBlocking { withTimeout(TIMEOUT_MS) { scope.coroutineContext.job.children.toList().joinAll() } }
        assertNotNull("expected a failed installation", result.error)
    }

    private fun checkNotification(title: String, scenario: String) {
        val shownForTitle = awaitSettledNotifications(title)
        // Capture before asserting so the same test also records the old failure wording.
        val label = InstrumentationRegistry.getArguments().getString("notificationEvidence")
        if (label != null && shownForTitle.isNotEmpty()) {
            shadeOpened = true
            assertTrue(device.openNotification())
            assertTrue("notification shade did not show $title", device.wait(Until.hasObject(By.text(title)), TIMEOUT_MS))
            assertTrue(TestCaptures.screenshot(device, "pack-failure/$label-$scenario.png"))
        }
        assertEquals("notifications for $title: ${shownForTitle.map(::describe)}", 1, shownForTitle.size)
        val notification = shownForTitle.single()
        assertEquals(context.getString(R.string.failed), notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertEquals(0, notification.extras.getInt(Notification.EXTRA_PROGRESS_MAX))
        assertFalse(notification.extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE))
        assertEquals(0, notification.flags and Notification.FLAG_ONGOING_EVENT)
        assertEquals(0, notification.flags and Notification.FLAG_NO_CLEAR)
        assertTrue(notification.flags and Notification.FLAG_AUTO_CANCEL != 0)
    }

    /**
     * The system applies notify() and cancel() asynchronously, so right after the failure callback
     * the shade can still hold the progress notification. Waits until only the failure is shown
     * for [title], then returns whatever is shown so the assertions report what stayed.
     */
    private fun awaitSettledNotifications(title: String): List<Notification> {
        val failed = context.getString(R.string.failed)
        val deadline = System.currentTimeMillis() + TIMEOUT_MS
        while (true) {
            val current = shown(setOf(title)).map { it.notification }
            val settled = current.singleOrNull()?.extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString() == failed
            if (settled || System.currentTimeMillis() > deadline) return current
            Thread.sleep(POLL_INTERVAL_MS)
        }
    }

    private fun shown(titles: Set<String>) = notifications.activeNotifications.filter {
        it.notification.extras.getString(Notification.EXTRA_TITLE) in titles
    }

    private fun describe(notification: Notification) =
        "${notification.extras.getCharSequence(Notification.EXTRA_TEXT)}" +
            " ongoing=${notification.flags and Notification.FLAG_ONGOING_EVENT != 0}" +
            " progressMax=${notification.extras.getInt(Notification.EXTRA_PROGRESS_MAX)}"

    /** Answers one HTTP request on a local port: [respond] runs once the request has been read. */
    private class OneShotServer(respond: (Socket) -> Unit) : AutoCloseable {
        private val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).apply {
            soTimeout = TIMEOUT_MS.toInt()
        }
        @Volatile private var failure: Throwable? = null
        private val responder = thread {
            try {
                server.accept().use { socket ->
                    socket.soTimeout = TIMEOUT_MS.toInt()
                    val input = socket.getInputStream().bufferedReader()
                    while (!input.readLine().isNullOrEmpty()) { /* Read the HTTP request. */ }
                    respond(socket)
                }
            } catch (e: Exception) {
                failure = e
            }
        }
        val url = "http://127.0.0.1:${server.localPort}"

        /** Waits for the response to be sent and fails the test if it could not be. */
        fun finish() {
            responder.join(TIMEOUT_MS)
            assertFalse("HTTP responder did not finish", responder.isAlive)
            failure?.let { throw AssertionError("HTTP responder failed", it) }
        }

        override fun close() = server.close()
    }

    private class Result : UniPackDownloader.Listener, UniPackImporter.OnEventListener {
        val finished = CountDownLatch(1)
        @Volatile var error: Throwable? = null
        override fun onInstallStart() {}
        override fun onGetFileSize(fileSize: Long, contentLength: Long, preKnownFileSize: Long) {}
        override fun onDownloadProgress(percent: Int, downloadedSize: Long, fileSize: Long) {}
        override fun onDownloadProgressPercent(percent: Int, downloadedSize: Long, fileSize: Long) {}
        override fun onImportStart(zip: File) {}
        override fun onImportStart() {}
        override fun onInstallComplete(folder: File, unipack: UniPack) { finished.countDown() }
        override fun onImportComplete(folder: File, unipack: UniPack, byteSize: Long) { finished.countDown() }
        override fun onException(throwable: Throwable) { error = throwable; finished.countDown() }
    }

    private companion object {
        const val TIMEOUT_MS = 15_000L
        const val POLL_INTERVAL_MS = 100L
        // About 6 MB a second for 0.6 s: the percentage changes every few milliseconds.
        const val FAST_BODY_BYTES = 6_400_000
        const val FAST_CHUNK_BYTES = 64 * 1024
        const val FAST_CHUNK_INTERVAL_MS = 10L
        const val FAST_SEND_MS = 600L
    }
}
