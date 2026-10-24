package com.kimjisub.launchpad

import android.app.Activity
import android.os.Build
import android.provider.Settings
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.google.android.gms.oss.licenses.v2.OssLicensesMenuActivity
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

    private companion object {
        const val SYSTEM_SETTINGS_PACKAGE = "com.android.settings"
    }

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
    fun testMissingTapOrderChoiceShowsNumbersWithoutSaving() {
        withTapOrderChoice(null) {
            repeat(2) {
                launchToMainScreen()
                openSettings()
                takeScreenshot("tap_order_missing_choice_$it")
                assertTrue("An absent choice should show numbers", settingChecked(R.string.trace_log_classic))
                assertTrue("Opening settings saved a default", !context.getSharedPreferences("data", 0).contains("TraceLogClassic"))
            }
        }
    }

    @Test
    fun testBothTapOrderChoicesSurviveSettingsReopening() {
        for (stored in listOf(false, true)) withTapOrderChoice(stored) {
            repeat(2) {
                launchToMainScreen()
                openSettings()
                assertEquals("Stored tap order choice changed", stored, settingChecked(R.string.trace_log_classic))
                assertEquals(stored, prefs.traceLogClassic)
                takeScreenshot("tap_order_stored_${stored}_$it")
            }
        }
    }

    private fun withTapOrderChoice(value: Boolean?, action: () -> Unit) {
        val data = context.getSharedPreferences("data", 0)
        val original = if (data.contains("TraceLogClassic")) data.getBoolean("TraceLogClassic", false) else null
        fun store(choice: Boolean?) {
            val editor = data.edit()
            if (choice == null) editor.remove("TraceLogClassic") else editor.putBoolean("TraceLogClassic", choice)
            assertTrue("Could not store test preference", editor.commit())
        }
        try {
            store(value)
            action()
        } finally {
            store(original)
        }
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
     * From Android 13 the language row opens the system's language screen for UniPad, which lists
     * UniPad's translations and stays open; going back returns to UniPad's settings even while the
     * Settings app keeps another screen open behind it. Below 13 the row only names the translators
     * and tapping it leaves Settings as it was.
     */
    @Test
    fun testLanguageRowOpensSystemAppLanguageScreen() {
        val modern = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        try {
            if (modern) openOtherSystemSettingsScreen()
            launchToMainScreen()
            openSettings()

            val content = device.wait(Until.findObject(By.scrollable(true)), 5000L)
            assertNotNull("Settings content is not scrollable", content)
            val row = content!!.scrollUntil(Direction.DOWN, Until.findObject(By.text(str(R.string.language))))
            assertNotNull("Language row not found", row)
            row!!.click()

            if (!modern) {
                assertSettingsStillOpen("language")
                takeScreenshot("settings_language_row_without_system_screen")
                return
            }
            val korean = By.pkg(SYSTEM_SETTINGS_PACKAGE).text("한국어")
            val list = device.wait(Until.findObject(By.pkg(SYSTEM_SETTINGS_PACKAGE).scrollable(true)), 10000L)
            assertNotNull("The system language screen for UniPad did not open", list)
            assertNotNull(
                "The system language screen does not list Korean",
                list!!.scrollUntil(Direction.DOWN, Until.findObject(korean))
            )
            Thread.sleep(1500L)
            assertTrue("The system language screen closed by itself", device.hasObject(korean))
            takeScreenshot("settings_language_system_screen")

            device.pressBack()
            assertTrue(
                "Did not return to settings from the system language screen",
                device.wait(Until.hasObject(By.pkg(PACKAGE_NAME).text(str(R.string.settings_storage))), 5000L)
            )
        } finally {
            if (modern) device.executeShellCommand("am force-stop $SYSTEM_SETTINGS_PACKAGE")
        }
    }

    /** Leaves the Settings app on its Wi-Fi screen in the background, as after a user visited it. */
    private fun openOtherSystemSettingsScreen() {
        device.executeShellCommand("am start -a ${Settings.ACTION_WIFI_SETTINGS}")
        assertTrue(
            "The Settings app did not open",
            device.wait(Until.hasObject(By.pkg(SYSTEM_SETTINGS_PACKAGE)), 10000L)
        )
        device.pressHome()
        assertTrue(
            "The Settings app stayed in front after Home",
            device.wait(Until.gone(By.pkg(SYSTEM_SETTINGS_PACKAGE)), 5000L)
        )
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
            val homepage = By.text(str(R.string.officialHomepage))
            assertTrue("Community list not found", device.wait(Until.hasObject(homepage), 5000L))
            // The list scrolls only when its items do not fit; on a large screen they all show at once.
            val emailItem = By.text(str(R.string.email))
            val email = device.findObject(emailItem)
                ?: device.findObject(By.scrollable(true).hasDescendant(homepage))
                    ?.scrollUntil(Direction.DOWN, Until.findObject(emailItem))
            assertNotNull("Community e-mail item not found", email)
            email!!.click()
            takeScreenshot("settings_email_without_mail_app")
            assertSettingsStillOpen("community e-mail")
        }
    }

    /**
     * The license row opens the library's current license screen (the older one is deprecated), and
     * that screen reads the bundled license list instead of failing. A debug build bundles a
     * single "Debug License Info" entry; a release build bundles the full list. The screen takes the
     * app's window theme, so its status bar is shown or hidden the same way as on Settings.
     */
    @Test
    fun testOpenSourceLicenseRowOpensLicenseList() {
        launchToMainScreen()
        openSettings()

        val content = device.wait(Until.findObject(By.scrollable(true)), 5000L)
        assertNotNull("Settings content is not scrollable", content)
        val settingsStatusBarVisible = resumedActivityStatusBarVisible()
        val row = content!!.scrollUntil(Direction.DOWN, Until.findObject(By.text(str(R.string.openSourceLicense))))
        assertNotNull("Open source license row not found", row)
        row!!.click()

        assertTrue(
            "The license screen did not open",
            waitUntil(5000L) { resumedActivity() is OssLicensesMenuActivity }
        )
        assertTrue(
            "The license list was not shown",
            device.wait(Until.hasObject(By.pkg(PACKAGE_NAME).text("Debug License Info")), 5000L)
        )
        assertEquals(
            "The license screen shows the status bar differently from Settings",
            settingsStatusBarVisible,
            resumedActivityStatusBarVisible()
        )
        takeScreenshot("settings_open_source_licenses")

        device.pressBack()
        assertTrue(
            "Did not return to settings from the license screen",
            device.wait(Until.hasObject(By.text(str(R.string.settings_storage))), 5000L)
        )
    }

    private fun resumedActivity(): Activity? {
        var activity: Activity? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).firstOrNull()
        }
        return activity
    }

    private fun resumedActivityStatusBarVisible(): Boolean? {
        val decorView = resumedActivity()?.window?.decorView ?: return null
        var visible: Boolean? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            visible = ViewCompat.getRootWindowInsets(decorView)?.isVisible(WindowInsetsCompat.Type.statusBars())
        }
        return visible
    }

    private fun assertSettingsStillOpen(link: String) {
        device.waitForIdle()
        Thread.sleep(1500L)
        assertTrue(
            "Settings did not stay open after tapping the $link link",
            device.hasObject(By.pkg(PACKAGE_NAME).text(str(R.string.settings_storage)))
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
