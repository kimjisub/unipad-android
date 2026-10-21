package com.kimjisub.launchpad.unipack

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kimjisub.launchpad.manager.PackArchive
import com.kimjisub.launchpad.unipack.struct.LedAnimation.LedEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Two keyLed files on one pad whose names hold the same letters composed or decomposed (the shared
 * corpus cases KL-026 to KL-030), stored where the app keeps packs and unpacked from an archive. Android
 * keeps each name as it was written, which the JVM on macOS does not, so only a device shows that a
 * decomposed name reaches the parser and still queues where its composed spelling does.
 */
@RunWith(AndroidJUnit4::class)
class KeyLedNameOrderDeviceTest {

	private val context = InstrumentationRegistry.getInstrumentation().targetContext

	private val appStorage get() = File(checkNotNull(context.getExternalFilesDir(null)), "UniPack")

	@Test
	fun appStorageKeepsTheNamesAndQueuesThemByTheirComposedForm() = checkWrittenIn(appStorage, "app storage")

	@Test
	fun internalStorageKeepsTheNamesAndQueuesThemByTheirComposedForm() =
		checkWrittenIn(File(context.filesDir, "UniPack"), "internal storage")

	@Test
	fun anUnpackedArchiveKeepsTheNamesAndQueuesThemByTheirComposedForm() {
		for (case in CASES) {
			val work = File(appStorage, "key-led-order-zip-${System.nanoTime()}").apply { check(mkdirs()) }
			try {
				val archive = File(work, "pack.zip")
				archive.writeBytes(zip(packFiles(case).mapKeys { "pack/${it.key}" }))
				val destination = File(work, "out")
				PackArchive.extract(archive, destination)
				checkPack(File(destination, "pack"), case, "unpacked archive")
			} finally {
				work.deleteRecursively()
			}
		}
	}

	private fun checkWrittenIn(workspace: File, place: String) {
		for (case in CASES) {
			val root = File(workspace, "key-led-order-${System.nanoTime()}")
			try {
				for ((path, text) in packFiles(case)) File(root, path).apply { requireNotNull(parentFile).mkdirs() }.writeText(text)
				checkPack(root, case, place)
			} finally {
				root.deleteRecursively()
			}
		}
	}

	private fun checkPack(root: File, case: Case, place: String) {
		val listed = requireNotNull(File(root, "keyLed").list()).sorted()
		val pack = UniPackFolder(root).load().loadDetail()
		val queue = pack.ledAnimationTable?.get(0)?.get(0)?.get(0).orEmpty()
		val delays = queue.map { (it.ledEvents.single() as LedEvent.Delay).delay }
		Log.i(TAG, "$place ${case.id}: listed=${listed.map(::codePoints)} delays=$delays errors=${pack.errorDetail}")

		assertEquals("$place ${case.id}: names as stored", listOf(case.earlier, case.later).sorted(), listed)
		assertNull("$place ${case.id}: ${pack.errorDetail}", pack.errorDetail)
		assertEquals("$place ${case.id}: LED queue", listOf(10, 20), delays)
	}

	/** The files of a corpus case: [Case.earlier] waits 10 ms, [Case.later] 20 ms. */
	private fun packFiles(case: Case): Map<String, String> = linkedMapOf(
		"info" to "title=Conformance\nproducerName=UniPad Conformance\nbuttonX=4\nbuttonY=3\nchain=2\nsquareButton=true",
		"keySound" to "1 4 3 c.wav",
		"sounds/c.wav" to "",
		"keyLed/${case.later}" to "d 20",
		"keyLed/${case.earlier}" to "d 10",
	)

	private fun zip(entries: Map<String, String>): ByteArray =
		ByteArrayOutputStream().also { out ->
			ZipOutputStream(out).use { zip ->
				for ((name, text) in entries) {
					zip.putNextEntry(ZipEntry(name))
					zip.write(text.toByteArray())
					zip.closeEntry()
				}
			}
		}.toByteArray()

	private fun codePoints(name: String): String =
		name.codePoints().toArray().joinToString("") { if (it in 0x20..0x7e) it.toChar().toString() else "<U+%04X>".format(it) }

	private class Case(val id: String, val earlier: String, val later: String)

	private companion object {
		const val TAG = "KeyLedNameOrder"

		val CASES = listOf(
			Case("KL-026", "1 1 1 1 o", "1 1 1 1 n\u0303"),
			Case("KL-027", "1 1 1 1 o", "1 1 1 1 \u00f1"),
			Case("KL-028", "1 1 1 1 \u3131", "1 1 1 1 \u1100\u1161"),
			Case("KL-029", "1 1 1 1 \u3131", "1 1 1 1 \uac00"),
			Case("KL-030", "1 1 1 1 \ud83d\ude00", "1 1 1 1 \ufffd"),
		)
	}
}
