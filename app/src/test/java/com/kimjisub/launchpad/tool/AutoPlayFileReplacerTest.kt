package com.kimjisub.launchpad.tool

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.util.Date

class AutoPlayFileReplacerTest {

	@get:Rule
	val tmp = TemporaryFolder()

	private val sameSecond = Date(1_790_000_000_000)

	private lateinit var folder: File
	private lateinit var autoPlay: File

	private fun createAutoPlay(content: String = "old") {
		folder = tmp.newFolder("pack")
		autoPlay = File(folder, "autoPlay").apply { writeText(content) }
	}

	private fun files() = folder.list()!!.sorted()

	/** A small pack maps in well under a second, so pressing it twice must not fail on the backup name. */
	@Test
	fun twoReplacementsInTheSameSecond_bothSucceedAndKeepEachOldFile() {
		createAutoPlay("first")
		val replacer = AutoPlayFileReplacer(now = { sameSecond })

		replacer.replace(autoPlay, "second")
		replacer.replace(autoPlay, "third")

		assertEquals("third", autoPlay.readText())
		val backups = files() - "autoPlay"
		assertEquals(2, backups.size)
		assertEquals(listOf("first", "second"), backups.map { File(folder, it).readText() })
		assertEquals(backups[0] + "-2", backups[1])
	}

	/** Running out of space while the old file is copied must not leave a partial backup in the pack. */
	@Test
	fun aFailedBackupCopy_leavesNoBackupOrTemporaryFile() {
		createAutoPlay()
		val copyStopsHalfway = AutoPlayFileReplacer(copy = { from, to ->
			to.writeText(from.readText().take(1))
			throw IOException("No space left on device")
		})

		val thrown = assertThrows(IOException::class.java) { copyStopsHalfway.replace(autoPlay, "new") }

		assertEquals("No space left on device", thrown.message)
		assertEquals("old", autoPlay.readText())
		assertEquals(listOf("autoPlay"), files())
	}

	/** A failure never removes a backup an earlier mapping left behind. */
	@Test
	fun aFailure_keepsEarlierBackups() {
		createAutoPlay("first")
		AutoPlayFileReplacer(now = { sameSecond }).replace(autoPlay, "second")
		val earlier = files() - "autoPlay"

		assertThrows(IOException::class.java) {
			AutoPlayFileReplacer(now = { sameSecond }, copy = { _, _ -> throw IOException("stopped") })
				.replace(autoPlay, "third")
		}

		assertEquals("second", autoPlay.readText())
		assertEquals((earlier + "autoPlay").sorted(), files())
		assertEquals("first", File(folder, earlier.single()).readText())
	}
}
