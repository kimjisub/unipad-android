package com.kimjisub.launchpad

import android.app.Notification
import android.app.NotificationManager
import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.content.ContextWrapper
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.OpenableColumns
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.kimjisub.launchpad.tool.UniPackImporter
import com.kimjisub.launchpad.unipack.UniPack
import com.kimjisub.launchpad.unipack.UniPackFolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Real importer, Android pipes, ZIP extraction and pack reader; only the input is controlled. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29) // ContentResolver.wrap; this does not change the app's minSdk.
class UniPackImportOverlapDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun overlappingImportsKeepBothResultsAndExistingPack() {
        assertNotEquals(Looper.getMainLooper(), Looper.myLooper())
        val base = instrumentation.targetContext
        val name = "overlap-${UUID.randomUUID()}"
        val root = File(base.cacheDir, name).apply { check(mkdir()) }
        val workspace = File(root, "workspace").apply { check(mkdir()) }
        val cache = File(root, "cache").apply { check(mkdir()) }
        val scopes = List(2) { CoroutineScope(SupervisorJob() + Dispatchers.IO) }
        val gates = listOf(InputGate("first", packFiles("First", 800)), InputGate("second", packFiles("Second", 1600)))
        val provider = InputProvider("$name.zip", gates)
        val context = object : ContextWrapper(base) {
            override fun getContentResolver(): ContentResolver = ContentResolver.wrap(provider)
            override fun getCacheDir(): File = cache
        }
        val recorders = listOf(Recorder("first"), Recorder("second"))
        try {
            val existing = File(workspace, name)
            packFiles("Existing", 2400).forEach { (path, bytes) ->
                File(existing, path).apply { parentFile!!.mkdirs(); writeBytes(bytes) }
            }
            val before = hashes(existing)
            evidence("existing-before", before.toString())
            gates.forEach { evidence("${it.id}-original", it.expected.toString()) }

            // Both constructors run before their Main callbacks. On pre-#112 code this selects
            // the same unclaimed path twice. This runnable never waits or blocks the main thread.
            instrumentation.runOnMainSync {
                gates.indices.forEach { index ->
                    evidence(gates[index].id, "constructed")
                    UniPackImporter(context, Uri.parse("content://overlap/${gates[index].id}"),
                        workspace, recorders[index], scopes[index])
                }
            }
            gates.forEach { it.awaitInput() }
            assertTrue(gates.all { it.release.count == 1L })
            assertTrue(recorders.all { it.done.count == 1L })
            evidence("both", "inputs reached; neither input released; neither import completed")

            gates[0].open()
            recorders[0].awaitComplete()
            finish(scopes[0])
            val first = requireNotNull(recorders[0].folder)
            assertEquals(gates[0].expected, hashes(first))
            evidence("first-before-second-release", hashes(first).toString())
            gates[1].open()
            recorders[1].awaitComplete()
            finish(scopes[1])
            val second = requireNotNull(recorders[1].folder)
            // Emit all post-state evidence even on the old source, before the regression assertion.
            evidence("first-final", "${first.name} ${hashes(first)}")
            evidence("second-final", "${second.name} ${hashes(second)}")
            evidence("existing-after", hashes(existing).toString())
            assertEquals(before, hashes(existing))
            assertNotEquals("overlapping imports must claim different folders", first, second)
            assertEquals(gates[0].expected, hashes(first))
            assertEquals(gates[1].expected, hashes(second))
            assertEquals(listOf(name, first.name, second.name).sorted(), workspace.list()!!.sorted())
            assertTrue("temporary ZIPs cleaned", cache.list()!!.isEmpty())
            listOf(first to "First", second to "Second", existing to "Existing").forEach { (folder, title) ->
                val pack = UniPackFolder(folder).load().loadDetail()
                assertFalse(pack.errorDetail, pack.criticalError)
                assertEquals(title, pack.title)
                assertEquals(1, pack.soundCount)
            }
            evidence("result", "PASS")
        } finally {
            // Release pipes before waiting for IO jobs, including when an assertion failed.
            gates.forEach { it.open() }
            try {
                scopes.forEach { finish(it) }
            } finally {
                scopes.forEach { it.cancel() }
                provider.close()
                val notifications = base.getSystemService(NotificationManager::class.java)
                notifications.activeNotifications.filter {
                    it.notification.extras.getString(Notification.EXTRA_TITLE) == "$name.zip"
                }.forEach { notifications.cancel(it.tag, it.id) }
                check(root.deleteRecursively()) { "test directory cleanup failed: $root" }
                evidence("cleanup", "test directory removed=${!root.exists()}")
            }
        }
    }

    private fun finish(scope: CoroutineScope) = runBlocking {
        withTimeout(TIMEOUT_MS) { scope.coroutineContext[ kotlinx.coroutines.Job ]!!.children.toList().joinAll() }
    }

    private inner class Recorder(val id: String) : UniPackImporter.OnEventListener {
        val done = CountDownLatch(1)
        @Volatile var folder: File? = null
        @Volatile var error: Throwable? = null
        override fun onImportStart() { evidence(id, "started on main=${Looper.myLooper() == Looper.getMainLooper()}") }
        override fun onImportComplete(folder: File, unipack: UniPack) {
            this.folder = folder
            evidence(id, "completed folder=${folder.name} title=${unipack.title}")
            done.countDown()
        }
        override fun onException(throwable: Throwable) {
            error = throwable
            evidence(id, "failed $throwable")
            done.countDown()
        }
        fun awaitComplete() {
            assertTrue("$id completion timed out", done.await(TIMEOUT_MS, TimeUnit.MILLISECONDS))
            assertNull("$id unexpected import failure: $error", error)
        }
    }

    private inner class InputGate(val id: String, files: Map<String, ByteArray>) {
        val expected = files.mapValues { sha256(it.value) }.toSortedMap()
        val bytes = ByteArrayOutputStream().also { output ->
            ZipOutputStream(output).use { zip ->
                files.forEach { (path, data) ->
                    zip.putNextEntry(ZipEntry(path).apply { time = 0 })
                    zip.write(data)
                    zip.closeEntry()
                }
            }
        }.toByteArray()
        val reached = CountDownLatch(1)
        val release = CountDownLatch(1)
        fun awaitInput() { assertTrue("$id input not reached", reached.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)) }
        fun open() {
            if (release.count != 0L) { evidence(id, "release requested"); release.countDown() }
        }
    }

    private inner class InputProvider(val displayName: String, val gates: List<InputGate>) : ContentProvider() {
        private val writers = Executors.newFixedThreadPool(2)
        override fun onCreate() = true
        override fun getType(uri: Uri) = "application/zip"
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
                           selectionArgs: Array<out String>?, sortOrder: String?): Cursor =
            MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)).apply {
                addRow(arrayOf<Any>(displayName, gates.first { it.id == uri.lastPathSegment }.bytes.size))
            }
        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
            check(mode == "r")
            val gate = gates.first { it.id == uri.lastPathSegment }
            evidence(gate.id, "input reached via openFile on main=${Looper.myLooper() == Looper.getMainLooper()}")
            val pipe = ParcelFileDescriptor.createPipe()
            writers.submit {
                ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]).use { output ->
                    evidence(gate.id, "waiting; zero input bytes supplied")
                    gate.reached.countDown()
                    check(gate.release.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)) { "input release timed out" }
                    evidence(gate.id, "released; supplying ZIP sha256=${sha256(gate.bytes)}")
                    output.write(gate.bytes)
                }
            }
            return pipe[0]
        }
        fun close() {
            writers.shutdown()
            check(writers.awaitTermination(TIMEOUT_MS, TimeUnit.MILLISECONDS)) { "input writers did not stop" }
        }
        override fun insert(uri: Uri, values: ContentValues?): Uri = error("read only")
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = error("read only")
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = error("read only")
    }

    private fun evidence(id: String, event: String) {
        instrumentation.sendStatus(2, Bundle().apply {
            putString("stream", "\noverlap uptimeNs=${SystemClock.elapsedRealtimeNanos()} operation=$id $event\n")
        })
    }

    private fun hashes(folder: File): Map<String, String> = folder.walkTopDown().filter { it.isFile }
        .associate { it.relativeTo(folder).invariantSeparatorsPath to sha256(it.readBytes()) }.toSortedMap()

    private fun packFiles(title: String, samples: Int): Map<String, ByteArray> = mapOf(
        "info" to "title=$title\nproducerName=Device Test\nbuttonX=8\nbuttonY=8\nchain=1\n".toByteArray(),
        "keySound" to "1 1 1 silence.wav\n".toByteArray(),
        "sounds/silence.wav" to ByteBuffer.allocate(44 + samples * 2).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + samples * 2); put("WAVEfmt ".toByteArray())
            putInt(16); putShort(1); putShort(1); putInt(8000); putInt(16000); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(samples * 2)
        }.array(),
    )

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it) }

    private companion object { const val TIMEOUT_MS = 15_000L }
}
