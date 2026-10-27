package com.kimjisub.launchpad.tool

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class AutoPlayFileReplacerTest {

	@get:Rule
	val tmp = TemporaryFolder()


	private lateinit var folder: File
	private lateinit var autoPlay: File

	private fun createAutoPlay(content: String = "old") {
		folder = tmp.newFolder("pack")
		autoPlay = File(folder, "autoPlay").apply { writeText(content) }
	}

	private fun files() = folder.list()!!.sorted()

	/** A small pack maps in well under a second, so pressing it twice must not fail on the backup name. */
	@Test
	fun twoReplacements_bothSucceedAndKeepTheImmediatelyPreviousFile() {
		createAutoPlay("first")
		val replacer = AutoPlayFileReplacer()

		replacer.replace(autoPlay, "second")
		replacer.replace(autoPlay, "third")

		assertEquals("third", autoPlay.readText())
		val backups = files() - "autoPlay"
		assertEquals(1, backups.size)
		assertEquals(listOf("second"), backups.map { File(folder, it).readText() })
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
		AutoPlayFileReplacer().replace(autoPlay, "second")
		val earlier = files() - "autoPlay"

		assertThrows(IOException::class.java) {
			AutoPlayFileReplacer(copy = { _, _ -> throw IOException("stopped") })
				.replace(autoPlay, "third")
		}

		assertEquals("second", autoPlay.readText())
		assertEquals((earlier + "autoPlay").sorted(), files())
		assertEquals("first", File(folder, earlier.single()).readText())
	}

	/**
	 * The default backup copy goes through a flushed file and leaves only the whole backup. Whether
	 * it survives a power loss cannot be checked in a unit test.
	 */
	@Test
	fun theDefaultBackupCopy_keepsTheOldContentWholeAndNoPartialFile() {
		createAutoPlay("old")

		AutoPlayFileReplacer().replace(autoPlay, "new")

		assertEquals("new", autoPlay.readText())
		val backup = (files() - "autoPlay").single()
		assertEquals("old", File(folder, backup).readText())
	}

	@Test
	fun copyToDisk_failingToReadLeavesNoPartialFile() {
		createAutoPlay()

		assertThrows(IOException::class.java) {
			AutoPlayFileReplacer.copyToDisk(File(folder, "missing"), File(folder, "autoPlay_backup"))
		}

		assertEquals(listOf("autoPlay"), files())
	}
	@Test
	fun repeatedMappingKeepsOnlyTheImmediatelyPreviousBackup() {
		createAutoPlay("first")
		val replacer = AutoPlayFileReplacer()
		replacer.replace(autoPlay, "second")
		replacer.replace(autoPlay, "third")

		assertEquals("third", autoPlay.readText())
		val backups = files() - "autoPlay"
		assertEquals("Only one previous autoPlay may remain", 1, backups.size)
		assertEquals("second", File(folder, backups.single()).readText())
	}

	@Test
	fun reopeningAndRestoringSwapsBothContentsAndCanBeRepeated() {
		createAutoPlay("first")
		AutoPlayFileReplacer().replace(autoPlay, "second")
		val restore = AutoPlayFileReplacer::class.java.getMethod("restore", File::class.java)
		restore.invoke(AutoPlayFileReplacer(), autoPlay)
		assertEquals("first", autoPlay.readText())
		restore.invoke(AutoPlayFileReplacer(), autoPlay)
		assertEquals("second", autoPlay.readText())
		assertEquals("first", File(folder, (files() - "autoPlay").single()).readText())
	}

	@Test
	fun anInterruptedSaveRecoversBothOriginalsAtEveryRenameBoundary() {
		for (boundary in 1..4) {
			val root = tmp.newFolder("crash-$boundary")
			val current = File(root, "autoPlay").apply { writeText("first") }
			AutoPlayFileReplacer().replace(current, "second")
			var moves = 0
			val interrupted = AutoPlayFileReplacer(move = { from, to ->
				check(from.renameTo(to))
				if (++moves == boundary) throw AssertionError("process stopped")
			})
			assertThrows(AssertionError::class.java) { interrupted.replace(current, "third") }
			AutoPlayFileReplacer().recover(current)
			AutoPlayFileReplacer().recover(current)
			assertEquals(if (boundary == 4) "third" else "second", current.readText())
			assertEquals(if (boundary == 4) "second" else "first", File(root, "autoPlay_previous").readText())
			assertEquals(listOf("autoPlay", "autoPlay_previous"), root.list()!!.sorted())
		}
	}

	@Test
	fun aFailedReadBackRollsBackBothTheCurrentAndPreviousFile() {
		createAutoPlay("first")
		val replacer = AutoPlayFileReplacer()
		replacer.replace(autoPlay, "second")
		assertThrows(IOException::class.java) {
			replacer.replace(autoPlay, "third") { throw IOException("read back failed") }
		}
		assertEquals("second", autoPlay.readText())
		assertEquals("first", File(folder, "autoPlay_previous").readText())
		assertEquals(listOf("autoPlay", "autoPlay_previous"), files())
	}

	@Test
	fun aFailedRestorePreservesBothFiles() {
		createAutoPlay("first")
		val replacer = AutoPlayFileReplacer()
		replacer.replace(autoPlay, "second")
		assertThrows(IOException::class.java) {
			replacer.restore(autoPlay) { throw IOException("read back failed") }
		}
		assertEquals("second", autoPlay.readText())
		assertEquals("first", File(folder, "autoPlay_previous").readText())
	}

	@Test
	fun anInterruptedFirstSaveDoesNotCreateABackupOnRecovery() {
		createAutoPlay()
		assertThrows(AssertionError::class.java) {
			AutoPlayFileReplacer().replace(autoPlay, "new") { throw AssertionError("process stopped") }
		}
		AutoPlayFileReplacer().recover(autoPlay)
		assertEquals("old", autoPlay.readText())
		assertEquals(listOf("autoPlay"), files())
	}

}
