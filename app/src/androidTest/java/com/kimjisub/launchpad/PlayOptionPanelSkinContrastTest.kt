package com.kimjisub.launchpad

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import androidx.compose.ui.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import com.kimjisub.launchpad.ui.theme.PlayPalette
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Reads the open Play option panel off the screen, per skin, and checks that the Auto Mapping label
 * (text, 4.5:1), its arrow and the Quit icon (graphics, 3:1) stand out from the panel actually drawn
 * behind them. A skin that sets only option_window used to leave the default accent on its panel.
 */
@RunWith(AndroidJUnit4::class)
class PlayOptionPanelSkinContrastTest : BaseUITest() {

    private val prefs by lazy { context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE) }
    private var originalTheme: String? = null
    private var themeChanged = false

    @After
    fun restoreTheme() {
        if (themeChanged) {
            prefs.edit().apply {
                if (originalTheme == null) remove(KEY_SELECTED_THEME) else putString(KEY_SELECTED_THEME, originalTheme)
            }.commit()
        }
        skinFolder().deleteRecursively()
    }

    @Test
    fun defaultSkin() = checkPanel("default", context.packageName)

    @Test
    fun darkSkin() = checkPanel("dark", installSkin("#424242"))

    @Test
    fun midGreySkin() = checkPanel("midgrey", installSkin("#808080"))

    private fun skinFolder() = File(context.getExternalFilesDir(null), "themes/$SKIN_FOLDER")

    /** A skin that sets only the option panel colour, like the one the issue was found with. */
    private fun installSkin(optionWindow: String): String {
        skinFolder().apply {
            deleteRecursively()
            mkdirs()
            File(this, "theme.json").writeText("""{"name":"UI test panel $optionWindow","author":"UniPad UI Test"}""")
            File(this, "colors.json").writeText("""{"option_window":"$optionWindow"}""")
        }
        return "zip://$SKIN_FOLDER"
    }

    private fun checkPanel(name: String, themeId: String) {
        originalTheme = prefs.getString(KEY_SELECTED_THEME, null)
        themeChanged = true
        prefs.edit().putString(KEY_SELECTED_THEME, themeId).commit()

        launchToMainScreen()
        openTestPackInPlay()
        openPlayOptions()
        Thread.sleep(PANEL_SETTLE_MS)
        takeScreenshot("option_panel_$name")

        val shot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        assertNotNull("Screenshot failed", shot)
        val results = listOf(
            Triple("Auto Mapping", By.text("Auto Mapping"), PlayPalette.MIN_TEXT_CONTRAST),
            Triple("Auto Mapping arrow", By.text("→"), PlayPalette.MIN_GRAPHIC_CONTRAST),
            Triple("Quit", By.desc(str(R.string.quit)), PlayPalette.MIN_GRAPHIC_CONTRAST),
        ).map { (label, selector, min) ->
            val node = device.wait(Until.findObject(selector), 3000L)
            assertNotNull("$label not found in the option panel", node)
            val (ratio, ink) = measure(shot, node!!.visibleBounds)
            println("[$name] $label contrast=%.2f ink=#%06X min=$min".format(ratio, ink and 0xFFFFFF))
            Triple(label, ratio, min) to ink
        }
        results.forEach { (r, _) ->
            val (label, ratio, min) = r
            assertTrue("[$name] $label contrast %.2f is below $min".format(ratio), ratio >= min)
        }
        val quitInk = Color(results.last().second)
        assertTrue("[$name] Quit is no longer red: $quitInk", quitInk.red > quitInk.green + 0.2f && quitInk.red > quitInk.blue + 0.2f)

        quitPlayToMain()
    }

    /**
     * Contrast of the strongest ink pixel against the panel inside [bounds]. The panel is the most
     * common colour there; glyph edges are anti-aliased, so the ink is the pixel that differs most.
     */
    private fun measure(shot: Bitmap, bounds: Rect): Pair<Float, Int> {
        val area = Rect(bounds).apply { intersect(0, 0, shot.width, shot.height) }
        val pixels = IntArray(area.width() * area.height())
        shot.getPixels(pixels, 0, area.width(), area.left, area.top, area.width(), area.height())
        val panel = Color(pixels.toList().groupingBy { it }.eachCount().maxBy { it.value }.key)
        val ink = pixels.maxBy { PlayPalette.contrastRatio(Color(it), panel) }
        return PlayPalette.contrastRatio(Color(ink), panel) to ink
    }

    companion object {
        private const val PREF_NAME = "data"
        private const val KEY_SELECTED_THEME = "SelectedTheme"
        private const val SKIN_FOLDER = "zz_ui_test_panel_skin"
        private const val PANEL_SETTLE_MS = 800L
    }
}
