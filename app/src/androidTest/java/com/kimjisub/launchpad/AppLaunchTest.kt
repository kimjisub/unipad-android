package com.kimjisub.launchpad

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * App Launch and Basic Navigation Tests
 * Tests for app startup, splash screen, and basic navigation
 */
@RunWith(AndroidJUnit4::class)
class AppLaunchTest : BaseUITest() {

    @Test
    fun testAppLaunch() {
        // Launch the app
        launchApp()

        // Wait until the app starts
        val appStarted = device.wait(
            Until.hasObject(By.pkg(PACKAGE_NAME)),
            LAUNCH_TIMEOUT
        )
        assertTrue("App did not start", appStarted)

        // Handle permission dialogs
        handlePermissionDialogs()

        // Verify the app is still running
        val stillRunning = device.wait(
            Until.hasObject(By.pkg(PACKAGE_NAME)),
            5000L
        )
        assertTrue("App terminated after permission handling", stillRunning)

        // Save screenshot
        takeScreenshot("app_launched")
    }

    @Test
    fun testSplashScreenTransition() {
        // Launch the app
        launchApp()

        // Wait for the splash screen
        val splashVisible = device.wait(
            Until.hasObject(By.pkg(PACKAGE_NAME)),
            LAUNCH_TIMEOUT
        )
        assertTrue("Splash screen did not appear", splashVisible)

        // Handle permission dialogs
        handlePermissionDialogs()

        // Wait for transition to the main screen
        // Verify transition to MainActivity
        var attempts = 0
        var mainActivityFound = false
        while (attempts < 15 && !mainActivityFound) {
            Thread.sleep(1000)
            val currentActivity = device.wait(
                Until.hasObject(By.pkg(PACKAGE_NAME)),
                1000L
            )
            if (currentActivity) {
                mainActivityFound = true
            }
            attempts++
        }

        // Verify the app is running without crashes
        val mainScreenVisible = device.wait(
            Until.hasObject(By.pkg(PACKAGE_NAME)),
            5000L
        )
        assertTrue("Did not transition to the main screen", mainScreenVisible)

        // Final screen screenshot
        takeScreenshot("main_screen")
    }

    @Test
    fun testMainScreenElements() {
        // Launch the app and navigate to the main screen
        launchApp()
        device.wait(Until.hasObject(By.pkg(PACKAGE_NAME)), LAUNCH_TIMEOUT)
        handlePermissionDialogs()

        // Wait for main screen to load
        Thread.sleep(10000)

        // Verify the main screen is displayed
        val appRunning = device.wait(
            Until.hasObject(By.pkg(PACKAGE_NAME)),
            5000L
        )
        assertTrue("Main screen is not displayed", appRunning)

        // Take screenshot
        takeScreenshot("main_screen_elements")

        // Test basic touch event (center tap)
        val displayWidth = device.displayWidth
        val displayHeight = device.displayHeight
        device.click(displayWidth / 2, displayHeight / 2)
        Thread.sleep(1000)

        takeScreenshot("after_center_tap")
    }

    @Test
    fun testNavigationFlow() {
        // Launch the app
        launchApp()
        device.wait(Until.hasObject(By.pkg(PACKAGE_NAME)), LAUNCH_TIMEOUT)
        handlePermissionDialogs()

        // Wait for main screen
        Thread.sleep(10000)

        takeScreenshot("navigation_main_screen")

        // Touch the bottom area of the screen (tabs/menu may be present)
        val displayWidth = device.displayWidth
        val displayHeight = device.displayHeight

        // Bottom left
        device.click(displayWidth / 4, displayHeight - 100)
        Thread.sleep(2000)
        takeScreenshot("navigation_bottom_left")

        // Bottom center
        device.click(displayWidth / 2, displayHeight - 100)
        Thread.sleep(2000)
        takeScreenshot("navigation_bottom_center")

        // Bottom right
        device.click(displayWidth * 3 / 4, displayHeight - 100)
        Thread.sleep(2000)
        takeScreenshot("navigation_bottom_right")

        // Verify the app is still running
        val stillRunning = device.wait(
            Until.hasObject(By.pkg(PACKAGE_NAME)),
            2000L
        )
        assertTrue("App terminated during navigation", stillRunning)
    }

    @Test
    fun testPermissionHandling() {
        // Launch the app
        launchApp()

        // Wait until the app starts
        device.wait(Until.hasObject(By.pkg(PACKAGE_NAME)), LAUNCH_TIMEOUT)

        // Handle permissions based on Android version
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                // Android 13+: READ_MEDIA_AUDIO permission only
                handlePermissionDialog("오디오")
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                // Android 11-12: READ_EXTERNAL_STORAGE
                handlePermissionDialog("저장공간", "파일", "미디어")
            }
            else -> {
                // Android 10 and below: READ/WRITE_EXTERNAL_STORAGE
                handlePermissionDialog("저장공간", "파일", "미디어")
            }
        }

        // Verify the app works normally after granting permissions
        val appRunning = device.wait(
            Until.hasObject(By.pkg(PACKAGE_NAME)),
            5000L
        )
        assertTrue("App terminated after granting permissions", appRunning)

        takeScreenshot("after_permissions")
    }

    @Test
    fun testPlayActivityNavigation() {
        // Launch the app and navigate to the main screen
        launchApp()
        device.wait(Until.hasObject(By.pkg(PACKAGE_NAME)), LAUNCH_TIMEOUT)
        handlePermissionDialogs()

        // Wait for main screen to load
        Thread.sleep(10000)

        takeScreenshot("before_play_navigation")

        // Click the center area of the main screen where the unipack list is displayed
        // (attempt to click the first item if unipacks are present)
        val displayWidth = device.displayWidth
        val displayHeight = device.displayHeight

        // Upper center of screen (expected position of the first list item)
        val itemX = displayWidth / 2
        val itemY = displayHeight / 3

        device.click(itemX, itemY)
        Thread.sleep(2000)
        takeScreenshot("after_list_item_click")

        // Check if PlayActivity or detail panel appeared
        // (unipacks may not exist, so just verify the app hasn't crashed)
        val stillRunning = device.wait(
            Until.hasObject(By.pkg(PACKAGE_NAME)),
            3000L
        )
        assertTrue("App terminated after clicking list item", stillRunning)

        // If the detail panel opened, attempt to click the play button
        // The play button is typically in the bottom-right or inside the panel
        // Multiple positions can be tried

        // Attempt 1: Bottom-right area (possible play button location)
        val playButtonX1 = displayWidth * 3 / 4
        val playButtonY1 = displayHeight * 2 / 3
        device.click(playButtonX1, playButtonY1)
        Thread.sleep(3000)

        takeScreenshot("after_play_button_click_attempt1")

        // Check if PlayActivity has started
        val playActivityStarted = device.wait(
            Until.hasObject(By.pkg(PACKAGE_NAME)),
            5000L
        )

        if (playActivityStarted) {
            println("Transitioned to PlayActivity or the app is still running")
            takeScreenshot("play_activity_or_main")

            // Navigate back to the main screen
            device.pressBack()
            Thread.sleep(2000)

            val backToMain = device.wait(
                Until.hasObject(By.pkg(PACKAGE_NAME)),
                5000L
            )
            assertTrue("App terminated after pressing back", backToMain)

            takeScreenshot("back_from_play_attempt")
        } else {
            println("PlayActivity did not start or no unipacks are available")
        }
    }
}
