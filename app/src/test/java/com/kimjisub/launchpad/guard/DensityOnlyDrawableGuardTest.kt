package com.kimjisub.launchpad.guard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Keeps drawables that only density splits carry (see [DensityOnlyDrawableScanner]) out of the app. */
class DensityOnlyDrawableGuardTest {

	private val modules = listOf(File("."), File("../design"))

	/** Only these source sets reach the release bundle; debug and test resources never ship. */
	private val shippedSourceSets = listOf("main", "release")

	/** Every module's resources merge into the app, so the app's minSdk decides which folders all devices read. */
	private fun appMinSdk(): Int {
		val gradle = File("build.gradle")
		val match = Regex("""minSdk(?:Version)?\s*=?\s*(\d+)""").find(gradle.readText())
		assertNotNull("No minSdk in ${gradle.canonicalPath}", match)
		return match!!.groupValues[1].toInt()
	}

	private fun resourceFolders(): List<File> = modules.flatMap { module ->
		val src = File(module, "src")
		assertTrue("Missing source root ${src.canonicalPath}", src.isDirectory)
		shippedSourceSets.map { sourceSet -> File(src, "$sourceSet/res") }
			.filter { it.isDirectory }
			.flatMap { res -> res.listFiles { dir -> dir.isDirectory }!!.toList() }
	}

	@Test
	fun everyAppDrawableIsInTheBaseApk() {
		modules.forEach { module ->
			val drawable = File(module, "src/main/res/drawable")
			assertTrue("Scanner did not reach ${drawable.canonicalPath}; resource roots moved?", drawable.isDirectory)
		}
		val found = DensityOnlyDrawableScanner.scan(
			resourceFolders().map { folder -> folder.name to folder.list()!!.toList() },
			appMinSdk(),
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
		assertEquals(listOf("custom", "frame", "icon"), DensityOnlyDrawableScanner.scan(folders, MIN_SDK))
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
		assertEquals(emptyList<String>(), DensityOnlyDrawableScanner.scan(folders, MIN_SDK))
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
			DensityOnlyDrawableScanner.scan(folders, MIN_SDK),
		)
	}

	@Test
	fun flagsDensityCopiesBackedOnlyByANewerApiFolder() {
		val folders = listOf(
			"drawable-hdpi" to listOf("v26_icon.png", "anydpi_icon.png"),
			"drawable-v26" to listOf("v26_icon.xml"),
			"drawable-anydpi-v26" to listOf("anydpi_icon.xml"),
		)
		assertEquals(listOf("anydpi_icon", "v26_icon"), DensityOnlyDrawableScanner.scan(folders, MIN_SDK))
	}

	@Test
	fun recognisesBaseFoldersUpToMinSdk() {
		assertTrue(DensityOnlyDrawableScanner.isBaseFolder("drawable", MIN_SDK))
		assertTrue(DensityOnlyDrawableScanner.isBaseFolder("drawable-nodpi", MIN_SDK))
		assertTrue(DensityOnlyDrawableScanner.isBaseFolder("drawable-v21", MIN_SDK))
		assertTrue(DensityOnlyDrawableScanner.isBaseFolder("drawable-anydpi-v24", MIN_SDK))
		assertFalse(DensityOnlyDrawableScanner.isBaseFolder("drawable-v25", MIN_SDK))
		assertFalse(DensityOnlyDrawableScanner.isBaseFolder("drawable-anydpi-v26", MIN_SDK))
		assertFalse(DensityOnlyDrawableScanner.isBaseFolder("drawable-night", MIN_SDK))
		assertFalse(DensityOnlyDrawableScanner.isBaseFolder("drawable-hdpi", MIN_SDK))
	}

	@Test
	fun ignoresFilesTheResourceMergerSkips() {
		val folders = listOf(
			"drawable-hdpi" to listOf(".DS_Store", ".gitkeep", "Thumbs.db", "picasa.ini", "icon.png~", "icon.png"),
			"drawable" to listOf("icon.xml"),
		)
		assertEquals(emptyList<String>(), DensityOnlyDrawableScanner.scan(folders, MIN_SDK))
	}

	@Test
	fun ignoresMipmapsAndOtherResourceTypes() {
		val folders = listOf(
			"mipmap-xxhdpi" to listOf("ic_launcher.png"),
			"layout-hdpi" to listOf("screen.xml"),
		)
		assertEquals(emptyList<String>(), DensityOnlyDrawableScanner.scan(folders, MIN_SDK))
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

	private companion object {
		/** Fixed for the synthetic cases above; the real scan reads the app's minSdk. */
		const val MIN_SDK = 24
	}
}
