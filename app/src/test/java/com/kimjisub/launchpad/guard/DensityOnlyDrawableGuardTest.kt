package com.kimjisub.launchpad.guard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Keeps drawables that only density splits carry (see [DensityOnlyDrawableScanner]) out of the app. */
class DensityOnlyDrawableGuardTest {

	private val modules = listOf(File("."), File("../design"))

	/** Only these source sets reach the release bundle; debug and test resources never ship. */
	private val shippedSourceSets = listOf("main", "release")

	private fun resourceFolders(): List<File> = modules.flatMap { module ->
		val src = File(module, "src")
		assertTrue("Missing source root ${src.canonicalPath}", src.isDirectory)
		shippedSourceSets.map { sourceSet -> File(src, "$sourceSet/res") }
			.filter { it.isDirectory }
			.flatMap { res -> res.listFiles { dir -> dir.isDirectory }!!.toList() }
	}

	@Test
	fun everyAppDrawableIsInTheBaseApk() {
		val folders = resourceFolders()
		assertTrue(
			"Scanner did not reach app/src/main/res/drawable; resource roots moved?",
			folders.any { it.name == "drawable" && it.parentFile.parentFile.name == "main" },
		)
		val found = DensityOnlyDrawableScanner.scan(
			folders.map { folder -> folder.name to folder.list()!!.toList() },
		)
		assertTrue(
			"These drawables exist only in density folders, so Play ships them only in density splits " +
				"and an install without those splits crashes when it reads them. Add a vector or a " +
				"drawable-nodpi copy under the same name:\n" + found.joinToString("\n"),
			found.isEmpty(),
		)
	}

	@Test
	fun flagsDrawableOnlyInDensityFolders() {
		val folders = listOf(
			"drawable-mdpi" to listOf("icon.png"),
			"drawable-xxhdpi-v21" to listOf("icon.png", "frame.9.png"),
			"drawable-480dpi" to listOf("custom.webp"),
		)
		assertEquals(listOf("custom", "frame", "icon"), DensityOnlyDrawableScanner.scan(folders))
	}

	@Test
	fun acceptsDensityCopiesBackedByABaseFolder() {
		val folders = listOf(
			"drawable-hdpi" to listOf("vector_icon.png", "nodpi_icon.png", "anydpi_icon.png", "v24_icon.png"),
			"drawable" to listOf("vector_icon.xml"),
			"drawable-nodpi" to listOf("nodpi_icon.png"),
			"drawable-anydpi-v24" to listOf("anydpi_icon.xml"),
			"drawable-v24" to listOf("v24_icon.xml"),
		)
		assertEquals(emptyList<String>(), DensityOnlyDrawableScanner.scan(folders))
	}

	@Test
	fun flagsDensityCopiesBackedOnlyByAConfigurationFolder() {
		val folders = listOf(
			"drawable-hdpi" to listOf("night_icon.png", "land_icon.png", "tablet_icon.png"),
			"drawable-night-v26" to listOf("night_icon.xml"),
			"drawable-land" to listOf("land_icon.xml"),
			"drawable-sw600dp" to listOf("tablet_icon.xml"),
		)
		assertEquals(
			listOf("land_icon", "night_icon", "tablet_icon"),
			DensityOnlyDrawableScanner.scan(folders),
		)
	}

	@Test
	fun ignoresHiddenFiles() {
		val folders = listOf(
			"drawable-hdpi" to listOf(".DS_Store", ".gitkeep", "icon.png"),
			"drawable" to listOf("icon.xml"),
		)
		assertEquals(emptyList<String>(), DensityOnlyDrawableScanner.scan(folders))
	}

	@Test
	fun ignoresMipmapsAndOtherResourceTypes() {
		val folders = listOf(
			"mipmap-xxhdpi" to listOf("ic_launcher.png"),
			"layout-hdpi" to listOf("screen.xml"),
		)
		assertEquals(emptyList<String>(), DensityOnlyDrawableScanner.scan(folders))
	}

	@Test
	fun recognisesDensityQualifiers() {
		assertTrue(DensityOnlyDrawableScanner.isDensitySplit("drawable-ldpi"))
		assertTrue(DensityOnlyDrawableScanner.isDensitySplit("drawable-land-xxxhdpi-v21"))
		assertTrue(DensityOnlyDrawableScanner.isDensitySplit("drawable-tvdpi"))
		assertFalse(DensityOnlyDrawableScanner.isDensitySplit("drawable-nodpi"))
		assertFalse(DensityOnlyDrawableScanner.isDensitySplit("drawable-anydpi-v26"))
		assertFalse(DensityOnlyDrawableScanner.isDensitySplit("drawable"))
		assertFalse(DensityOnlyDrawableScanner.isDensitySplit("mipmap-hdpi"))
	}
}
