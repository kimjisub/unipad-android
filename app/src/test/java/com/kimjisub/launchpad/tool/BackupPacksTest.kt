package com.kimjisub.launchpad.tool

import androidx.documentfile.provider.DocumentFile
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

/** What the restore list shows and Settings counts in a backup folder. */
class BackupPacksTest {

	@Test
	fun unfinishedExportsAndOtherFilesAreNotPacks() {
		val folder = folderOf(
			"folder_pack" to true,
			"zip_pack.zip" to false,
			"UPPER_CASE.ZIP" to false,
			".unipad-partial-1b2c" to true,
			".unipad-partial-3d4e.zip" to false,
			"notes.txt" to false,
			null to true,
		)

		assertEquals(
			listOf("folder_pack", "zip_pack.zip", "UPPER_CASE.ZIP"),
			SafMigrationHelper.backupPacks(folder).map { it.name },
		)
	}

	@Test
	fun aFolderHoldingOnlyAKilledExportHasNoPack() {
		val folder = folderOf(".unipad-partial-1b2c" to true, ".unipad-partial-3d4e.zip" to false)

		assertEquals(0, SafMigrationHelper.backupPacks(folder).size)
	}

	private fun folderOf(vararg entries: Pair<String?, Boolean>): DocumentFile {
		val children = entries.map { (name, isDirectory) ->
			mockk<DocumentFile> {
				every { this@mockk.name } returns name
				every { this@mockk.isDirectory } returns isDirectory
			}
		}
		return mockk { every { listFiles() } returns children.toTypedArray() }
	}
}
