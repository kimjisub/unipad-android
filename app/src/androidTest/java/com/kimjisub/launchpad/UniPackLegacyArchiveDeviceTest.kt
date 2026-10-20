package com.kimjisub.launchpad

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kimjisub.launchpad.analytics.PackImportSource
import com.kimjisub.launchpad.analytics.UsageAnalytics
import com.kimjisub.launchpad.analytics.UsageEventSink
import com.kimjisub.launchpad.tool.UniPackImporter
import com.kimjisub.launchpad.unipack.LegacyText
import com.kimjisub.launchpad.unipack.UniPack
import com.kimjisub.launchpad.unipack.UniPackFolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** A pack zipped on Korean Windows, with a readme beside its folder, through the real importer. */
@RunWith(AndroidJUnit4::class)
class UniPackLegacyArchiveDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /** 똠 (8C 63) is in CP949 but not in EUC-KR, so this fails if the device only offers EUC-KR. */
    @Test
    fun theDeviceReadsCp949() {
        assertEquals("똠 한글", String(byteArrayOf(0x8C.toByte(), 0x63, 0x20, 0xC7.toByte(), 0xD1.toByte(), 0xB1.toByte(), 0xDB.toByte()), LegacyText.CP949))
    }

    @Test
    fun aKoreanWindowsArchiveWithAReadmeBesideThePackImports() {
        val root = File(context.cacheDir, "legacy-archive-${UUID.randomUUID()}").apply { check(mkdirs()) }
        val workspace = File(root, "workspace").apply { check(mkdir()) }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val cp949 = LegacyText.CP949
            val archive = File(root, "pack.zip").apply {
                writeBytes(zip(cp949, linkedMapOf(
                    ".DS_Store" to byteArrayOf(1),
                    "__MACOSX/._info" to byteArrayOf(2),
                    "바깥/notes.txt" to byteArrayOf(3),
                    "바깥/.hidden" to byteArrayOf(4),
                    "읽어 주세요.txt" to "연주 안내".toByteArray(cp949),
                    "바깥/봄날/info" to "title=봄날 연주\nproducerName=지은이\nbuttonX=8\nbuttonY=8\nchain=2\n".toByteArray(cp949),
                    "바깥/봄날/keySound" to "1 1 1 첫소리.wav 1 2\n2 2 2 둘째.wav\n".toByteArray(cp949),
                    "바깥/봄날/autoPlay" to "t 1 1\nd 100\nt 2 2\n".toByteArray(cp949),
                    "바깥/봄날/sounds/첫소리.wav" to silentWav(),
                    "바깥/봄날/sounds/둘째.wav" to silentWav(),
                )))
            }
            val recorder = Recorder()

            UniPackImporter(context, Uri.fromFile(archive), { workspace }, recorder,
                usage = UsageAnalytics(UsageEventSink { _, _ -> }).packImport(PackImportSource.FILE),
                scope = scope)

            assertEquals("import did not finish", true, recorder.done.await(TIMEOUT_S, TimeUnit.SECONDS))
            assertNull("import failed: ${recorder.error}", recorder.error)
            val pack = UniPackFolder(requireNotNull(recorder.folder)).load().loadDetail()
            assertNull(pack.errorDetail)
            assertEquals("봄날 연주", pack.title)
            assertEquals("지은이", pack.producerName)
            assertEquals(2, pack.soundCount)
            assertEquals("첫소리.wav", pack.soundGet(0, 0, 0)?.file?.name)
            assertEquals("둘째.wav", pack.soundGet(1, 1, 1)?.file?.name)
            assertEquals(silentWav().toList(), pack.soundGet(0, 0, 0)!!.file.readBytes().toList())
            assertEquals(silentWav().toList(), pack.soundGet(1, 1, 1)!!.file.readBytes().toList())
            // Hidden metadata may remain; ignoring it for pack discovery does not require deletion.
            assertEquals(setOf("autoPlay", "info", "keySound", "sounds"), recorder.folder!!.list()!!.filterNot { it.startsWith(".") }.toSet())
        } finally {
            scope.cancel()
            check(root.deleteRecursively()) { "test directory cleanup failed: $root" }
        }
    }

    /** Names in [charset]; java.util.zip sets the UTF-8 flag only for UTF-8, as Korean Windows zips lack it. */
    private fun zip(charset: java.nio.charset.Charset, entries: Map<String, ByteArray>): ByteArray =
        ByteArrayOutputStream().also { out ->
            ZipOutputStream(out, charset).use { zip ->
                for ((name, bytes) in entries) {
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(bytes)
                    zip.closeEntry()
                }
            }
        }.toByteArray()

    private fun silentWav(): ByteArray {
        val dataSize = 1600
        return java.nio.ByteBuffer.allocate(44 + dataSize).order(java.nio.ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + dataSize); put("WAVEfmt ".toByteArray())
            putInt(16); putShort(1); putShort(1); putInt(8000); putInt(16000); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(dataSize)
        }.array()
    }

    private class Recorder : UniPackImporter.OnEventListener {
        val done = CountDownLatch(1)
        @Volatile var folder: File? = null
        @Volatile var error: Throwable? = null
        override fun onImportStart() = Unit
        override fun onImportComplete(folder: File, unipack: UniPack, byteSize: Long) {
            this.folder = folder
            done.countDown()
        }
        override fun onException(throwable: Throwable) {
            error = throwable
            done.countDown()
        }
    }

    private companion object {
        const val TIMEOUT_S = 30L
    }
}
