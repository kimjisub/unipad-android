package com.kimjisub.launchpad

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.kimjisub.launchpad.manager.PreferenceManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.regex.Pattern

/**
 * Theme Tests
 *
 * Theme is opened from Settings' category rail. The theme screen is Compose: a list of themes on
 * the left (the selected, not applied theme shows an Apply button) and an "add theme" entry.
 */
@RunWith(AndroidJUnit4::class)
class ThemeTest : BaseUITest() {

    private val prefs by lazy { PreferenceManager(context) }

    @Test
    fun testThemeActivityNavigation() {
        val originalTheme = prefs.selectedTheme
        try {
            launchToMainScreen()
            takeScreenshot("theme_test_start")

            device.findObject(By.desc(str(R.string.setting))).click()
            assertTrue(
                "Did not transition to the settings screen",
                device.wait(Until.hasObject(By.text(str(R.string.settings_storage))), 5000L)
            )
            takeScreenshot("theme_settings_opened")

            device.findObject(By.text(str(R.string.settings_theme))).click()
            assertTrue(
                "Theme screen did not open",
                device.wait(Until.hasObject(By.desc(str(R.string.theme_add_title))), 5000L)
            )
            takeScreenshot("theme_activity_opened")

            val rows = themeRows()
            assertTrue("No themes listed", rows.isNotEmpty())
            println("Themes listed: ${rows.size}")

            // Selecting a theme that is not applied offers Apply; applying stores it
            val candidate = rows.indices.firstOrNull { i ->
                themeRows()[i].click()
                device.wait(Until.hasObject(By.text(str(R.string.apply))), 1500L)
            }
            takeScreenshot("theme_item_selected")
            if (candidate != null) {
                // Theme previews recompose while Apply is visible. Refetch a replaced node,
                // and keep the original stored-theme assertion and its existing deadline.
                assertTrue("Applying a theme did not store it", waitUntil {
                    if (prefs.selectedTheme != originalTheme) return@waitUntil true
                    try {
                        device.findObject(By.text(str(R.string.apply)))?.click()
                        prefs.selectedTheme != originalTheme
                    } catch (_: StaleObjectException) {
                        false
                    }
                })
                assertTrue(
                    "Apply button did not disappear after applying",
                    device.wait(Until.gone(By.text(str(R.string.apply))), 3000L)
                )
                takeScreenshot("theme_applied")
            } else {
                println("Only the applied theme is installed; Apply is not offered")
                assertEquals("Theme changed without Apply", originalTheme, prefs.selectedTheme)
            }

            // The add-theme entry opens the import / create panel
            device.findObject(By.desc(str(R.string.theme_add_title))).click()
            assertTrue(
                "Add theme panel did not open",
                device.wait(Until.hasObject(By.text(str(R.string.theme_add_zip))), 3000L)
            )
            takeScreenshot("theme_add_panel")

            device.pressBack()
            assertTrue(
                "Did not return to settings from the theme screen",
                device.wait(Until.hasObject(By.text(str(R.string.settings_storage))), 5000L)
            )
            device.pressBack()
            assertTrue("Could not return to the main screen", waitForMainScreen())
            takeScreenshot("theme_test_end")
        } finally {
            prefs.selectedTheme = originalTheme
        }
    }

    /**
     * Clickable theme rows. Each row carries a type badge (Built-in / ZIP); the add-theme entry has
     * none. The list is only marked scrollable when it overflows, so rows are not found through it.
     */
    private fun themeRows(): List<UiObject2> {
        val badge = Pattern.compile(
            "${Pattern.quote(str(R.string.theme_type_builtin))}|${Pattern.quote(str(R.string.theme_type_zip))}"
        )
        val selector = By.pkg(PACKAGE_NAME).clickable(true).hasChild(By.text(badge))
        device.wait(Until.hasObject(selector), 3000L)
        return device.findObjects(selector).sortedBy { it.visibleBounds.top }
    }
}
