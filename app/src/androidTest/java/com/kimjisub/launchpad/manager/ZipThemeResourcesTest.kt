package com.kimjisub.launchpad.manager

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * A ZIP theme has to load end to end. 4.1.6 (113) shipped a version where every `zip://` theme
 * threw an NPE on the first image lookup, because the alias table was an instance property declared
 * after `init` and was still null when `init` ran (unipad-android#72). Nothing caught it: no test
 * had ever constructed this class.
 */
@RunWith(AndroidJUnit4::class)
class ZipThemeResourcesTest {

	private fun seedTheme(dir: File, extraImages: List<String> = emptyList()) {
		dir.mkdirs()
		File(dir, "theme.json").writeText("""{"name":"Test Skin","author":"tester","version":"1.0.0"}""")
		File(dir, "colors.json").writeText("""{"checkbox":"#FF00FF"}""")
		val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
		for (name in listOf("theme_ic.png", "playbg.png", "btn.png", "btn_.png") + extraImages) {
			File(dir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
		}
	}

	@Test
	fun zipTheme_loadsIconAndImages() {
		val context = InstrumentationRegistry.getInstrumentation().targetContext
		val dir = File(context.cacheDir, "zip-theme-test-${System.nanoTime()}")
		seedTheme(dir)

		val theme = ZipThemeResources(context, dir, fullLoad = true)

		assertNotNull("icon", theme.icon)
		assertNotNull("playbg", theme.playbg)
		assertNotNull("btn", theme.btn)
		assertNotNull("btnPressed", theme.btnPressed)
		assertTrue("name", theme.name == "Test Skin")
		dir.deleteRecursively()
	}

	@Test
	fun zipTheme_acceptsTheAlternateStemsAndExtensions() {
		val context = InstrumentationRegistry.getInstrumentation().targetContext
		val dir = File(context.cacheDir, "zip-theme-alias-${System.nanoTime()}")
		dir.mkdirs()
		File(dir, "theme.json").writeText("""{"name":"Alias Skin","author":"tester","version":"1.0.0"}""")
		val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
		// play_bg instead of playbg, and webp instead of png: what iOS and web accept.
		File(dir, "play_bg.webp").outputStream().use { bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSLESS, 100, it) }

		val theme = ZipThemeResources(context, dir, fullLoad = true)

		assertNotNull("playbg from play_bg.webp", theme.playbg)
		dir.deleteRecursively()
	}
}
