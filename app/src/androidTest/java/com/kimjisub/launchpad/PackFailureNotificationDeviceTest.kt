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
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** Real HTTP 404 and invalid archive failures must leave dismissible, finished notifications. */
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
        notifications.activeNotifications.filter {
            it.notification.extras.getString(Notification.EXTRA_TITLE) in titles
        }.forEach { notifications.cancel(it.tag, it.id) }
        device.pressBack()
        check(root.deleteRecursively())
    }

    @Test
    fun missingDownloadShowsFailureWithoutProgressAndCanBeDismissed() {
        val title = "Missing pack.zip"
        titles += title
        val result = Result()
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
            server.soTimeout = TIMEOUT_MS.toInt()
            val responder = thread {
                server.accept().use { socket ->
                    socket.soTimeout = TIMEOUT_MS.toInt()
                    val input = socket.getInputStream().bufferedReader()
                    while (!input.readLine().isNullOrEmpty()) { /* Read the HTTP request. */ }
                    socket.getOutputStream().write(
                        "HTTP/1.1 404 Not Found\r\nContent-Length: 7\r\nConnection: close\r\n\r\nmissing".toByteArray()
                    )
                }
            }
            UniPackDownloader(context, title, "http://127.0.0.1:${server.localPort}/missing.zip",
                { root }, "missing", listener = result,
                usage = UsageAnalytics { _, _ -> }.packImport(PackImportSource.STORE), scope = scope)
            awaitFailure(result)
            responder.join(TIMEOUT_MS)
            assertFalse("HTTP responder did not finish", responder.isAlive)
        }
        assertTrue(result.error is UniPackDownloader.HttpStatusException)
        assertEquals(404, (result.error as UniPackDownloader.HttpStatusException).status)
        checkNotification(title, "download")
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
        val active = notifications.activeNotifications.single {
            it.notification.extras.getString(Notification.EXTRA_TITLE) == title
        }
        val notification = active.notification
        // Capture before asserting so the same test also records the old failure wording.
        val label = InstrumentationRegistry.getArguments().getString("notificationEvidence")
        if (label != null) {
            assertTrue(device.openNotification())
            device.waitForIdle()
            assertTrue(TestCaptures.screenshot(device, "pack-failure/$label-$scenario.png"))
        }
        assertEquals(context.getString(R.string.failed), notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertEquals(0, notification.extras.getInt(Notification.EXTRA_PROGRESS_MAX))
        assertFalse(notification.extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE))
        assertEquals(0, notification.flags and Notification.FLAG_ONGOING_EVENT)
        assertEquals(0, notification.flags and Notification.FLAG_NO_CLEAR)
        assertTrue(notification.flags and Notification.FLAG_AUTO_CANCEL != 0)
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
    }
}
