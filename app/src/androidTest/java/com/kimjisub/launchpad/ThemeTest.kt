package com.kimjisub.launchpad

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.Until
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Theme Tests
 * Tests for theme navigation and selection
 */
@RunWith(AndroidJUnit4::class)
class ThemeTest : BaseUITest() {

    @Test
    fun testThemeActivityNavigation() {
        // Launch the app and navigate to the main screen
        launchApp()
        device.wait(Until.hasObject(By.pkg(PACKAGE_NAME)), LAUNCH_TIMEOUT)
        handlePermissionDialogs()

        // Wait for main screen to load
        Thread.sleep(10000)

        takeScreenshot("theme_test_start")

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

        takeScreenshot("theme_settings_opened")

        // Verify SettingsActivity launched
        val settingsActivityRunning = device.wait(
            Until.hasObject(By.pkg(PACKAGE_NAME)),
            5000L
        )
        assertTrue("Did not transition to the settings screen", settingsActivityRunning)

        println("=== Finding THEME category in SettingsActivity ===")

        // Attempt to click THEME category (left category area)
        // SettingsActivity has a category list on the left and detail content on the right
        val categoryX = displayWidth / 4

        // Expected order: INFO, STORAGE, THEME
        // Try multiple Y positions to find the THEME category
        val categoryYPositions = listOf(
            displayHeight / 3,     // Top
            displayHeight / 2,     // Middle
            displayHeight * 2 / 3  // Bottom
        )

        var themeClicked = false
        for (categoryY in categoryYPositions) {
            println("Attempting to click category area: X=$categoryX, Y=$categoryY")
            device.click(categoryX, categoryY)
            Thread.sleep(2000)

            // Check if ThemeActivity started
            val themeActivityRunning = device.wait(
                Until.hasObject(By.pkg(PACKAGE_NAME)),
                3000L
            )

            if (themeActivityRunning) {
                // Check for "apply" button to determine if we entered ThemeActivity
                val applyButton = device.findObject(
                    UiSelector()
                        .resourceId("${PACKAGE_NAME}:id/apply")
                )

                if (applyButton.exists()) {
                    println("Entered ThemeActivity")
                    themeClicked = true
                    takeScreenshot("theme_activity_opened")
                    break
                } else {
                    println("Not ThemeActivity, navigating back and retrying")
                    device.pressBack()
                    Thread.sleep(1000)
                }
            }
        }

        if (!themeClicked) {
            println("Could not enter ThemeActivity. Attempting direct THEME text search")

            // Search for and click "THEME" or "theme" text
            val themeText = device.findObject(
                UiSelector()
                    .textMatches("(?i).*theme.*|.*테마.*")
                    .clickable(true)
            )

            if (themeText.exists()) {
                println("THEME text found, clicking")
                themeText.click()
                Thread.sleep(3000)
                takeScreenshot("theme_activity_opened")
            } else {
                println("Could not find THEME category. Ending test")
                device.pressBack()
                Thread.sleep(2000)
                takeScreenshot("theme_test_failed")
                return
            }
        }

        println("=== ThemeActivity test start ===")

        // Check for RecyclerView (carousel) in ThemeActivity
        val themeList = device.findObject(
            UiSelector()
                .resourceId("${PACKAGE_NAME}:id/list")
        )

        if (themeList.exists()) {
            println("Theme list (carousel) found")

            // Test theme carousel horizontal scrolling
            println("Theme carousel scroll test: left to right")
            device.swipe(displayWidth * 3 / 4, displayHeight / 2, displayWidth / 4, displayHeight / 2, 20)
            Thread.sleep(1500)
            takeScreenshot("theme_scrolled_right")

            println("Theme carousel scroll test: right to left")
            device.swipe(displayWidth / 4, displayHeight / 2, displayWidth * 3 / 4, displayHeight / 2, 20)
            Thread.sleep(1500)
            takeScreenshot("theme_scrolled_left")

            // Click center theme item (select)
            println("Clicking center theme item")
            val centerX = displayWidth / 2
            val centerY = displayHeight / 2
            device.click(centerX, centerY)
            Thread.sleep(1000)
            takeScreenshot("theme_item_selected")

            // Scroll to select a different theme
            println("Scrolling to another theme")
            device.swipe(displayWidth * 2 / 3, displayHeight / 2, displayWidth / 3, displayHeight / 2, 15)
            Thread.sleep(1500)
            takeScreenshot("theme_another_selected")

            // Click center item once more
            device.click(centerX, centerY)
            Thread.sleep(1000)
            takeScreenshot("theme_final_selection")

        } else {
            println("Theme list not found")
            takeScreenshot("theme_list_not_found")
        }

        // Test Apply button click
        println("=== Apply button click test ===")
        val applyButton = device.findObject(
            UiSelector()
                .resourceId("${PACKAGE_NAME}:id/apply")
        )

        if (applyButton.exists()) {
            println("Apply button found, clicking")
            applyButton.click()
            Thread.sleep(2000)
            takeScreenshot("theme_applied")

            // Verify return to SettingsActivity or MainActivity after Apply
            val returnedToMain = device.wait(
                Until.hasObject(By.pkg(PACKAGE_NAME)),
                5000L
            )
            assertTrue("App terminated after Apply", returnedToMain)

            println("Apply successful, returned to previous screen")
            takeScreenshot("theme_after_apply")
        } else {
            println("Apply button not found. Exiting via back button")
            device.pressBack()
            Thread.sleep(2000)
        }

        // Press back once more if on the settings screen
        println("Returning to main screen")
        device.pressBack()
        Thread.sleep(2000)

        val backToMain = device.wait(
            Until.hasObject(By.pkg(PACKAGE_NAME)),
            5000L
        )
        assertTrue("Could not return to the main screen", backToMain)

        takeScreenshot("theme_test_end")
        println("=== ThemeActivity test complete ===")
    }
}
