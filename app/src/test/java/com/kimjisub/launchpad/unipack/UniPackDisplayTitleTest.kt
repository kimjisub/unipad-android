package com.kimjisub.launchpad.unipack

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class UniPackDisplayTitleTest {

	@get:Rule
	val tmp = TemporaryFolder()

	private fun load(folderName: String, info: String): UniPack {
		val root = tmp.newFolder(folderName)
		File(root, "info").writeText(info)
		File(root, "keySound").writeText("1 1 1 a.wav\n")
		File(root, "sounds").mkdir()
		File(root, "sounds/a.wav").writeText("")
		return UniPackFolder(root).load()
	}

	@Test
	fun missingTitle_showsFolderName() {
		val pack = load("Untitled Pack A", "buttonX=4\nbuttonY=3\nchain=1\n")

		assertEquals("", pack.title)
		assertEquals("Untitled Pack A", pack.displayTitle)
	}

	@Test
	fun whitespaceTitle_showsFolderName() {
		val pack = load("Untitled Pack B", "buttonX=4\nbuttonY=3\nchain=1\n")
		pack.title = " \t "

		assertEquals("Untitled Pack B", pack.displayTitle)
	}

	@Test
	fun namedTitle_isShownUnchanged() {
		val pack = load("folder-name", "title=My Pack\nproducerName=Tester\nbuttonX=4\nbuttonY=3\nchain=1\n")

		assertEquals("My Pack", pack.displayTitle)
	}
}
