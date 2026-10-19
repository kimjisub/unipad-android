package com.kimjisub.launchpad

import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.kimjisub.launchpad.activity.MainActivity
import org.junit.Assert.assertEquals
import androidx.test.uiautomator.Until
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ErrorCollector
import org.junit.runner.RunWith

/** Real screens at the phone's default and largest accessibility settings, restored after each case. */
@RunWith(AndroidJUnit4::class)
class LargeTextLayoutTest : BaseUITest() {
    @get:Rule val compose = createEmptyComposeRule()
    @get:Rule val errors = ErrorCollector()

    @Test fun englishDefault() = checkScreens("en", 1f, 420)
    @Test fun koreanDefault() = checkScreens("ko", 1f, 420)
    @Test fun englishLarge420() = checkScreens("en", 2f, 420)
    @Test fun koreanLarge420() = checkScreens("ko", 2f, 420)
    @Test fun englishLarge540() = checkScreens("en", 2f, 540)
    @Test fun koreanLarge540() = checkScreens("ko", 2f, 540)

    private fun checkScreens(language: String, scale: Float, density: Int) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val oldLanguage = AppLanguage.get()
        val oldScale = device.executeShellCommand("settings get system font_scale").trim()
        val oldDensity = device.executeShellCommand("wm density")
            .lineSequence().firstOrNull { it.startsWith("Override density:") }?.substringAfter(":")?.trim()
        val prefix = "large_text_${language}_${scale}_${density}"
        try {
            TestUniPack.install(context, title = TITLE, producer = PRODUCER)
            device.executeShellCommand("settings put system font_scale $scale")
            device.executeShellCommand("wm density $density")
            AppLanguage.set(language)
            launchToMainScreen()
            instrumentation.runOnMainSync {
                val config = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<MainActivity>().single().resources.configuration
                assertEquals("Font scale not applied", scale, config.fontScale, 0.01f)
                assertEquals("Display density not applied", density, config.densityDpi)
                assertEquals("Language not applied", language, config.locales[0].language)
                println("Device API ${android.os.Build.VERSION.SDK_INT}, ${device.displayWidth}x${device.displayHeight}, language=$language, fontScale=${config.fontScale}, density=${config.densityDpi}")
            }
            findTestPackRow()
            takeScreenshot("${prefix}_main")
            if (scale == 1f) {
                assertEquals("Default pack row height changed", 60f * density / 160f,
                    compose.onNode(hasTestTag("main_pack_${TestUniPack.FOLDER_NAME}")).fetchSemanticsNode().boundsInRoot.height, 1f)
            }
            val producer = compose.onNode(
                hasText(PRODUCER) and hasAnyAncestor(hasTestTag("main_pack_${TestUniPack.FOLDER_NAME}")),
                useUnmergedTree = true,
            )
            errors.checkSucceeds {
                producer.assertIsDisplayed()
                val layouts = mutableListOf<TextLayoutResult>()
                producer.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                val layout = layouts.single()
                assertTrue("Producer's line is clipped: $layout", layout.getLineBottom(0) <= layout.size.height)
                assertTrue("Producer's visible bounds clip its line", producer.fetchSemanticsNode().boundsInRoot.height >= layout.getLineBottom(0) - 1f)
                null
            }
            openSettings()
            takeScreenshot("${prefix}_settings")
            errors.checkSucceeds {
                val theme = compose.onNode(hasText(str(R.string.settings_theme)))
                if (scale > 1f) theme.performScrollTo()
                theme.assertIsDisplayed()
                takeScreenshot("${prefix}_settings_scrolled")
                theme.performClick()
                assertTrue("Theme screen did not open", device.wait(Until.hasObject(By.desc(str(R.string.theme_add_title))), 5000L))
                takeScreenshot("${prefix}_theme")
                null
            }
        } finally {
            device.executeShellCommand(if (oldScale == "null") "settings delete system font_scale"
                else "settings put system font_scale $oldScale")
            device.executeShellCommand("wm density ${oldDensity ?: "reset"}")
            AppLanguage.set(oldLanguage)
            TestUniPack.install(context)
        }
    }

    companion object {
        private const val TITLE = "A very long performance pack title for larger text checks"
        private const val PRODUCER = "A very long producer name for larger text checks"
    }
}
