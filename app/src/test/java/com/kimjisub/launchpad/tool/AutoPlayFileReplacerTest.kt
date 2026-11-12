package com.kimjisub.launchpad.tool

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

	@Test
	fun legacyBackupRestoresWithoutChangingItAndCanBeUndone() {
		createAutoPlay("mapped")
		val legacy = File(folder, "autoPlay_2026_09_01-12_30_00").apply { writeText("original") }
		val modified = legacy.lastModified()
		val replacer = AutoPlayFileReplacer()
		assertTrue(replacer.hasBackup(autoPlay))
		replacer.restore(autoPlay)
		assertEquals("original", autoPlay.readText())
		assertEquals("mapped", File(folder, "autoPlay_previous").readText())
		replacer.restore(autoPlay)
		assertEquals("mapped", autoPlay.readText())
		assertEquals("original", File(folder, "autoPlay_previous").readText())
		assertEquals("original", legacy.readText())
		assertEquals(modified, legacy.lastModified())
	}

	@Test
	fun legacyBackupUsesTheHistoricalPrefixForEveryAutoPlayFileCasing() {
		for ((index, name) in listOf("autoplay", "Autoplay", "AutoPlay").withIndex()) {
			val root = tmp.newFolder("case-$index")
			val current = File(root, name).apply { writeText("mapped") }
			val legacy = File(root, "autoPlay_2026_09_01-12_30_00").apply { writeText("original") }
			val modified = legacy.lastModified()
			val previous = File(root, name + "_previous")
			val replacer = AutoPlayFileReplacer()

			assertTrue("Legacy backup must be available for $name", replacer.hasBackup(current))
			replacer.restore(current)
			assertEquals("original", current.readText())
			assertEquals("mapped", previous.readText())

			replacer.restore(current)
			assertEquals("mapped", current.readText())
			assertEquals("original", previous.readText())
			assertEquals("original", legacy.readText())
			assertEquals(modified, legacy.lastModified())
			assertEquals(listOf(name, previous.name, legacy.name).sorted(), root.list()!!.sorted())
		}
	}

	@Test
	fun oldestLegacyBackupUsesParsedDigitsAndSequenceInsteadOfModificationTime() {
		createAutoPlay()
		val names = listOf(
			"autoPlay_2026_01_01-00_00_00",
			"autoPlay_۲۰۲۵_۱۲_۳۱-۲۳_۵۹_۵۹-۱۰",
			"autoPlay_٢٠٢٥_١٢_٣١-٢٣_٥٩_٥٩-٢",
			"autoPlay_۲۰2٥_۱۲_۳۱-۲۳_۵۹_۵۹",
		)
		names.forEachIndexed { index, name ->
			File(folder, name).apply { writeText(name); assertTrue(setLastModified((index + 1) * 1000L)) }
		}
		AutoPlayFileReplacer().restore(autoPlay)
		assertEquals(names.last(), autoPlay.readText())
		names.forEach { assertEquals(it, File(folder, it).readText()) }
	}

	@Test
	fun sequenceUsesItsNumericValueIncludingUnicodeAndLargeNumbers() {
		createAutoPlay()
		listOf("999999999999999999999999999999", "۱۰", "٢").forEach { suffix ->
			File(folder, "autoPlay_2026_01_01-00_00_00-$suffix").writeText(suffix)
		}
		AutoPlayFileReplacer().restore(autoPlay)
		assertEquals("٢", autoPlay.readText())
	}

	@Test
	fun supplementaryUnicodeDigitsAreAccepted() {
		createAutoPlay()
		val digits = "2026_01_02-03_04_05".map { char ->
			if (char.isDigit()) String(Character.toChars(0x1D7CE + (char - '0'))) else char.toString()
		}.joinToString("")
		File(folder, "autoPlay_$digits").writeText("original")
		AutoPlayFileReplacer().restore(autoPlay)
		assertEquals("original", autoPlay.readText())
	}

	@Test
	fun nonBackupNamesDirectoriesAndEmptyLegacyFilesAreIgnored() {
		createAutoPlay()
		listOf("autoPlay_old", "autoPlay_2026_1_01-00_00_00", "autoPlay_2026_01_01-00_00_00-",
			"autoPlay_2026_01_01-00_00_00-2.tmp", "autoPlay_2026_01_01-00_00_00x",
			"other_2026_01_01-00_00_00").forEach { File(folder, it).writeText("untouched") }
		File(folder, "autoPlay_2026_01_01-00_00_00").mkdir()
		File(folder, "autoPlay_2026_01_02-00_00_00").writeText("")
		val before = files()
		val replacer = AutoPlayFileReplacer()
		assertFalse(replacer.hasBackup(autoPlay))
		assertThrows(IOException::class.java) { replacer.restore(autoPlay) }
		assertEquals(before, files())
		File(folder, "autoPlay_2026_01_03-00_00_00").writeText("nonempty")
		assertTrue(replacer.hasBackup(autoPlay))
		replacer.restore(autoPlay)
		assertEquals("nonempty", autoPlay.readText())
	}

	@Test
	fun previousBackupTakesPriorityOverLegacyEvenWhenEmpty() {
		createAutoPlay()
		val legacy = File(folder, "autoPlay_2026_01_01-00_00_00").apply { writeText("legacy") }
		val previous = File(folder, "autoPlay_previous").apply { writeText("") }
		val replacer = AutoPlayFileReplacer()
		assertTrue(replacer.hasBackup(autoPlay))
		replacer.restore(autoPlay)
		assertEquals("", autoPlay.readText())
		assertEquals("old", previous.readText())
		assertEquals("legacy", legacy.readText())
	}

	@Test
	fun failedLegacyRestoreRollsBackAtEveryWriteCopyMoveAndValidation() {
		// One write, three copies, four renames and the validation callback.
		for (boundary in 1..9) {
			val root = tmp.newFolder("failure-$boundary")
			val current = File(root, "autoPlay").apply { writeText("mapped") }
			val legacy = File(root, "autoPlay_2026_01_01-00_00_00").apply { writeText("original") }
			var operations = 0
			fun fail() { if (++operations == boundary) throw IOException("stopped") }
			val replacer = AutoPlayFileReplacer(
				write = { to, content -> to.writeText(content); fail() },
				copy = { from, to -> from.copyTo(to, overwrite = true); fail() },
				move = { from, to ->
					// The last rename is the commit point: fail before committing.
					if (to.name.endsWith(".committed")) fail()
					check(from.renameTo(to))
					if (!to.name.endsWith(".committed")) fail()
				},
			)
			assertThrows(IOException::class.java) { replacer.restore(current) { fail() } }
			assertEquals("Failure must reach the intended operation", boundary, operations)
			AutoPlayFileReplacer().recover(current)
			assertEquals("boundary $boundary", "mapped", current.readText())
			assertEquals("original", legacy.readText())
			assertEquals(listOf("autoPlay", legacy.name).sorted(), root.list()!!.sorted())
		}
	}

	@Test
	fun interruptedLegacyRestoreRecoversAtEveryRenameBoundary() {
		for (boundary in 1..4) {
			val root = tmp.newFolder("legacy-crash-$boundary")
			val current = File(root, "autoPlay").apply { writeText("mapped") }
			val legacy = File(root, "autoPlay_2026_01_01-00_00_00").apply { writeText("original") }
			var moves = 0
			val replacer = AutoPlayFileReplacer(move = { from, to ->
				check(from.renameTo(to))
				if (++moves == boundary) throw AssertionError("process stopped")
			})
			assertThrows(AssertionError::class.java) { replacer.restore(current) }
			AutoPlayFileReplacer().recover(current)
			AutoPlayFileReplacer().recover(current)
			assertEquals(if (boundary == 4) "original" else "mapped", current.readText())
			val previous = File(root, "autoPlay_previous")
			if (boundary == 4) assertEquals("mapped", previous.readText()) else assertFalse(previous.exists())
			assertEquals("original", legacy.readText())
		}
	}
}
