package com.kimjisub.launchpad.tool

import org.junit.Assert.assertEquals
import org.junit.Test

class ZipPickingTest {
	@Test
	fun android7PicksAFolderBecauseItsPickerOpensATappedZip() {
		assertEquals(ZipPickRoute.FOLDER, ZipPickRoute.forSdk(24))
		assertEquals(ZipPickRoute.FOLDER, ZipPickRoute.forSdk(25))
	}

	@Test
	fun android8AndLaterKeepTheDocumentPicker() {
		for (sdk in listOf(26, 29, 33, 37)) assertEquals("API $sdk", ZipPickRoute.DOCUMENT, ZipPickRoute.forSdk(sdk))
	}

	@Test
	fun onlyZipFilesAreOfferedNewestFirst() {
		val entries = listOf(
			FolderEntry("1", "old.zip", "application/zip", 100),
			FolderEntry("2", "song.mp3", "audio/mpeg", 900),
			FolderEntry("3", "folder.zip", FolderZips.DIRECTORY_MIME_TYPE, 800),
			FolderEntry("4", "NEW.ZIP", "application/octet-stream", 500),
			FolderEntry("5", null, "application/zip", 700),
			FolderEntry("6", "b.zip", "application/zip", 100),
			FolderEntry("7", "A.zip", "application/x-zip-compressed", 100),
		)

		val picked = FolderZips.select(entries).map { it.documentId }

		assertEquals(listOf("4", "7", "6", "1"), picked)
	}

	@Test
	fun aFolderWithoutZipsOffersNothing() {
		val entries = listOf(
			FolderEntry("1", "sounds", FolderZips.DIRECTORY_MIME_TYPE, 0),
			FolderEntry("2", "info", "application/octet-stream", 0),
		)

		assertEquals(emptyList<FolderEntry>(), FolderZips.select(entries))
	}
}
