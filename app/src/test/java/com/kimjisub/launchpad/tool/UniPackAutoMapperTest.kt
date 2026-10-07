package com.kimjisub.launchpad.tool

import com.kimjisub.launchpad.unipack.UniPackFolder
import com.kimjisub.launchpad.unipack.struct.AutoPlay
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

/**
 * Auto mapping rewrites a pack's autoPlay in place. The pack must end with either its old autoPlay
 * or the whole new one: never without it, never half written, and never changed after the play
 * screen that started it is gone.
 */
class UniPackAutoMapperTest {

	@get:Rule
	val tmp = TemporaryFolder()

	private val original = "c 1\nd 50\nt 1 1\nd 100\nf 1 1\nd 100\nt 1 2\nd 300\nt 1 3\nd 777\nt 1 4\nd 90\nt 1 1\n"

	/** a.wav plays 400 ms and b.wav 250 ms; 1 3 has no sound and c.wav on 1 4 cannot be read. */
	private val lengths = mapOf("a.wav" to 400, "b.wav" to 250)

	private class FakeReader(private val lengths: Map<String, Int>) : SoundDurationReader {
		var closed = false
		override fun durationMs(file: File) = lengths[file.name]
		override fun close() {
			closed = true
		}
	}

	private lateinit var root: File
	private lateinit var pack: UniPackFolder

	private fun createPack() {
		root = tmp.newFolder("pack")
		File(root, "info").writeText("title=Test\nproducerName=Tester\nbuttonX=8\nbuttonY=8\nchain=1\n")
		File(root, "keySound").writeText("1 1 1 a.wav\n1 1 2 b.wav\n1 1 4 c.wav\n")
		File(root, "sounds").mkdir()
		listOf("a.wav", "b.wav", "c.wav").forEach { File(root, "sounds/$it").writeText("") }
		File(root, "autoPlay").writeText(original)
		pack = UniPackFolder(root).load().loadDetail() as UniPackFolder
	}

	private fun mapper(
		reader: FakeReader = FakeReader(lengths),
		replacer: AutoPlayFileReplacer = AutoPlayFileReplacer(),
	) = UniPackAutoMapper(pack, { reader }, replacer)

	private val noProgress = object : UniPackAutoMapper.Listener {
		override fun onGetWorkSize(size: Int) {}
		override fun onProgress(progress: Int) {}
	}

	private fun autoPlay() = File(root, "autoPlay").readText()
	private fun files() = root.list()!!.sorted()
	private fun backups() = files().filter { it.startsWith("autoPlay_") }

	@Test
	fun writesEachWaitAsTheLengthOfTheSoundBeforeIt_andKeepsTheOldFile() {
		createPack()
		val reader = FakeReader(lengths)

		runBlocking { mapper(reader).run(noProgress) }

		assertEquals("c 1\nd 1000\nt 1 1\nd 400\nt 1 2\nd 250\nt 1 3\nd 777\nt 1 4\nd 90\nt 1 1\n", autoPlay())
		assertEquals(listOf("autoPlay", "info", "keySound", "sounds"), files() - backups().toSet())
		assertEquals(original, File(root, backups().single()).readText())
		assertTrue("the sound player was not released", reader.closed)
	}

	/** A press whose sound is missing or unreadable stays, and the pack's own wait after it is kept. */
	@Test
	fun aSoundThatCannotBeRead_keepsThePressAndThePacksWait() {
		createPack()

		runBlocking { mapper().run(noProgress) }

		assertTrue(autoPlay(), autoPlay().contains("t 1 3\nd 777\nt 1 4\nd 90\n"))
	}

	@Test
	fun theLoadedAutoPlayIsNotChangedByTheMapping() {
		createPack()
		val delaysBefore = pack.autoPlayTable!!.elements.filterIsInstance<AutoPlay.Element.Delay>().map { it.delay }

		runBlocking { mapper().run(noProgress) }

		assertEquals(delaysBefore, pack.autoPlayTable!!.elements.filterIsInstance<AutoPlay.Element.Delay>().map { it.delay })
	}

	@Test
	fun aFailedWrite_isReportedAndLeavesThePackAsItWas() {
		createPack()
		val failing = AutoPlayFileReplacer(write = { _, _ -> throw IOException("No space left on device") })

		val thrown = assertThrows(IOException::class.java) { runBlocking { mapper(replacer = failing).run(noProgress) } }

		assertEquals("No space left on device", thrown.message)
		assertEquals(original, autoPlay())
		assertEquals(listOf("autoPlay", "info", "keySound", "sounds"), files())
	}

	/** The app stopping while the new content is being written must leave the old autoPlay whole. */
	@Test
	fun stoppingWhileTheNewFileIsWritten_leavesTheOldAutoPlayWhole() {
		createPack()
		var autoPlayWhileWriting: String? = null
		var filesWhileWriting: List<String>? = null
		val stopsMidWrite = AutoPlayFileReplacer(write = { file, content ->
			file.writeText(content.take(content.length / 2))
			// What a process killed at this moment would leave behind.
			autoPlayWhileWriting = autoPlay()
			filesWhileWriting = files()
			throw IOException("stopped")
		})

		assertThrows(IOException::class.java) { runBlocking { mapper(replacer = stopsMidWrite).run(noProgress) } }

		assertEquals(original, autoPlayWhileWriting)
		assertFalse("no backup was needed yet: $filesWhileWriting", filesWhileWriting!!.any { it.startsWith("autoPlay_") })
		assertEquals(listOf("autoPlay", "info", "keySound", "sounds"), files())
	}

	/** The play screen is left while the last press is being measured: nothing may be written. */
	@Test
	fun cancelledBeforeWriting_leavesThePackAsItWas() {
		createPack()

		val job = runBlocking {
			launch {
				mapper().run(object : UniPackAutoMapper.Listener {
					var size = 0
					override fun onGetWorkSize(size: Int) {
						this.size = size
					}

					override fun onProgress(progress: Int) {
						if (progress == size - 1) cancel()
					}
				})
			}.also { it.join() }
		}

		assertTrue(job.isCancelled)

		assertEquals(original, autoPlay())
		assertEquals(listOf("autoPlay", "info", "keySound", "sounds"), files())
	}
}
