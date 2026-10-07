package com.kimjisub.launchpad

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.kimjisub.launchpad.manager.PreferenceManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Settings Tests
 * Tests for settings functionality
 *
 * Settings is reached from the main screen's Settings icon (the FAB menu is gone). The screen is
 * Compose: a category rail (Info / Storage / Theme) and switch rows found by their title text.
 */
@RunWith(AndroidJUnit4::class)
class SettingsTest : BaseUITest() {

    private val prefs by lazy { PreferenceManager(context) }

    @Test
    fun testSettingsActivityNavigation() {
        launchToMainScreen()
        takeScreenshot("before_settings_navigation")

        openSettings()
        takeScreenshot("settings_activity")

        device.pressBack()
        assertTrue("Could not return to the main screen", waitForMainScreen())
        takeScreenshot("back_from_settings")
    }

    @Test
    fun testSettingsModification() {
        val original = prefs.traceLogClassic
        try {
            launchToMainScreen()
            takeScreenshot("settings_mod_start")

            openSettings()
            takeScreenshot("settings_opened")

            // Toggle a play setting and check both the switch and the stored preference
            settingRow(R.string.trace_log_classic).click()
            assertTrue("Switch did not toggle", waitUntil { settingChecked(R.string.trace_log_classic) != original })
            assertEquals("Preference not written", !original, prefs.traceLogClassic)
            takeScreenshot("settings_item_clicked")

            // Move between categories
            device.findObject(By.text(str(R.string.settings_storage))).click()
            assertTrue(
                "Storage category did not open",
                device.wait(Until.gone(By.textStartsWith(str(R.string.trace_log_classic))), 5000L)
            )
            takeScreenshot("settings_item2_clicked")

            device.findObject(By.text(str(R.string.settings_info))).click()
            assertTrue(
                "Info category did not reopen",
                device.wait(Until.hasObject(By.textStartsWith(str(R.string.trace_log_classic))), 5000L)
            )

            settingRow(R.string.trace_log_classic).click()
            assertTrue("Switch did not toggle back", waitUntil { settingChecked(R.string.trace_log_classic) == original })
            assertEquals("Preference not restored", original, prefs.traceLogClassic)
            takeScreenshot("settings_scrolled_up")

            device.pressBack()
            assertTrue("Could not return to the main screen from settings", waitForMainScreen())
            takeScreenshot("settings_mod_end")
        } finally {
            prefs.traceLogClassic = original
        }
    }

    /**
     * Test #30: App settings persistence test
     * - Verifies that a changed setting is shown again after the app is closed and relaunched.
     *
     * The app process cannot be killed here: the instrumentation runs inside it. The app is left
     * (Home) and relaunched with a cleared task, so SettingsActivity is rebuilt from preferences.
     */
    @Test
    fun testSettingsPersistence() {
        val original = prefs.slideMode
        try {
            launchToMainScreen()
            takeScreenshot("settings_persistence_start")

            openSettings()
            takeScreenshot("settings_persistence_opened")

            settingRow(R.string.slide_mode).click()
            assertTrue("Switch did not toggle", waitUntil { settingChecked(R.string.slide_mode) != original })
            takeScreenshot("settings_persistence_item1_clicked")

            device.pressHome()
            device.wait(Until.gone(By.pkg(PACKAGE_NAME)), 5000L)
            takeScreenshot("settings_persistence_app_killed")

            launchToMainScreen()
            takeScreenshot("settings_persistence_app_restarted")

            openSettings()
            assertTrue(
                "Changed setting was not kept after relaunch",
                waitUntil { settingChecked(R.string.slide_mode) == !original }
            )
            assertEquals("Preference not kept", !original, prefs.slideMode)
            takeScreenshot("settings_persistence_reopened")

            settingRow(R.string.slide_mode).click()
            assertTrue("Switch did not toggle back", waitUntil { settingChecked(R.string.slide_mode) == original })

            device.pressBack()
            assertTrue("Failed to return to main screen after settings persistence test", waitForMainScreen())
            takeScreenshot("settings_persistence_end")
        } finally {
            prefs.slideMode = original
        }
    }

    /**
     * The info rows are drawn from string resources in the device language, not English literals:
     * the language row names the language in its own words and the push identifier hint is translated.
     */
    @Test
    fun testInfoRowsFollowDeviceLanguage() {
        launchToMainScreen()
        openSettings()

        val content = device.wait(Until.findObject(By.scrollable(true)), 5000L)
        assertNotNull("Settings content is not scrollable", content)
        val hint = content!!.scrollUntil(Direction.DOWN, Until.findObject(By.text(str(R.string.tap_to_copy))))
        assertNotNull("Push identifier hint '${str(R.string.tap_to_copy)}' not found", hint)
        assertNotNull(
            "Language row '${str(R.string.language)}' not found",
            device.findObject(By.text(str(R.string.language)))
        )
        takeScreenshot("settings_info_language_${context.resources.configuration.locales[0].language}")

        if (context.resources.configuration.locales[0].language != "en") {
            assertNull("Push identifier hint is still English", device.findObject(By.text("Tap to copy")))
        }
    }

    /**
     * With no app on the device for a link (no browser, no mail app), the GitHub row and a community
     * item report it and leave Settings open instead of crashing the app.
     *
     * The handlers are disabled for the test and enabled again after it. A crash kills this process
     * before the finally block runs; enable them by hand then (`pm enable --user 0 <package>`).
     */
    @Test
    fun testLinksWithoutHandlingAppKeepSettingsOpen() {
        withoutHandlers(
            "-a android.intent.action.VIEW -d https://github.com/kimjisub/unipad-android",
            "-a android.intent.action.SENDTO -d mailto:0226unipad@gmail.com",
        ) {
            launchToMainScreen()
            openSettings()

            val content = device.wait(Until.findObject(By.scrollable(true)), 5000L)
            assertNotNull("Settings content is not scrollable", content)
            val github = content!!.scrollUntil(Direction.DOWN, Until.findObject(By.text(str(R.string.github))))
            assertNotNull("GitHub row not found", github)
            github!!.click()
            takeScreenshot("settings_github_without_browser")
            assertSettingsStillOpen("GitHub")

            device.findObject(By.text(str(R.string.community))).click()
            val list = device.wait(
                Until.findObject(By.scrollable(true).hasDescendant(By.text(str(R.string.officialHomepage)))),
                5000L
            )
            assertNotNull("Community list not found", list)
            val email = list!!.scrollUntil(Direction.DOWN, Until.findObject(By.text(str(R.string.email))))
            assertNotNull("Community e-mail item not found", email)
            email!!.click()
            takeScreenshot("settings_email_without_mail_app")
            assertSettingsStillOpen("community e-mail")
        }
    }

    private fun assertSettingsStillOpen(link: String) {
        device.waitForIdle()
        Thread.sleep(1500L)
        assertTrue(
            "Settings did not stay open after tapping the $link link",
            device.hasObject(By.pkg(PACKAGE_NAME).text(str(R.string.settings_storage)))
        )
    }

    /** Runs [block] with every app that resolves the given `cmd package query-activities` arguments disabled. */
    private fun withoutHandlers(vararg queries: String, block: () -> Unit) {
        val packages = queries.flatMap { query ->
            device.executeShellCommand("cmd package query-activities --brief $query")
                .lines()
                .mapNotNull { Regex("""^\s+([\w.]+)/\S+$""").find(it)?.groupValues?.get(1) }
        }.distinct()
        try {
            packages.forEach { device.executeShellCommand("pm disable-user --user 0 $it") }
            block()
        } finally {
            packages.forEach { device.executeShellCommand("pm enable --user 0 $it") }
        }
    }

    private fun openSettings() {
        val settingsButton = device.wait(Until.findObject(By.desc(str(R.string.setting))), 5000L)
        assertNotNull("Could not find the Settings button", settingsButton)
        settingsButton!!.click()
        assertTrue(
            "Did not transition to the settings screen",
            device.wait(Until.hasObject(By.text(str(R.string.settings_storage))), 5000L)
        )
    }

    /** The title text of a settings row (tapping it toggles the row). */
    private fun settingRow(titleRes: Int): UiObject2 {
        val row = device.wait(Until.findObject(By.textStartsWith(str(titleRes))), 5000L)
        assertNotNull("Setting '${str(titleRes)}' not found", row)
        return row!!
    }

    private fun settingChecked(titleRes: Int): Boolean = isSwitchNextToChecked(settingRow(titleRes))
}
