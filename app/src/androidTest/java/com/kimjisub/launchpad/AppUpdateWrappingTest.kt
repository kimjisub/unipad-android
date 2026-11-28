package com.kimjisub.launchpad

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import com.kimjisub.launchpad.activity.MainActivity
import com.kimjisub.launchpad.manager.PreferenceAppUpdateStore
import com.kimjisub.launchpad.manager.UpdateStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ErrorCollector
import java.util.Locale

/** Verify rendered lines, since accessibility still exposes text that has been ellipsized. */
class AppUpdateWrappingTest : BaseUITest() {
    @get:Rule val compose = createEmptyComposeRule()
    @get:Rule val errors = ErrorCollector()

    @Test fun englishSmallLarge() = checkWrapping("en", 720 to 1440, 320, 2f)
    @Test fun koreanSmallLarge() = checkWrapping("ko", 720 to 1440, 320, 2f)
    @Test fun englishDefault() = checkWrapping("en", 1080 to 2400, 420, 1f)
    @Test fun koreanDefault() = checkWrapping("ko", 1080 to 2400, 420, 1f)
    @Test fun englishLarge() = checkWrapping("en", 1080 to 2400, 420, 2f)
    @Test fun koreanLarge() = checkWrapping("ko", 1080 to 2400, 420, 2f)
    @Test fun spanishSmallLarge() = checkWrapping("es", 720 to 1440, 320, 2f)
    @Test fun indonesianSmallLarge() = checkWrapping("in", 720 to 1440, 320, 2f)
    @Test fun portugueseSmallLarge() = checkWrapping("pt-BR", 720 to 1440, 320, 2f)
    @Test fun spanishDefault() = checkWrapping("es", 1080 to 2400, 420, 1f)
    @Test fun indonesianDefault() = checkWrapping("in", 1080 to 2400, 420, 1f)
    @Test fun portugueseDefault() = checkWrapping("pt-BR", 1080 to 2400, 420, 1f)
    @Test fun germanSmallLarge() = checkWrapping("de", 720 to 1440, 320, 2f)
    @Test fun germanDefault() = checkWrapping("de", 1080 to 2400, 420, 1f)
    @Test fun filipinoSmallLarge() = checkWrapping("fil", 720 to 1440, 320, 2f)
    @Test fun filipinoDefault() = checkWrapping("fil", 1080 to 2400, 420, 1f)
    @Test fun frenchSmallLarge() = checkWrapping("fr", 720 to 1440, 320, 2f)
    @Test fun frenchDefault() = checkWrapping("fr", 1080 to 2400, 420, 1f)
    @Test fun hindiSmallLarge() = checkWrapping("hi", 720 to 1440, 320, 2f)
    @Test fun hindiDefault() = checkWrapping("hi", 1080 to 2400, 420, 1f)
    @Test fun italianSmallLarge() = checkWrapping("it", 720 to 1440, 320, 2f)
    @Test fun italianDefault() = checkWrapping("it", 1080 to 2400, 420, 1f)
    @Test fun japaneseSmallLarge() = checkWrapping("ja", 720 to 1440, 320, 2f)
    @Test fun japaneseDefault() = checkWrapping("ja", 1080 to 2400, 420, 1f)
    @Test fun malaySmallLarge() = checkWrapping("ms", 720 to 1440, 320, 2f)
    @Test fun malayDefault() = checkWrapping("ms", 1080 to 2400, 420, 1f)
    @Test fun burmeseSmallLarge() = checkWrapping("my", 720 to 1440, 320, 2f)
    @Test fun burmeseDefault() = checkWrapping("my", 1080 to 2400, 420, 1f)
    @Test fun russianSmallLarge() = checkWrapping("ru", 720 to 1440, 320, 2f)
    @Test fun russianDefault() = checkWrapping("ru", 1080 to 2400, 420, 1f)
    @Test fun thaiSmallLarge() = checkWrapping("th", 720 to 1440, 320, 2f)
    @Test fun thaiDefault() = checkWrapping("th", 1080 to 2400, 420, 1f)
    @Test fun turkishSmallLarge() = checkWrapping("tr", 720 to 1440, 320, 2f)
    @Test fun turkishDefault() = checkWrapping("tr", 1080 to 2400, 420, 1f)
    @Test fun vietnameseSmallLarge() = checkWrapping("vi", 720 to 1440, 320, 2f)
    @Test fun vietnameseDefault() = checkWrapping("vi", 1080 to 2400, 420, 1f)
    @Test fun simplifiedChineseSmallLarge() = checkWrapping("zh-CN", 720 to 1440, 320, 2f)
    @Test fun simplifiedChineseDefault() = checkWrapping("zh-CN", 1080 to 2400, 420, 1f)

    private fun checkWrapping(language: String, size: Pair<Int, Int>, density: Int, scale: Float) =
        DisplaySettings.withPhoneDisplay(device, size, density) { checkWrappingOnDisplay(language, size, density, scale) }

    private fun checkWrappingOnDisplay(language: String, size: Pair<Int, Int>, density: Int, scale: Float) {
        val oldLanguage = AppLanguage.get()
        val oldScale = device.executeShellCommand("settings get system font_scale").trim()
        val source = ControlledSource()
        val prefix = "update_wrap_${language}_${size.first}x${size.second}_${density}_${scale}"
        try {
            device.executeShellCommand("settings put system font_scale $scale")
            AppLanguage.set(language)
            PreferenceAppUpdateStore(context).apply {
                lastAutoCheckAt = null
                declinedAt = null
                installPostponedAt = null
                downloadRequested = false
            }
            MainActivity.appUpdateSourceForTest = { source }
            launchToMainScreen()
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                val config = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<MainActivity>().single().resources.configuration
                assertEquals(Locale.forLanguageTag(language), Locale.forLanguageTag(config.locales[0].toLanguageTag()))
                assertEquals(density, config.densityDpi)
                assertEquals(scale, config.fontScale, 0.01f)
                println("Geometry: ${device.displayWidth}x${device.displayHeight}, density=$density, scale=$scale, language=$language")
            }
            assertTrue(device.wait(Until.hasObject(By.text(str(R.string.update_check))), 10000L))
            compose.waitUntil(timeoutMillis = 10000L) {
                device.findObject(By.res("app_update_notice"))?.isEnabled == true
            }
            takeScreenshot("${prefix}_check")
            checkText(R.string.update_check, requireComplete = true)
            source.emit(UpdateStatus.Failed)
            compose.waitForIdle()
            assertTrue(device.wait(Until.hasObject(By.text(str(R.string.update_failed))), 10000L))
            takeScreenshot("${prefix}_failed")
            // A long status may ellipsize after three lines at the largest font size.
            checkText(R.string.update_failed, requireComplete = scale == 1f)
            val notice = compose.onNode(hasTestTag("app_update_notice")).fetchSemanticsNode().boundsInRoot
            val settings = device.findObject(By.desc(str(R.string.setting))).visibleBounds
            errors.checkSucceeds {
                assertTrue("update target is below 48dp", notice.height >= 48 * density / 160f - 1)
                if (scale == 1f) {
                    assertTrue("update overlaps settings", notice.right <= settings.left)
                    assertTrue("default footer exceeds its 48dp minimum", notice.height <= 48 * density / 160f + 1)
                } else {
                    val information = device.findObjects(By.scrollable(true))
                        .filter { it.visibleBounds.left < notice.center.x }.minByOrNull { it.visibleBounds.left }
                    assertTrue("information overlaps update", information != null && information.visibleBounds.bottom <= notice.top + 1)
                }
                null
            }
        } finally {
            MainActivity.appUpdateSourceForTest = null
            device.executeShellCommand(if (oldScale == "null") "settings delete system font_scale" else "settings put system font_scale $oldScale")
            AppLanguage.set(oldLanguage)
        }
    }

    private fun checkText(resource: Int, requireComplete: Boolean) {
        errors.checkSucceeds {
            val text = compose.onNode(hasText(str(resource)) and hasAnyAncestor(hasTestTag("app_update_notice")), useUnmergedTree = true)
            text.assertIsDisplayed()
            val layouts = mutableListOf<TextLayoutResult>()
            text.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val layout = layouts.single()
            println("Rendered '${layout.layoutInput.text}': lines=${layout.lineCount}, omitted=${layout.multiParagraph.didExceedMaxLines}, size=${layout.size}")
            assertTrue("more than three lines", layout.lineCount <= 3)
            if (requireComplete) assertFalse("update text is ellipsized: ${layout.layoutInput.text}", layout.multiParagraph.didExceedMaxLines)
            for (line in 0 until layout.lineCount) {
                val visibleEnd = layout.getLineEnd(line, visibleEnd = true)
                val visibleRight = if (visibleEnd > layout.getLineStart(line)) layout.getBoundingBox(visibleEnd - 1).right else 0f
                // The first omitted offset holds the ellipsis; later offsets have no drawn width.
                val ellipsis = if (layout.isLineEllipsized(line)) layout.getBoundingBox(visibleEnd) else null
                println("Line $line: left=${layout.getLineLeft(line)}, right=${layout.getLineRight(line)}, visibleEnd=$visibleEnd, visibleRight=$visibleRight, ellipsis=$ellipsis")
                assertTrue("line clipped vertically", layout.getLineBottom(line) <= layout.size.height + 1)
                if (ellipsis != null) {
                    // Android 11/12 line extents can include hidden text after an end ellipsis.
                    assertTrue("line $line visible text clipped horizontally", visibleRight <= layout.size.width + 1)
                    assertTrue("line $line ellipsis has no width", ellipsis.width > 0f)
                    assertTrue("line $line ellipsis clipped horizontally", ellipsis.left >= -1f && ellipsis.right <= layout.size.width + 1)
                } else {
                    assertTrue("line $line clipped horizontally", layout.getLineRight(line) - layout.getLineLeft(line) <= layout.size.width + 1)
                }
            }
            assertTrue("visible bounds clip the text", text.fetchSemanticsNode().boundsInRoot.height >= layout.getLineBottom(layout.lineCount - 1) - 1)
            null
        }
    }
}
