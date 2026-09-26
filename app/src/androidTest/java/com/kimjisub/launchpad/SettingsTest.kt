package com.kimjisub.launchpad

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.kimjisub.launchpad.manager.PreferenceManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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

            val launchIntent = context.packageManager.getLaunchIntentForPackage(PACKAGE_NAME)
            assertNotNull("Could not find launch intent", launchIntent)
            launchIntent!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            context.startActivity(launchIntent)
            handlePermissionDialogs()
            assertTrue("Did not transition to main screen after restart", waitForMainScreen())
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
