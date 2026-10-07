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
        launchToMainActivity()

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
        // launchApp inside asserts that Splash, the launcher activity, was created
        launchToMainActivity()

        // Handle permission dialogs
        handlePermissionDialogs()

        assertTrue("Did not transition to the main screen", waitForMainScreen())

        // Final screen screenshot
        takeScreenshot("main_screen")
    }

    @Test
    fun testMainScreenElements() {
        launchToMainScreen()
        assertTrue("Test pack is not listed", device.hasObject(By.text(TestUniPack.TITLE)))

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
        launchToMainScreen()
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
        launchToMainActivity()

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
        launchToMainScreen()
        takeScreenshot("before_play_navigation")

        // Selecting a pack opens its detail panel; its Play flag starts PlayActivity
        selectTestPack()
        takeScreenshot("after_list_item_click")

        openTestPackInPlay()
        takeScreenshot("play_activity_opened")

        // Back in PlayActivity opens the option panel instead of leaving
        device.pressBack()
        assertTrue("Back did not open the play option panel", waitUntil(5000L) { isPlayOptionsOpen() })
        takeScreenshot("play_back_opens_options")

        quitPlayToMain()
        takeScreenshot("back_from_play")
    }
}
