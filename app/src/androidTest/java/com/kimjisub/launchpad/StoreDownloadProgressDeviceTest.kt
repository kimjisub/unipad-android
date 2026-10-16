package com.kimjisub.launchpad

import android.content.Intent
import android.os.Build
import android.os.SystemClock
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
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
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.kimjisub.launchpad.activity.FBStoreActivity
import com.kimjisub.launchpad.api.file.FileApi
import com.kimjisub.launchpad.manager.PreferenceManager
import com.kimjisub.launchpad.network.FirebaseStoreCatalog
import com.kimjisub.launchpad.network.StoreCatalog
import com.kimjisub.launchpad.network.fb.StoreVO
import com.kimjisub.launchpad.unipack.UniPackFolder
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.loadKoinModules
import org.koin.core.context.unloadKoinModules
import org.koin.dsl.module
import retrofit2.Retrofit
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.util.Random
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** The real store screen downloading from a local server: leaving, unknown sizes and unpacking. */
@RunWith(AndroidJUnit4::class)
class StoreDownloadProgressDeviceTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val id = "store-progress-${UUID.randomUUID()}"
    private val workspace = File(context.getExternalFilesDir(null), "UniPack").apply { mkdirs() }
    private val prefs = PreferenceManager(context)
    private val oldCount = prefs.prevStoreCount
    private val oldPath = prefs.downloadStoragePath
    private var server: PackServer? = null
    private var client: OkHttpClient? = null
    private var override: org.koin.core.module.Module? = null
    private var screen: ActivityScenario<FBStoreActivity>? = null

    @After
    fun restore() {
        server?.release?.countDown()
        screen?.close()
        server?.close()
        client?.let { it.dispatcher.executorService.shutdown(); it.connectionPool.evictAll() }
        override?.let { unloadKoinModules(it) }
        loadKoinModules(module { factory<StoreCatalog> { FirebaseStoreCatalog() } })
        prefs.prevStoreCount = oldCount
        prefs.downloadStoragePath = oldPath
        workspace.listFiles().orEmpty().filter { it.name.startsWith(id) }.forEach { check(it.deleteRecursively()) }
    }

    /** The top arrow is handled like system Back: it closes the detail, then refuses to leave mid-download. */
    @Test fun topArrowWhileDownloadingKeepsTheStoreAndTheDownload() {
        openDetail(PackServer(packZip(files = 1), announceSize = true, holdAfter = HOLD_AFTER))
        startDownload()
        assertTrue("download never reached the server", server!!.held.await(10, TimeUnit.SECONDS))

        compose.onNodeWithTag("store_back").performClick()
        compose.waitUntil(5000) {
            compose.onAllNodesWithTag("store_detail").fetchSemanticsNodes(atLeastOneRootRequired = false).isEmpty()
        }
        assertEquals("the top arrow closed the store instead of the detail", Lifecycle.State.RESUMED, screen!!.state)
        compose.onNodeWithTag("store_back").performClick()
        val refusal = context.getString(R.string.canNotQuitWhileDownloading)
        val shown = device.wait(Until.hasObject(By.text(refusal)), 5000)
        capture("arrow-while-downloading")
        assertTrue("the top arrow left the store without saying a download is running", shown)
        assertEquals(Lifecycle.State.RESUMED, screen!!.state)

        server!!.release.countDown()
        await("the download was dropped after the top arrow") { File(workspace, id).isDirectory }
        assertEquals(TITLE, UniPackFolder(File(workspace, id)).load().title)
    }

    /** A server that sends no Content-Length shows the amount received and an open-ended bar, never "-1%". */
    @Test fun unknownSizeShowsTheReceivedAmountAndAnOpenEndedBar() {
        openDetail(PackServer(packZip(files = 1), announceSize = false, holdAfter = HOLD_AFTER))
        startDownload()
        assertTrue("download never reached the server", server!!.held.await(10, TimeUnit.SECONDS))

        var seen: Snapshot? = null
        val settled = runCatching {
            compose.waitUntil(5000) {
                seen = snapshot()
                seen?.status?.matches(Regex("""\d+\.\d\d MB""")) == true && seen?.bar == ProgressBarRangeInfo.Indeterminate
            }
        }.isSuccess
        capture("unknown-size")
        assertTrue("unknown size showed ${seen?.status?.replace("\n", " | ")} with bar ${seen?.bar}", settled)

        server!!.release.countDown()
        await("the download did not finish") { File(workspace, id).isDirectory }
    }

    /** Unpacking after a full download shows an open-ended bar instead of dropping back to 0%. */
    @Test fun unpackingShowsAnOpenEndedBarInsteadOfStartingOver() {
        openDetail(PackServer(packZip(files = UNPACK_FILES), announceSize = true, holdAfter = null))
        startDownload()

        val importing = context.getString(R.string.importing)
        var seen: Snapshot? = null
        compose.waitUntil(60000) {
            seen = snapshot()
            seen?.status == importing
        }
        capture("unpacking")
        assertEquals("bar while unpacking", ProgressBarRangeInfo.Indeterminate, seen?.bar)
        await("the download did not finish", 60000) { File(workspace, id).isDirectory }
    }

    private fun openDetail(packServer: PackServer) {
        server = packServer
        val http = OkHttpClient.Builder().readTimeout(30, TimeUnit.SECONDS).build().also { client = it }
        val service = Retrofit.Builder().baseUrl(packServer.url).client(http).build().create(FileApi.FileService::class.java)
        // Inside a Koin definition `id` would name the Koin scope.
        val code = id
        override = module {
            single<FileApi.FileService> { service }
            factory<StoreCatalog> {
                object : StoreCatalog {
                    override fun attach(listener: StoreCatalog.Listener) {
                        listener.onAdded(StoreVO(code = code, title = TITLE, producerName = "Device test", URL = "${packServer.url}pack"), code)
                        listener.onCount(1)
                    }
                    override fun detach() {}
                }
            }
        }.also { loadKoinModules(it) }
        prefs.downloadStoragePath = workspace.path
        if (Build.VERSION.SDK_INT >= 33) {
            // The first download asks for notification permission; answering it is not what these tests check.
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
    }

    private fun startDownload() {
        compose.onNode(hasText(context.getString(R.string.download)) and hasAnyAncestor(hasTestTag("store_detail")))
            .assertIsDisplayed().performClick()
    }

    private data class Snapshot(val status: String?, val bar: ProgressBarRangeInfo?)

    /** Status text and bar of the open detail, read in one pass so they belong to the same frame. */
    private fun snapshot(): Snapshot? {
        val root = compose.onAllNodesWithTag("store_detail", useUnmergedTree = true)
            .fetchSemanticsNodes(atLeastOneRootRequired = false).firstOrNull() ?: return null
        var result: Snapshot? = null
        compose.runOnUiThread {
            fun walk(node: SemanticsNode): Sequence<SemanticsNode> = sequenceOf(node) + node.children.asSequence().flatMap(::walk)
            val nodes = walk(root).toList()
            fun tagged(tag: String) = nodes.firstOrNull { it.config.getOrNull(SemanticsProperties.TestTag) == tag }?.config
            result = Snapshot(
                status = tagged("store_download_status")?.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text },
                bar = tagged("store_download_progress")?.getOrNull(SemanticsProperties.ProgressBarRangeInfo),
            )
        }
        return result
    }

    private fun await(message: String, timeoutMs: Long = 15000, condition: () -> Boolean) {
        val until = SystemClock.elapsedRealtime() + timeoutMs
        while (!condition() && SystemClock.elapsedRealtime() < until) SystemClock.sleep(20)
        assertTrue(message, condition())
    }

    private fun capture(name: String) {
        val prefix = InstrumentationRegistry.getArguments().getString("capturePrefix", "fixed")
        assertTrue(TestCaptures.screenshot(device, "store-progress-captures/$prefix/$name.png"))
    }

    /** A valid pack whose extra random files make unpacking take long enough to be seen. */
    private fun packZip(files: Int): ByteArray = ByteArrayOutputStream().also { bytes ->
        val random = Random(1388)
        ZipOutputStream(bytes).use { zip ->
            fun entry(name: String, data: ByteArray) { zip.putNextEntry(ZipEntry(name)); zip.write(data); zip.closeEntry() }
            entry("info", "title=$TITLE\nproducerName=Device test\nbuttonX=8\nbuttonY=8\nchain=1\n".toByteArray())
            entry("keySound", "1 1 1 a.wav\n".toByteArray())
            entry("sounds/a.wav", ByteArray(64 * 1024).also(random::nextBytes))
            repeat(files) { entry("extra/$it.bin", ByteArray(4096).also(random::nextBytes)) }
        }
    }.toByteArray()

    /**
     * Serves [body] once per request. With [holdAfter], it sends that many bytes, waits long enough for
     * the screen to show progress, sends another chunk and then holds the rest until [release].
     */
    private class PackServer(private val body: ByteArray, private val announceSize: Boolean, private val holdAfter: Int?) {
        private val socket = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        val url = "http://127.0.0.1:${socket.localPort}/"
        val held = CountDownLatch(1)
        val release = CountDownLatch(1)
        private val workers = Executors.newCachedThreadPool()

        init {
            workers.submit {
                while (!socket.isClosed) {
                    val connection = try { socket.accept() } catch (_: java.io.IOException) { break }
                    workers.submit {
                        connection.use { peer ->
                            peer.soTimeout = 10000
                            val reader = peer.getInputStream().bufferedReader()
                            reader.readLine()
                            while (!reader.readLine().isNullOrEmpty()) { /* consume headers */ }
                            val output = peer.getOutputStream()
                            val length = if (announceSize) "Content-Length: ${body.size}\r\n" else ""
                            output.write("HTTP/1.1 200 OK\r\n${length}Connection: close\r\n\r\n".toByteArray())
                            var sent = 0
                            if (holdAfter != null) {
                                output.write(body, 0, holdAfter); output.flush()
                                SystemClock.sleep(200)
                                output.write(body, holdAfter, holdAfter); output.flush()
                                sent = holdAfter * 2
                                held.countDown()
                                check(release.await(30, TimeUnit.SECONDS))
                            }
                            output.write(body, sent, body.size - sent)
                            output.flush()
                        }
                    }
                }
            }
        }

        fun close() {
            socket.close()
            workers.shutdownNow()
            workers.awaitTermination(15, TimeUnit.SECONDS)
        }
    }

    private companion object {
        const val TITLE = "Progress fixture"
        const val HOLD_AFTER = 8 * 1024
        const val UNPACK_FILES = 3000
    }
}
