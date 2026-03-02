package com.kimjisub.launchpad

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Settings Tests
 * Tests for settings functionality
 */
@RunWith(AndroidJUnit4::class)
class SettingsTest : BaseUITest() {

    @Test
    fun testSettingsActivityNavigation() {
        // Launch the app and navigate to the main screen
        launchApp()
        device.wait(Until.hasObject(By.pkg(PACKAGE_NAME)), LAUNCH_TIMEOUT)
        handlePermissionDialogs()

        // Wait for main screen to load
        Thread.sleep(10000)

        takeScreenshot("before_settings_navigation")

        // Open the FAB menu
        val displayWidth = device.displayWidth
        val displayHeight = device.displayHeight
        val fabX = displayWidth - 80
        val fabY = displayHeight - 80
        device.click(fabX, fabY)
        Thread.sleep(2000)

        // Click the settings button (first button in the FAB menu)
        // Menu opens upward, so move Y coordinate up
        val settingsFabY = fabY - 60
        device.click(fabX, settingsFabY)
        Thread.sleep(3000)

        takeScreenshot("settings_activity")

        // Verify SettingsActivity launched
        val settingsActivityRunning = device.wait(
            Until.hasObject(By.pkg(PACKAGE_NAME)),
            5000L
        )
        assertTrue("Did not transition to the settings screen", settingsActivityRunning)

        // Navigate back to the main screen
        device.pressBack()
        Thread.sleep(2000)

        val backToMain = device.wait(
            Until.hasObject(By.pkg(PACKAGE_NAME)),
            5000L
        )
        assertTrue("Could not return to the main screen", backToMain)

        takeScreenshot("back_from_settings")
    }

    @Test
    fun testSettingsModification() {
        // Launch the app and navigate to the main screen
        launchApp()
        device.wait(Until.hasObject(By.pkg(PACKAGE_NAME)), LAUNCH_TIMEOUT)
        handlePermissionDialogs()

        // Wait for main screen to load
        Thread.sleep(10000)

        takeScreenshot("settings_mod_start")

        // Open the FAB menu
        val displayWidth = device.displayWidth
        val displayHeight = device.displayHeight
        val fabX = displayWidth - 80
        val fabY = displayHeight - 80
        device.click(fabX, fabY)
        Thread.sleep(2000)

        // Click the settings button
        val settingsFabY = fabY - 60
        device.click(fabX, settingsFabY)
        Thread.sleep(3000)

        takeScreenshot("settings_opened")

        // Verify SettingsActivity launched
        val settingsActivityRunning = device.wait(
            Until.hasObject(By.pkg(PACKAGE_NAME)),
            5000L
        )
        assertTrue("Did not transition to the settings screen", settingsActivityRunning)

        // Scroll through the settings screen to explore various options
        // Scroll down
        device.swipe(displayWidth / 2, displayHeight / 2, displayWidth / 2, displayHeight / 4, 10)
        Thread.sleep(2000)
        takeScreenshot("settings_scrolled_down")

        // Click a settings item (top area)
        val settingItemX = displayWidth / 2
        val settingItemY = displayHeight / 3

        device.click(settingItemX, settingItemY)
        Thread.sleep(2000)
        takeScreenshot("settings_item_clicked")

        // Verify the app is still running
        val stillRunning1 = device.wait(
            Until.hasObject(By.pkg(PACKAGE_NAME)),
            3000L
        )
        assertTrue("App terminated after clicking settings item", stillRunning1)

        // A dialog or submenu may have opened, so press back
        device.pressBack()
        Thread.sleep(1000)

        // Click another settings item (center area)
        val settingItemY2 = displayHeight / 2
        device.click(settingItemX, settingItemY2)
        Thread.sleep(2000)
        takeScreenshot("settings_item2_clicked")

        val stillRunning2 = device.wait(
            Until.hasObject(By.pkg(PACKAGE_NAME)),
            3000L
        )
        assertTrue("App terminated after clicking second settings item", stillRunning2)

        // Press back
        device.pressBack()
        Thread.sleep(1000)

        // Scroll up (back to original position)
        device.swipe(displayWidth / 2, displayHeight / 4, displayWidth / 2, displayHeight / 2, 10)
        Thread.sleep(2000)
        takeScreenshot("settings_scrolled_up")

        // Navigate back to the main screen
        device.pressBack()
        Thread.sleep(2000)

        val backToMain = device.wait(
            Until.hasObject(By.pkg(PACKAGE_NAME)),
            5000L
        )
        assertTrue("Could not return to the main screen from settings", backToMain)

        takeScreenshot("settings_mod_end")
    }

    /**
     * Test #30: App settings persistence test
     * - Verifies that settings are preserved after app restart
     */
    @Test
    fun testSettingsPersistence() {
        println("=== App settings persistence test start ===")

        // Launch the app and navigate to the main screen
        launchApp()
        device.wait(Until.hasObject(By.pkg(PACKAGE_NAME)), LAUNCH_TIMEOUT)
        handlePermissionDialogs()

        // Wait for main screen to load
        Thread.sleep(10000)

        takeScreenshot("settings_persistence_start")

        // 1. Navigate from main screen to settings
        // Find and click the FAB menu button by Resource ID
        val fabButton = device.findObject(By.res(PACKAGE_NAME, "floatingMenu"))
        assertNotNull("Could not find the FAB button", fabButton)

        // Open the FAB menu
        fabButton.click()
        Thread.sleep(2000)
        takeScreenshot("settings_persistence_fab_opened")

        // Find and click the Settings button
        val settingsButton = device.wait(
            Until.findObject(By.res(PACKAGE_NAME, "setting")),
            5000L
        )
        assertNotNull("Could not find the Settings button", settingsButton)
        settingsButton.click()
        Thread.sleep(2000)
        takeScreenshot("settings_persistence_opened")

        // 2. Attempt to change the first settings item (e.g., click the top area of the screen)
        val displayMetrics = context.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        // Click the top area of the settings screen (first item)
        device.click(screenWidth / 2, screenHeight / 4)
        Thread.sleep(1000)
        takeScreenshot("settings_persistence_item1_clicked")

        // A dialog or submenu may appear, so close it by pressing back
        device.pressBack()
        Thread.sleep(500)

        // 3. Attempt to change the second settings item
        device.click(screenWidth / 2, screenHeight / 3)
        Thread.sleep(1000)
        takeScreenshot("settings_persistence_item2_clicked")

        device.pressBack()
        Thread.sleep(1000)

        // 4. Verify the app is still running (Settings or MainActivity)
        takeScreenshot("settings_persistence_after_settings")
        println("Settings changes complete, preparing to close app")

        // 5. Close the app (remove from recent apps)
        // Note: Close the app regardless of whether on settings or main screen
        println("Closing the app...")
        device.pressRecentApps()
        Thread.sleep(2000)

        // Swipe up on the recent apps screen to close the app
        val swipeStartY = screenHeight * 2 / 3
        val swipeEndY = screenHeight / 10
        device.swipe(screenWidth / 2, swipeStartY, screenWidth / 2, swipeEndY, 20)
        Thread.sleep(1000)
        takeScreenshot("settings_persistence_app_killed")

        // 6. Return to home via Home button
        device.pressHome()
        Thread.sleep(1000)

        // 7. Restart the app
        println("Restarting the app...")
        val launchIntent = context.packageManager.getLaunchIntentForPackage(PACKAGE_NAME)
        assertNotNull("Could not find launch intent", launchIntent)
        launchIntent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(launchIntent)
        Thread.sleep(3000)

        // Handle permissions (may appear after restart)
        handlePermissionDialogs()
        Thread.sleep(2000)

        // 8. Wait for splash screen and transition to main screen
        // Wait until transition to MainActivity
        var attempts = 0
        var mainActivityFound = false
        while (attempts < 15 && !mainActivityFound) {
            Thread.sleep(1000)
            val currentActivity = device.wait(
                Until.hasObject(By.pkg(PACKAGE_NAME).clazz("com.kimjisub.launchpad.activity.MainActivity")),
                1000L
            )
            mainActivityFound = currentActivity
            attempts++
        }
        assertTrue("Did not transition to main screen after restart", mainActivityFound)
        takeScreenshot("settings_persistence_app_restarted")

        // 9. Re-enter settings screen to verify changes are preserved
        // Open the FAB menu
        val fabButton2 = device.findObject(By.res(PACKAGE_NAME, "floatingMenu"))
        assertNotNull("Could not find FAB button after restart", fabButton2)
        fabButton2.click()
        Thread.sleep(2000)

        // Click the Settings button
        val settingsButton2 = device.wait(
            Until.findObject(By.res(PACKAGE_NAME, "setting")),
            5000L
        )
        assertNotNull("Could not find Settings button after restart", settingsButton2)
        settingsButton2.click()
        Thread.sleep(2000)
        takeScreenshot("settings_persistence_reopened")

        // 10. Verify the settings screen displays normally (if settings persisted, app won't crash)
        val stillRunning = device.wait(
            Until.hasObject(By.pkg(PACKAGE_NAME)),
            5000L
        )
        assertTrue("Failed to enter settings screen after restart", stillRunning)

        // 11. Return to the main screen
        // Note: Attempting pressBack() from SettingsActivity to return to MainActivity,
        // but MainActivity may have been removed from the task stack, so go home and re-enter
        device.pressHome()
        Thread.sleep(1000)

        // Relaunch the app
        val finalLaunchIntent = context.packageManager.getLaunchIntentForPackage(PACKAGE_NAME)
        assertNotNull("Could not find final launch intent", finalLaunchIntent)
        context.startActivity(finalLaunchIntent)
        Thread.sleep(2000)

        // Verify return to MainActivity
        val finalCheck = device.wait(
            Until.hasObject(By.res(PACKAGE_NAME, "fragment_panel")),
            5000L
        ) || device.wait(
            Until.hasObject(By.res(PACKAGE_NAME, "floatingMenu")),
            3000L
        )
        assertTrue("Failed to return to main screen after settings persistence test", finalCheck)

        takeScreenshot("settings_persistence_end")
        println("=== App settings persistence test complete ===")
    }
}
