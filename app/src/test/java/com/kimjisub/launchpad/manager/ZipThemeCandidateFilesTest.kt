package com.kimjisub.launchpad.manager

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/** The stem and extension table that ZipThemeResources looks a theme image up with. */
class ZipThemeCandidateFilesTest {

	private val dir = File("/themes/test")

	@Test
	fun ownStemComesFirstAndEveryExtensionIsTried() {
		val names = ZipThemeResources.candidateFiles(dir, "btn").map { it.name }
		assertEquals(listOf("btn.png", "btn.webp", "btn.jpg", "btn.jpeg"), names)
	}

	@Test
	fun aliasesFollowTheOwnStem() {
		val names = ZipThemeResources.candidateFiles(dir, "playbg").map { it.name }
		assertEquals(
			listOf("playbg.png", "playbg.webp", "playbg.jpg", "playbg.jpeg",
				"play_bg.png", "play_bg.webp", "play_bg.jpg", "play_bg.jpeg"),
			names,
		)
	}
}
