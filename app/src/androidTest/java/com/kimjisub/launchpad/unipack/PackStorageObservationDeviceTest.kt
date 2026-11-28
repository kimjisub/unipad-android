package com.kimjisub.launchpad.unipack

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.documentfile.provider.DocumentFile
import androidx.test.platform.app.InstrumentationRegistry
import com.kimjisub.launchpad.TestCaptures
import com.kimjisub.launchpad.audio.OboeAudioEngine
import com.kimjisub.launchpad.manager.PackArchive
import com.kimjisub.launchpad.manager.FileManager
import com.kimjisub.launchpad.tool.SafMigrationHelper
import kotlinx.coroutines.runBlocking
import com.kimjisub.launchpad.unipack.conformance.*
import kotlinx.serialization.json.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Observes the same archive on both storage locations, including every original byte. */
@RunWith(AndroidJUnit4::class)
class PackStorageObservationDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test fun internalStorage() = observe("internal", context.filesDir)
    @Test fun defaultExternalStorage() = observe("external", requireNotNull(context.getExternalFilesDir(null)))

    @Test fun moveDeleteAndReimportAcrossStorageLocations() {
        val locations = listOf(context.filesDir, requireNotNull(context.getExternalFilesDir(null)))
        val roots = locations.map { File(it, "pack-storage-lifecycle").apply { deleteRecursively(); check(mkdirs()) } }
        try {
            for (direction in 0..1) repeat(2) {
                val source = File(roots[direction], "staged").apply { check(mkdirs()) }
                val target = File(roots[1 - direction], "installed")
                val entries = linkedMapOf(
                    "Pack/info" to "title=Storage\nproducerName=Test\nbuttonX=8\nbuttonY=8\nchain=1\n",
                    "Pack/keySound" to "1 1 1 end.wav\n1 1 2 END.wav\n1 1 3 café.wav\n1 1 4 cafe\u0301.wav\n",
                    "Pack/sounds/end.wav" to "lower", "Pack/sounds/END.wav" to "upper",
                    "Pack/sounds/café.wav" to "composed", "Pack/sounds/cafe\u0301.wav" to "decomposed",
                    "Pack/keyLED/1 1 1 1 café" to "on 1 1 auto 5\n",
                    "Pack/keyLED/1 1 1 1 cafe\u0301" to "on 1 1 auto 6\n",
                    "Pack/autoPlay" to "delay 10\n", "Pack/AUTOPLAY" to "delay 20\n")
                val archive = File(roots[direction], "input.zip").apply {
                    outputStream().use { output -> ZipOutputStream(output).use { zip ->
                        for ((path, text) in entries) {
                            zip.putNextEntry(ZipEntry(path)); zip.write(text.toByteArray()); zip.closeEntry()
                        }
                    } }
                }
                PackArchive.extract(archive, source)
                FileManager.removeDoubleFolder(source.path)
                FileManager.moveDirectory(source, target)
                val pack = checkedLifecyclePack(target)
                val helper = SafMigrationHelper(context)
                val backups = File(roots[1 - direction], "backups").apply { deleteRecursively(); check(mkdirs()) }
                val exported = runBlocking {
                    helper.transferFileToSafZip(listOf(target), DocumentFile.fromFile(backups), false) { _, _, _ -> }
                }
                assertEquals(1, exported.transferred)
                assertEquals(0, exported.failed)
                val workspace = File(roots[direction], "restored").apply { deleteRecursively(); check(mkdirs()) }
                val restored = runBlocking {
                    helper.transferSafZipToFile(listOf(DocumentFile.fromFile(File(backups, "installed.zip"))),
                        workspace, false) { _, _, _ -> }
                }
                assertEquals(1, restored.transferred)
                assertEquals(0, restored.failed)
                val restoredRoot = File(workspace, "installed")
                FileManager.removeDoubleFolder(restoredRoot.path)
                assertTrue(checkedLifecyclePack(restoredRoot).delete())
                assertTrue(pack.delete())
                assertTrue(!target.exists())
            }
        } finally { roots.forEach { it.deleteRecursively() } }
    }

    private fun checkedLifecyclePack(root: File): UniPackFolder {
        val pack = UniPackFolder(root)
        pack.load().loadDetail()
        assertEquals(null, pack.errorDetail)
        assertEquals(listOf("lower", "upper", "composed", "decomposed"), (0..3).map {
            pack.soundTable!![0][0][it]!!.first().file.readText()
        })
        assertEquals(listOf(5, 6), pack.ledAnimationTable!![0][0][0]!!.map {
            (it.ledEvents.first() as com.kimjisub.launchpad.unipack.struct.LedAnimation.LedEvent.On).velocity
        })
        assertEquals("delay 10\n", pack.autoPlayFile!!.readText())
        assertTrue(root.walkTopDown().any { it.isFile && it.readText() == "delay 20\n" })
        return pack
    }

    private fun observe(location: String, storage: File) {
        val assets = instrumentation.context.assets
        val bytes = assets.open("unipack-conformance/corpus.json").use { it.readBytes() }
        val digest = assets.open("unipack-conformance/corpus.sha256").bufferedReader().use { it.readText() }
        val corpus = parseCorpus(bytes, digest)
        val cases = Json.parseToJsonElement(bytes.decodeToString()).jsonObject.getValue("cases").jsonArray
            .map { it.jsonObject }.filter { it.getValue("id").jsonPrimitive.content in IDS }
        assertEquals(13, cases.size)
        val root = File(storage, "pack-storage-observation").apply { deleteRecursively(); check(mkdirs()) }
        val failures = mutableListOf<String>()
        try {
            val filesystem = identityFileSystem(root)
            for (case in cases) {
                val input = case.toCase()
                verifyFingerprint(input, corpus)
                val archiveBytes = ByteArrayOutputStream().also { output ->
                    ZipOutputStream(output).use { zip ->
                        for (file in input.files) {
                            zip.putNextEntry(ZipEntry(file.path)); zip.write(file.bytes(corpus)); zip.closeEntry()
                        }
                    }
                }.toByteArray()
                val archive = File(root, "input.zip").apply { writeBytes(archiveBytes) }
                val packRoot = File(root, "pack").apply { deleteRecursively(); check(mkdirs()) }
                PackArchive.extract(archive, packRoot)
                val pack = UniPackFolder(packRoot).load().loadDetail()
                val observation = observeSoundIdentity(archiveBytes, packRoot, pack, OboeAudioEngine::decodeOnly)
                // Case-only fixtures express names. Translate their expectation AFTER observing,
                // using original ZIP input bytes, never a stored alias or the parser's selection.
                val identityCase = if (case["soundIdentity"]?.jsonPrimitive?.booleanOrNull == true) case else {
                    val expected = case.getValue("expected").jsonObject
                    val sounds = expected.getValue("sounds").jsonArray.map { pad ->
                        val queue = pad.jsonObject.getValue("queue").jsonArray.map { sound ->
                            val fields = sound.jsonObject
                            val path = "sounds/" + fields.getValue("file").jsonPrimitive.content
                            val original = input.files.single { it.path == path }
                            JsonObject(fields - "file" + mapOf("archiveEntry" to JsonPrimitive(path),
                                "sha256" to JsonPrimitive(sha256Hex(original.bytes(corpus)))))
                        }
                        JsonObject(pad.jsonObject + ("queue" to JsonArray(queue)))
                    }
                    JsonObject(case + ("expected" to JsonObject(expected + ("sounds" to JsonArray(sounds)))))
                }
                val outcome = soundIdentityOutcome(identityCase, observation)
                val id = input.id
                val isIdentity = case["soundIdentity"]?.jsonPrimitive?.booleanOrNull == true
                val actual = if (isIdentity) outcome.getValue("actual") else parseResult(pack, packRoot)
                val status = if (outcome.getValue("status").jsonPrimitive.content != "pass") outcome.getValue("status")
                    else JsonPrimitive(if (isIdentity || actual == case.getValue("expected")) "pass" else "fail")
                val report = JsonObject(outcome + mapOf("actual" to actual, "status" to status,
                    "archiveExtracted" to JsonPrimitive(true),
                    "platform" to JsonPrimitive("android"), "source" to JsonPrimitive(if (isIdentity) "device-archive-decoder-runner" else "device-archive-files"),
                    "corpusSha256" to JsonPrimitive(corpus.sha256), "storageLocation" to JsonPrimitive(location),
                    "filesystem" to filesystem, "sdkInt" to JsonPrimitive(Build.VERSION.SDK_INT),
                    "device" to JsonPrimitive(Build.MODEL), "completed" to JsonPrimitive(true)))
                assertTrue(TestCaptures.save(androidx.test.uiautomator.UiDevice.getInstance(instrumentation),
                    "pack-storage/$location/$id.json") { it.writeText(report.toString()); true })
                if (report.getValue("status").jsonPrimitive.content != "pass") failures += id
            }
        } finally { root.deleteRecursively() }
        assertEquals("Archive contents and selections on $location storage", emptyList<String>(), failures)
    }

    companion object {
        private val IDS = ((601..604) + (617..625)).map { "KS-G$it" }.toSet()
    }
}
