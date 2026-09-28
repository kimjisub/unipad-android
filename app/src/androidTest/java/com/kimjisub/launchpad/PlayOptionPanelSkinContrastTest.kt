package com.kimjisub.launchpad

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
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
 *
 * Every kind of plain text is measured too: title, section title, option label, pack info (title and
 * the secondary lines), Play Mode label and the Auto Mapping progress line. On #808080 the dark/white
 * pick alone gave 3.84, and the info card and Play Mode fills held their text at 4.48 and 4.37; with
 * those fills thinned every text there must reach 4.5:1. #787878 sits in the band where the bare
 * panel falls short over a white play screen, so its fills stay full and no colour in the text's
 * direction gives 4.5:1 on them; there the text must reach at least what its colour guarantees over
 * every backdrop, and the measured value is printed as a known limit. Over the dark play screen drawn
 * here its panel text must still give 4.5:1.
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

    @Test
    fun knownLimitGreySkin() = checkPanel("limitgrey", installSkin("#787878"), fillLimits(0xFF787878))

    /** On these panels even the full info card and Play Mode fills leave no colour in the text's direction at 4.5:1. */
    private fun fillLimits(optionWindow: Long) = mapOf(
        INFO_CARD_TITLE to guaranteed(optionWindow, PlayPalette.INFO_CARD_FILL_ALPHA) { it.primary },
        INFO_CARD_PRODUCER to guaranteed(optionWindow, PlayPalette.INFO_CARD_FILL_ALPHA) { it.secondary },
        PLAY_MODE_LABEL to guaranteed(optionWindow, PlayPalette.PLAY_MODE_FILL_ALPHA) { it.primary },
    )

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

    /** Worst contrast of the chosen text colour over every backdrop PlayActivity may draw behind it. */
    private fun guaranteed(optionWindow: Long, nominalFill: Float, pick: (PlayPalette.OptionPanelText) -> Color): Float {
        val panel = Color(optionWindow).copy(alpha = 0.94f)
        val fill = PlayPalette.optionPanelFill(panel, nominalFill)
        return PlayPalette.worstContrast(pick(fill.text), PlayPalette.optionPanelFillBackdrops(panel, fill.alpha))
    }

    private fun checkPanel(name: String, themeId: String, knownLimits: Map<String, Float> = emptyMap()) {
        originalTheme = prefs.getString(KEY_SELECTED_THEME, null)
        themeChanged = true
        prefs.edit().putString(KEY_SELECTED_THEME, themeId).commit()

        launchToMainScreen()
        openTestPackInPlay()
        openPlayOptions()
        Thread.sleep(PANEL_SETTLE_MS)
        takeScreenshot("option_panel_$name")

        val shot = screenshot()
        val text = PlayPalette.MIN_TEXT_CONTRAST
        val graphic = PlayPalette.MIN_GRAPHIC_CONTRAST
        val panel = measureAll(
            name,
            shot,
            knownLimits,
            Triple("Auto Mapping", By.text("Auto Mapping"), text),
            Triple("Auto Mapping arrow", By.text("→"), graphic),
            Triple("Quit", By.desc(str(R.string.quit)), graphic),
            Triple("title", By.text(str(R.string.menu)), text),
            Triple("section title", By.text("PERFORMANCE"), text),
            Triple("option label", By.text(str(R.string.feedbackLight)), text),
            Triple(INFO_CARD_TITLE, By.text(TestUniPack.TITLE), text),
            Triple(PLAY_MODE_LABEL, By.text(str(R.string.guidePlay)), text),
        )
        val quitInk = Color(panel.single { it.label == "Quit" }.ink)
        assertTrue("[$name] Quit is no longer red: $quitInk", quitInk.red > quitInk.green + 0.2f && quitInk.red > quitInk.blue + 0.2f)

        device.findObject(By.text(TestUniPack.TITLE)).click()
        assertNotNull("[$name] pack info did not expand", device.wait(Until.findObject(By.text(TestUniPack.PRODUCER)), 3000L))
        Thread.sleep(PANEL_SETTLE_MS)
        takeScreenshot("option_panel_${name}_info")
        val info = measureAll(name, screenshot(), knownLimits, Triple(INFO_CARD_PRODUCER, By.text(TestUniPack.PRODUCER), text))

        device.findObject(By.text("Auto Mapping")).click()
        assertNotNull("[$name] Auto Mapping did not start", device.wait(Until.findObject(By.text(AUTO_MAPPING_BUSY)), 5000L))
        val busyShot = screenshot()
        takeScreenshot("option_panel_${name}_automapping")
        val busy = measureAll(name, busyShot, knownLimits, Triple("auto mapping progress", By.text(AUTO_MAPPING_BUSY), text))
        assertNotNull("[$name] Auto Mapping did not finish", device.wait(Until.findObject(By.text("Auto Mapping")), AUTO_MAPPING_TIMEOUT_MS))

        (panel + info + busy).forEach { it.assertReadable(name) }

        quitPlayToMain()
    }

    private fun screenshot(): Bitmap = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()) { "Screenshot failed" }

    private class Contrast(val label: String, val ratio: Float, val ink: Int, val required: Float) {
        fun assertReadable(skin: String) =
            assertTrue("[$skin] $label contrast %.2f is below %.2f".format(ratio, required), ratio >= required)
    }

    /**
     * Contrast of the strongest ink pixel against the background inside each node. The background is
     * the most common colour there; glyph edges are anti-aliased, so the ink is the pixel that differs most.
     */
    private fun measureAll(name: String, shot: Bitmap, knownLimits: Map<String, Float>, vararg items: Triple<String, BySelector, Float>): List<Contrast> =
        items.map { (label, selector, min) ->
            val node = device.wait(Until.findObject(selector), 3000L)
            assertNotNull("[$name] $label not found in the option panel", node)
            val area = Rect(node!!.visibleBounds).apply { intersect(0, 0, shot.width, shot.height) }
            val pixels = IntArray(area.width() * area.height())
            shot.getPixels(pixels, 0, area.width(), area.left, area.top, area.width(), area.height())
            val background = Color(pixels.toList().groupingBy { it }.eachCount().maxBy { it.value }.key)
            val ink = pixels.maxBy { PlayPalette.contrastRatio(Color(it), background) }
            val ratio = PlayPalette.contrastRatio(Color(ink), background)
            val floor = knownLimits[label]
            val required = floor?.let { minOf(min, it - FLOOR_TOLERANCE) } ?: min
            val limit = floor?.let { " KNOWN LIMIT: guaranteed %.2f over every backdrop".format(it) } ?: ""
            println(
                "[$name] $label contrast=%.2f ink=#%06X bg=#%06X required=%.2f$limit"
                    .format(ratio, ink and 0xFFFFFF, background.toArgb() and 0xFFFFFF, required)
            )
            Contrast(label, ratio, ink, required)
        }

    companion object {
        private const val PREF_NAME = "data"
        private const val KEY_SELECTED_THEME = "SelectedTheme"
        private const val SKIN_FOLDER = "zz_ui_test_panel_skin"
        private const val PANEL_SETTLE_MS = 800L
        private const val AUTO_MAPPING_BUSY = "Auto Mapping…"
        private const val AUTO_MAPPING_TIMEOUT_MS = 60_000L
        private const val FLOOR_TOLERANCE = 0.05f
        private const val INFO_CARD_TITLE = "pack title (info card)"
        private const val INFO_CARD_PRODUCER = "pack producer (info card, secondary)"
        private const val PLAY_MODE_LABEL = "play mode label"
    }
}
