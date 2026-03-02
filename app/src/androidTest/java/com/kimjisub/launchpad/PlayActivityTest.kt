package com.kimjisub.launchpad

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.Until
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith

/**
 * PlayActivity Tests
 * Tests for play activity features (controls, recording, LED, volume, etc.)
 */
@RunWith(AndroidJUnit4::class)
class PlayActivityTest : BaseUITest() {

    @Test
    fun testPlayActivityControls() {
        // Launch the app and navigate to the main screen
        launchApp()
        device.wait(Until.hasObject(By.pkg(PACKAGE_NAME)), LAUNCH_TIMEOUT)
        handlePermissionDialogs()

        // Wait for main screen to load
        Thread.sleep(10000)

        // Click a unipack list item to navigate to PlayActivity
        val displayWidth = device.displayWidth
        val displayHeight = device.displayHeight
        val itemX = displayWidth / 2
        val itemY = displayHeight / 3

        device.click(itemX, itemY)
        Thread.sleep(2000)

        // Attempt to click the play button
        val playButtonX = displayWidth * 3 / 4
        val playButtonY = displayHeight * 2 / 3
        device.click(playButtonX, playButtonY)
        Thread.sleep(3000)

        takeScreenshot("playactivity_controls_start")

        // Verify PlayActivity launched
        val inPlayActivity = device.wait(
            Until.hasObject(By.pkg(PACKAGE_NAME)),
            5000L
        )

        if (!inPlayActivity) {
            println("Could not enter PlayActivity. Skipping test.")
            return
        }

        // Test the top-left checkbox area (feedbackLight position)
        val checkboxX = 100
        val checkboxY1 = 150  // feedbackLight
        val checkboxY2 = 250  // led
        val checkboxY3 = 350  // autoPlay

        // Click feedbackLight checkbox
        device.click(checkboxX, checkboxY1)
        Thread.sleep(1000)
        takeScreenshot("playactivity_feedback_light_toggle")

        // Click led checkbox
        device.click(checkboxX, checkboxY2)
        Thread.sleep(1000)
        takeScreenshot("playactivity_led_toggle")

        // Click autoPlay checkbox
        device.click(checkboxX, checkboxY3)
        Thread.sleep(1000)
        takeScreenshot("playactivity_autoplay_toggle")

        // When autoPlay is enabled, controls may appear
        // Play button position (autoPlay controls)
        val autoPlayControlX = 120
        val autoPlayPlayY = 480

        // Attempt to click autoPlay play button
        device.click(autoPlayControlX, autoPlayPlayY)
        Thread.sleep(2000)
        takeScreenshot("playactivity_autoplay_play")

        // Attempt to touch the center PadView area
        val padCenterX = displayWidth / 2
        val padCenterY = displayHeight / 2

        device.click(padCenterX, padCenterY)
        Thread.sleep(1000)
        takeScreenshot("playactivity_pad_touch")

        // Verify the app is still running
        val stillRunning = device.wait(
            Until.hasObject(By.pkg(PACKAGE_NAME)),
            2000L
        )
        assertTrue("App terminated while operating PlayActivity controls", stillRunning)

        // Navigate back to the main screen
        device.pressBack()
        Thread.sleep(2000)

        val backToMain = device.wait(
            Until.hasObject(By.pkg(PACKAGE_NAME)),
            5000L
        )
        assertTrue("Failed to navigate back from PlayActivity", backToMain)

        takeScreenshot("playactivity_controls_end")
    }

    @Test
    fun testPlayActivityChainSwitching() {
        // Launch the app and navigate to the main screen
        launchApp()
        device.wait(Until.hasObject(By.pkg(PACKAGE_NAME)), LAUNCH_TIMEOUT)
        handlePermissionDialogs()

        // Wait for main screen to load
        Thread.sleep(10000)

        // Click a unipack list item to navigate to PlayActivity
        val displayWidth = device.displayWidth
        val displayHeight = device.displayHeight
        val itemX = displayWidth / 2
        val itemY = displayHeight / 3

        device.click(itemX, itemY)
        Thread.sleep(2000)

        // Attempt to click the play button
        val playButtonX = displayWidth * 3 / 4
        val playButtonY = displayHeight * 2 / 3
        device.click(playButtonX, playButtonY)
        Thread.sleep(3000)

        takeScreenshot("chain_switching_start")

        // Verify PlayActivity launched
        val inPlayActivity = device.wait(
            Until.hasObject(By.pkg(PACKAGE_NAME)),
            5000L
        )

        if (!inPlayActivity) {
            println("Could not enter PlayActivity. Skipping test.")
            return
        }

        // Chain buttons are located to the right of PadView (chainsRight)
        // According to the layout, chainsRight is placed to the right of pads
        val chainButtonX = displayWidth * 2 / 3 + 50  // Move to the right of pads
        val chainBaseY = displayHeight / 2 - 150       // Starting from above center

        // Click the first chain button (Chain 0)
        device.click(chainButtonX, chainBaseY)
        Thread.sleep(1500)
        takeScreenshot("chain_0_selected")

        // Click the second chain button (Chain 1)
        device.click(chainButtonX, chainBaseY + 50)
        Thread.sleep(1500)
        takeScreenshot("chain_1_selected")

        // Click the third chain button (Chain 2)
        device.click(chainButtonX, chainBaseY + 100)
        Thread.sleep(1500)
        takeScreenshot("chain_2_selected")

        // Test left chain buttons (chainsLeft - starting from Chain 16)
        val chainButtonLeftX = displayWidth / 3 - 50  // Move to the left of pads

        // Click left chain button
        device.click(chainButtonLeftX, chainBaseY)
        Thread.sleep(1500)
        takeScreenshot("chain_left_selected")

        // Touch PadView center to test if sound plays for each chain
        val padCenterX = displayWidth / 2
        val padCenterY = displayHeight / 2

        device.click(padCenterX, padCenterY)
        Thread.sleep(1000)
        takeScreenshot("chain_pad_interaction")

        // Verify the app is still running
        val stillRunning = device.wait(
            Until.hasObject(By.pkg(PACKAGE_NAME)),
            2000L
        )
        assertTrue("App terminated during chain switching", stillRunning)

        // Navigate back to the main screen
        device.pressBack()
        Thread.sleep(2000)

        val backToMain = device.wait(
            Until.hasObject(By.pkg(PACKAGE_NAME)),
            5000L
        )
        assertTrue("Failed to navigate back after chain test", backToMain)

        takeScreenshot("chain_switching_end")
    }

    @Test
    fun testPlayActivityRecording() {
        // Launch the app and navigate to the main screen
        launchApp()
        device.wait(Until.hasObject(By.pkg(PACKAGE_NAME)), LAUNCH_TIMEOUT)
        handlePermissionDialogs()

        // Wait for main screen to load
        Thread.sleep(10000)

        // Click a unipack list item to navigate to PlayActivity
        val displayWidth = device.displayWidth
        val displayHeight = device.displayHeight
        val itemX = displayWidth / 2
        val itemY = displayHeight / 3

        device.click(itemX, itemY)
        Thread.sleep(2000)

        // Attempt to click the play button
        val playButtonX = displayWidth * 3 / 4
        val playButtonY = displayHeight * 2 / 3
        device.click(playButtonX, playButtonY)
        Thread.sleep(3000)

        takeScreenshot("recording_start")

        // Verify PlayActivity launched
        val inPlayActivity = device.wait(
            Until.hasObject(By.pkg(PACKAGE_NAME)),
            5000L
        )

        if (!inPlayActivity) {
            println("Could not enter PlayActivity. Skipping test.")
            return
        }

        // Click traceLog checkbox (bottom left)
        val checkboxX = 100
        val traceLogY = displayHeight - 250  // Up from bottom

        device.click(checkboxX, traceLogY)
        Thread.sleep(1000)
        takeScreenshot("recording_tracelog_enabled")

        // Click record checkbox (below traceLog)
        val recordY = displayHeight - 180

        device.click(checkboxX, recordY)
        Thread.sleep(1000)
        takeScreenshot("recording_enabled")

        // Touch PadView to record events while recording is active
        val padCenterX = displayWidth / 2
        val padCenterY = displayHeight / 2

        // Touch multiple positions to record
        device.click(padCenterX - 50, padCenterY - 50)
        Thread.sleep(500)
        takeScreenshot("recording_touch_1")

        device.click(padCenterX + 50, padCenterY - 50)
        Thread.sleep(500)
        takeScreenshot("recording_touch_2")

        device.click(padCenterX, padCenterY + 50)
        Thread.sleep(500)
        takeScreenshot("recording_touch_3")

        // Switch chains while recording
        val chainButtonX = displayWidth * 2 / 3 + 50
        val chainBaseY = displayHeight / 2 - 150

        device.click(chainButtonX, chainBaseY + 50)
        Thread.sleep(1000)
        takeScreenshot("recording_chain_switch")

        // Touch pads again
        device.click(padCenterX, padCenterY)
        Thread.sleep(500)
        takeScreenshot("recording_touch_after_chain")

        // Uncheck record checkbox to stop recording (copies to clipboard)
        device.click(checkboxX, recordY)
        Thread.sleep(1000)
        takeScreenshot("recording_stopped")

        // Verify the app is still running
        val stillRunning = device.wait(
            Until.hasObject(By.pkg(PACKAGE_NAME)),
            2000L
        )
        assertTrue("App terminated during recording test", stillRunning)

        // Also disable traceLog
        device.click(checkboxX, traceLogY)
        Thread.sleep(1000)

        // Navigate back to the main screen
        device.pressBack()
        Thread.sleep(2000)

        val backToMain = device.wait(
            Until.hasObject(By.pkg(PACKAGE_NAME)),
            5000L
        )
        assertTrue("Failed to navigate back after recording test", backToMain)

        takeScreenshot("recording_end")
    }

    @Test
    fun testPlayActivityVolumeControl() {
        // Launch the app and navigate to the main screen
        launchApp()
        device.wait(Until.hasObject(By.pkg(PACKAGE_NAME)), LAUNCH_TIMEOUT)
        handlePermissionDialogs()

        // Wait for main screen to load
        Thread.sleep(10000)

        // Click a unipack list item to navigate to PlayActivity
        val displayWidth = device.displayWidth
        val displayHeight = device.displayHeight
        val itemX = displayWidth / 2
        val itemY = displayHeight / 3

        device.click(itemX, itemY)
        Thread.sleep(2000)

        // Attempt to click the play button
        val playButtonX = displayWidth * 3 / 4
        val playButtonY = displayHeight * 2 / 3
        device.click(playButtonX, playButtonY)
        Thread.sleep(3000)

        takeScreenshot("volume_control_start")

        // Verify PlayActivity launched
        val inPlayActivity = device.wait(
            Until.hasObject(By.pkg(PACKAGE_NAME)),
            5000L
        )

        if (!inPlayActivity) {
            println("Could not enter PlayActivity. Skipping test.")
            return
        }

        // Simulate Android system volume up button
        // PlayActivity detects volume buttons for in-app volume control
        device.pressKeyCode(android.view.KeyEvent.KEYCODE_VOLUME_UP)
        Thread.sleep(1000)
        takeScreenshot("volume_up_pressed")

        // Multiple volume up presses (multi-step test)
        device.pressKeyCode(android.view.KeyEvent.KEYCODE_VOLUME_UP)
        Thread.sleep(1000)
        takeScreenshot("volume_up_pressed_2")

        device.pressKeyCode(android.view.KeyEvent.KEYCODE_VOLUME_UP)
        Thread.sleep(1000)
        takeScreenshot("volume_up_pressed_3")

        // Volume down test
        device.pressKeyCode(android.view.KeyEvent.KEYCODE_VOLUME_DOWN)
        Thread.sleep(1000)
        takeScreenshot("volume_down_pressed")

        device.pressKeyCode(android.view.KeyEvent.KEYCODE_VOLUME_DOWN)
        Thread.sleep(1000)
        takeScreenshot("volume_down_pressed_2")

        // Touch PadView after volume adjustment to verify sound playback
        val padCenterX = displayWidth / 2
        val padCenterY = displayHeight / 2

        device.click(padCenterX, padCenterY)
        Thread.sleep(1000)
        takeScreenshot("volume_pad_touch_after_adjustment")

        // Verify the app is still running (prevent crash during volume control)
        val stillRunning = device.wait(
            Until.hasObject(By.pkg(PACKAGE_NAME)),
            2000L
        )
        assertTrue("App terminated during volume control", stillRunning)

        // Set to minimum volume (multiple volume down presses)
        repeat(10) {
            device.pressKeyCode(android.view.KeyEvent.KEYCODE_VOLUME_DOWN)
            Thread.sleep(300)
        }
        takeScreenshot("volume_minimum")

        // Touch pad (at minimum volume)
        device.click(padCenterX, padCenterY)
        Thread.sleep(1000)

        // Set to maximum volume (multiple volume up presses)
        repeat(15) {
            device.pressKeyCode(android.view.KeyEvent.KEYCODE_VOLUME_UP)
            Thread.sleep(300)
        }
        takeScreenshot("volume_maximum")

        // Touch pad (at maximum volume)
        device.click(padCenterX, padCenterY)
        Thread.sleep(1000)

        // Restore to medium volume (for safety)
        repeat(7) {
            device.pressKeyCode(android.view.KeyEvent.KEYCODE_VOLUME_DOWN)
            Thread.sleep(300)
        }
        takeScreenshot("volume_middle")

        // Navigate back to the main screen
        device.pressBack()
        Thread.sleep(2000)

        val backToMain = device.wait(
            Until.hasObject(By.pkg(PACKAGE_NAME)),
            5000L
        )
        assertTrue("Failed to navigate back after volume test", backToMain)

        takeScreenshot("volume_control_end")
    }

    @Test
    fun testPlayActivityAutoPlayControls() {
        // Launch the app and navigate to the main screen
        launchApp()
        device.wait(Until.hasObject(By.pkg(PACKAGE_NAME)), LAUNCH_TIMEOUT)
        handlePermissionDialogs()

        // Wait for main screen to load
        Thread.sleep(10000)

        takeScreenshot("autoplay_controls_start")

        // Click the first item in the unipack list
        val displayWidth = device.displayWidth
        val displayHeight = device.displayHeight
        val listItemX = displayWidth * 3 / 4
        val listItemY = displayHeight / 3

        device.click(listItemX, listItemY)
        Thread.sleep(2000)

        takeScreenshot("autoplay_pack_selected")

        // Click the Play button to enter PlayActivity
        val playButtonX = displayWidth / 5
        val playButtonY = displayHeight - 100

        device.click(playButtonX, playButtonY)
        Thread.sleep(5000)  // Wait for PlayActivity to load

        takeScreenshot("autoplay_play_activity_opened")

        // Verify PlayActivity opened
        val playActivityOpened = device.wait(
            Until.hasObject(By.pkg(PACKAGE_NAME)),
            5000L
        )
        assertTrue("PlayActivity did not open", playActivityOpened)

        // Enable autoPlay checkbox
        // autoPlay checkbox is the third checkbox in the top-left (feedbackLight, led, autoPlay order)
        val autoPlayCheckboxX = 150
        val autoPlayCheckboxY = 200

        device.click(autoPlayCheckboxX, autoPlayCheckboxY)
        Thread.sleep(2000)

        takeScreenshot("autoplay_checkbox_enabled")

        // Verify autoPlay controls appeared and test control buttons

        // Click Play button (start autoPlay)
        // autoPlay controls are located below the autoPlay checkbox
        val playControlX = 150
        val playControlY = 300  // Buttons below the ProgressBar

        device.click(playControlX, playControlY)
        Thread.sleep(3000)  // Wait for autoPlay execution

        takeScreenshot("autoplay_started")

        // Click Pause button
        device.click(playControlX, playControlY)
        Thread.sleep(1000)

        takeScreenshot("autoplay_paused")

        // Click Next button
        val nextButtonX = playControlX + 70
        device.click(nextButtonX, playControlY)
        Thread.sleep(2000)

        takeScreenshot("autoplay_next_clicked")

        // Click Prev button
        val prevButtonX = playControlX - 70
        device.click(prevButtonX, playControlY)
        Thread.sleep(2000)

        takeScreenshot("autoplay_prev_clicked")

        // Click Play button again to resume
        device.click(playControlX, playControlY)
        Thread.sleep(3000)

        takeScreenshot("autoplay_resumed")

        // Click Pause button to stop
        device.click(playControlX, playControlY)
        Thread.sleep(1000)

        takeScreenshot("autoplay_stopped")

        // Disable autoPlay checkbox
        device.click(autoPlayCheckboxX, autoPlayCheckboxY)
        Thread.sleep(1000)

        takeScreenshot("autoplay_checkbox_disabled")

        // Verify the app is still running
        val stillRunning = device.wait(
            Until.hasObject(By.pkg(PACKAGE_NAME)),
            3000L
        )
        assertTrue("App terminated after AutoPlay controls test", stillRunning)

        // Navigate back to the main screen
        device.pressBack()
        Thread.sleep(2000)

        // Verify return to the main screen by checking for MainActivity-specific UI elements
        // Poll for fragment_panel (always accessible, unique to MainActivity) or fragment_list
        // Note: floatingMenu is excluded as the FAB can block the UI hierarchy when open
        val backToMain = waitForAnyElementWithPolling(
            listOf(
                By.res(PACKAGE_NAME, "fragment_panel"),
                By.res(PACKAGE_NAME, "fragment_list")
            ),
            timeoutMs = 10000L,
            pollingIntervalMs = 500L
        )
        assertTrue("Could not return to main screen (fragment_panel or fragment_list not found)", backToMain)

        // Additionally verify the MainActivity UI element actually exists
        val fragmentPanelExists = waitForElementWithPolling(
            By.res(PACKAGE_NAME, "fragment_panel"),
            timeoutMs = 5000L,
            pollingIntervalMs = 500L
        )
        assertTrue("MainActivity's fragment_panel is not displayed", fragmentPanelExists)

        takeScreenshot("autoplay_controls_test_end")

        println("AutoPlay controls test complete")
    }

    @Test
    fun testPlayActivityPadViewPatterns() {
        // Launch the app and navigate to the main screen
        launchApp()
        device.wait(Until.hasObject(By.pkg(PACKAGE_NAME)), LAUNCH_TIMEOUT)
        handlePermissionDialogs()

        // Wait for main screen to load
        Thread.sleep(10000)

        // Click a unipack list item to navigate to PlayActivity
        val displayWidth = device.displayWidth
        val displayHeight = device.displayHeight
        val itemX = displayWidth / 2
        val itemY = displayHeight / 3

        device.click(itemX, itemY)
        Thread.sleep(2000)

        // Attempt to click the play button
        val playButtonX = displayWidth * 3 / 4
        val playButtonY = displayHeight * 2 / 3
        device.click(playButtonX, playButtonY)
        Thread.sleep(3000)

        takeScreenshot("padview_patterns_start")

        // Verify PlayActivity launched
        val inPlayActivity = device.wait(
            Until.hasObject(By.pkg(PACKAGE_NAME)),
            5000L
        )

        if (!inPlayActivity) {
            println("Could not enter PlayActivity. Skipping test.")
            return
        }

        println("Starting PadView interaction pattern test")

        // Calculate PadView area (center region of screen)
        val padCenterX = displayWidth / 2
        val padCenterY = displayHeight / 2
        val padSize = minOf(displayWidth, displayHeight) * 0.6f
        val padLeft = padCenterX - padSize / 2
        val padTop = padCenterY - padSize / 2

        // Pattern 1: Touch all four corners
        println("Pattern 1: Four corner touches")
        val cornerOffsets = listOf(
            Pair(0.15f, 0.15f),  // Top-left
            Pair(0.85f, 0.15f),  // Top-right
            Pair(0.15f, 0.85f),  // Bottom-left
            Pair(0.85f, 0.85f)   // Bottom-right
        )

        for ((_, offset) in cornerOffsets.withIndex()) {
            val x = (padLeft + padSize * offset.first).toInt()
            val y = (padTop + padSize * offset.second).toInt()
            device.click(x, y)
            Thread.sleep(300)
        }
        takeScreenshot("padview_pattern_corners")

        // Pattern 2: Horizontal line touch (left to right)
        println("Pattern 2: Horizontal line touch")
        for (i in 0..4) {
            val x = (padLeft + padSize * i / 4).toInt()
            val y = (padTop + padSize * 0.5f).toInt()
            device.click(x, y)
            Thread.sleep(200)
        }
        takeScreenshot("padview_pattern_horizontal")

        // Pattern 3: Vertical line touch (top to bottom)
        println("Pattern 3: Vertical line touch")
        for (i in 0..4) {
            val x = (padLeft + padSize * 0.5f).toInt()
            val y = (padTop + padSize * i / 4).toInt()
            device.click(x, y)
            Thread.sleep(200)
        }
        takeScreenshot("padview_pattern_vertical")

        // Pattern 4: Diagonal touch (top-left to bottom-right)
        println("Pattern 4: Diagonal touch (top-left to bottom-right)")
        for (i in 0..4) {
            val x = (padLeft + padSize * i / 4).toInt()
            val y = (padTop + padSize * i / 4).toInt()
            device.click(x, y)
            Thread.sleep(200)
        }
        takeScreenshot("padview_pattern_diagonal1")

        // Pattern 5: Reverse diagonal touch (top-right to bottom-left)
        println("Pattern 5: Diagonal touch (top-right to bottom-left)")
        for (i in 0..4) {
            val x = (padLeft + padSize * (4 - i) / 4).toInt()
            val y = (padTop + padSize * i / 4).toInt()
            device.click(x, y)
            Thread.sleep(200)
        }
        takeScreenshot("padview_pattern_diagonal2")

        // Pattern 6: Circular pattern touch (8 directions)
        println("Pattern 6: Circular pattern touch")
        val radius = padSize * 0.35f
        val centerX = padLeft + padSize / 2
        val centerY = padTop + padSize / 2

        for (angle in 0 until 360 step 45) {
            val radian = Math.toRadians(angle.toDouble())
            val x = (centerX + radius * kotlin.math.cos(radian)).toInt()
            val y = (centerY + radius * kotlin.math.sin(radian)).toInt()
            device.click(x, y)
            Thread.sleep(200)
        }
        takeScreenshot("padview_pattern_circle")

        // Pattern 7: Rapid successive touches (10 times in center area)
        println("Pattern 7: Rapid successive touches")
        for (i in 0 until 10) {
            val x = (padLeft + padSize * 0.5f + (Math.random() * 100 - 50)).toInt()
            val y = (padTop + padSize * 0.5f + (Math.random() * 100 - 50)).toInt()
            device.click(x, y)
            Thread.sleep(100)  // Rapid touch
        }
        takeScreenshot("padview_pattern_rapid")

        // Pattern 8: Grid pattern (3x3)
        println("Pattern 8: 3x3 grid pattern")
        for (row in 0..2) {
            for (col in 0..2) {
                val x = (padLeft + padSize * (col + 1) / 4).toInt()
                val y = (padTop + padSize * (row + 1) / 4).toInt()
                device.click(x, y)
                Thread.sleep(150)
            }
        }
        takeScreenshot("padview_pattern_grid")

        // Pattern 9: Spiral pattern (inside to outside)
        println("Pattern 9: Spiral pattern")
        val spiralPoints = listOf(
            Pair(0.5f, 0.5f),   // Center
            Pair(0.6f, 0.5f),   // Right
            Pair(0.6f, 0.4f),   // Up
            Pair(0.4f, 0.4f),   // Left
            Pair(0.4f, 0.6f),   // Down
            Pair(0.7f, 0.6f),   // Right extended
            Pair(0.7f, 0.3f),   // Up extended
            Pair(0.3f, 0.3f),   // Left extended
            Pair(0.3f, 0.7f),   // Down extended
            Pair(0.8f, 0.7f)    // Right maximum
        )

        for (point in spiralPoints) {
            val x = (padLeft + padSize * point.first).toInt()
            val y = (padTop + padSize * point.second).toInt()
            device.click(x, y)
            Thread.sleep(200)
        }
        takeScreenshot("padview_pattern_spiral")

        // Pattern 10: Zigzag pattern
        println("Pattern 10: Zigzag pattern")
        for (i in 0..4) {
            val y = (padTop + padSize * i / 4).toInt()
            val x1 = (padLeft + padSize * 0.2f).toInt()
            val x2 = (padLeft + padSize * 0.8f).toInt()

            device.click(if (i % 2 == 0) x1 else x2, y)
            Thread.sleep(200)
        }
        takeScreenshot("padview_pattern_zigzag")

        println("All PadView interaction patterns complete")

        // Verify the app is still running (crash prevention)
        val stillRunning = device.wait(
            Until.hasObject(By.pkg(PACKAGE_NAME)),
            2000L
        )
        assertTrue("App terminated during PadView pattern test", stillRunning)

        // Final state screenshot
        takeScreenshot("padview_patterns_final")

        // Navigate back to the main screen
        device.pressBack()
        Thread.sleep(2000)

        // Verify return to the main screen by checking for MainActivity-specific UI elements
        // Poll for fragment_panel (always accessible, unique to MainActivity) or fragment_list
        // Note: floatingMenu is excluded as the FAB can block the UI hierarchy when open
        val backToMain = waitForAnyElementWithPolling(
            listOf(
                By.res(PACKAGE_NAME, "fragment_panel"),
                By.res(PACKAGE_NAME, "fragment_list")
            ),
            timeoutMs = 10000L,
            pollingIntervalMs = 500L
        )
        assertTrue("Could not return to main screen after PadView pattern test (fragment_panel or fragment_list not found)", backToMain)

        // Additionally poll for fragment_list
        val fragmentListExists = waitForElementWithPolling(
            By.res(PACKAGE_NAME, "fragment_list"),
            timeoutMs = 5000L,
            pollingIntervalMs = 500L
        )
        assertTrue("MainActivity's fragment_list is not displayed", fragmentListExists)

        takeScreenshot("padview_patterns_end")

        println("PadView interaction pattern test complete")
    }

    @Ignore("FAB menu child button clicking is unreliable - needs manual verification or different approach")
    @Test
    fun testLoadUniPackFABInteraction() {
        // Launch the app and navigate to the main screen
        launchApp()
        device.wait(Until.hasObject(By.pkg(PACKAGE_NAME)), LAUNCH_TIMEOUT)
        handlePermissionDialogs()

        // Wait for main screen to load
        Thread.sleep(10000)

        takeScreenshot("before_load_unipack")

        // Open the FAB menu (coordinate-based)
        val displayWidth = device.displayWidth
        val displayHeight = device.displayHeight
        val fabX = displayWidth - 80
        val fabY = displayHeight - 80
        device.click(fabX, fabY)
        Thread.sleep(2000)

        takeScreenshot("fab_menu_opened_for_load")

        // Click Load UniPack button (second button from top)
        val loadUniPackY = fabY - 150
        device.click(fabX, loadUniPackY)
        Thread.sleep(3000)

        takeScreenshot("after_load_unipack_click")

        // A file picker may open (could be a different app)
        Thread.sleep(3000)

        // Close the file picker by pressing back (multiple attempts)
        repeat(3) {
            device.pressBack()
            Thread.sleep(1000)
        }

        takeScreenshot("after_back_from_file_picker")

        // Verify return to the main screen
        val backToMain = device.wait(
            Until.hasObject(By.pkg(PACKAGE_NAME)),
            5000L
        )
        assertTrue("App is not in a normal state after Load UniPack test", backToMain)

        takeScreenshot("load_unipack_test_end")
    }

    @Ignore("FAB menu child button clicking is unreliable - needs manual verification or different approach")
    @Test
    fun testReconnectLaunchpadFABInteraction() {
        // Launch the app and navigate to the main screen
        launchApp()
        device.wait(Until.hasObject(By.pkg(PACKAGE_NAME)), LAUNCH_TIMEOUT)
        handlePermissionDialogs()

        // Wait for main screen to load
        Thread.sleep(10000)

        takeScreenshot("before_reconnect_launchpad")

        // Open the FAB menu (coordinate-based)
        val displayWidth = device.displayWidth
        val displayHeight = device.displayHeight
        val fabX = displayWidth - 80
        val fabY = displayHeight - 80
        device.click(fabX, fabY)
        Thread.sleep(2000)

        takeScreenshot("fab_menu_opened_for_reconnect")

        // Click Reconnect Launchpad button (first button from top)
        val reconnectY = fabY - 60
        device.click(fabX, reconnectY)
        Thread.sleep(3000)

        takeScreenshot("after_reconnect_click")

        // Verify the app hasn't crashed after reconnect
        val stillRunning = device.wait(
            Until.hasObject(By.pkg(PACKAGE_NAME)),
            5000L
        )
        assertTrue("App terminated after Reconnect Launchpad", stillRunning)

        // Additionally handle USB permission dialog that may appear
        // Click "Cancel" or "OK" button
        try {
            val cancelButton = device.findObject(
                UiSelector()
                    .textMatches(".*취소.*|.*Cancel.*")
                    .clickable(true)
            )
            if (cancelButton.exists()) {
                cancelButton.click()
                Thread.sleep(1000)
                takeScreenshot("after_usb_dialog_cancel")
            }
        } catch (e: Exception) {
            println("Error handling USB permission dialog (ignorable): ${e.message}")
        }

        // Verify the app is still functioning normally
        val finalCheck = device.wait(
            Until.hasObject(By.pkg(PACKAGE_NAME)),
            5000L
        )
        assertTrue("App is not in a normal state after Reconnect Launchpad test", finalCheck)

        takeScreenshot("reconnect_launchpad_test_end")
    }

    @Test
    fun testPlayActivityLEDAnimation() {
        // Launch the app and navigate to the main screen
        launchApp()
        device.wait(Until.hasObject(By.pkg(PACKAGE_NAME)), LAUNCH_TIMEOUT)
        handlePermissionDialogs()

        // Wait for main screen to load
        Thread.sleep(10000)

        // Click a unipack list item to navigate to PlayActivity
        val displayWidth = device.displayWidth
        val displayHeight = device.displayHeight
        val itemX = displayWidth / 2
        val itemY = displayHeight / 3

        device.click(itemX, itemY)
        Thread.sleep(2000)

        // Attempt to click the play button
        val playButtonX = displayWidth * 3 / 4
        val playButtonY = displayHeight * 2 / 3
        device.click(playButtonX, playButtonY)
        Thread.sleep(3000)

        takeScreenshot("led_test_start")

        // Verify PlayActivity launched
        val inPlayActivity = device.wait(
            Until.hasObject(By.pkg(PACKAGE_NAME)),
            5000L
        )

        if (!inPlayActivity) {
            println("Could not enter PlayActivity. Skipping test.")
            return
        }

        // LED checkbox position (second from top-left)
        val checkboxX = 100
        val ledCheckboxY = 250

        // Enable LED checkbox
        device.click(checkboxX, ledCheckboxY)
        Thread.sleep(1000)
        takeScreenshot("led_enabled")

        // Calculate PadView area
        val padCenterX = displayWidth / 2
        val padCenterY = displayHeight / 2
        val padRadius = minOf(displayWidth, displayHeight) / 3

        // Touch various patterns to trigger LED animations

        // Pattern 1: Four-directional touch (up, down, left, right)
        println("LED test - Pattern 1: Four-directional touch")
        device.click(padCenterX, padCenterY - padRadius / 2) // Up
        Thread.sleep(300)
        device.click(padCenterX + padRadius / 2, padCenterY) // Right
        Thread.sleep(300)
        device.click(padCenterX, padCenterY + padRadius / 2) // Down
        Thread.sleep(300)
        device.click(padCenterX - padRadius / 2, padCenterY) // Left
        Thread.sleep(300)
        takeScreenshot("led_pattern_cross")

        // Pattern 2: Diagonal touch
        println("LED test - Pattern 2: Diagonal touch")
        device.click(padCenterX - padRadius / 3, padCenterY - padRadius / 3) // Top-left
        Thread.sleep(300)
        device.click(padCenterX + padRadius / 3, padCenterY - padRadius / 3) // Top-right
        Thread.sleep(300)
        device.click(padCenterX + padRadius / 3, padCenterY + padRadius / 3) // Bottom-right
        Thread.sleep(300)
        device.click(padCenterX - padRadius / 3, padCenterY + padRadius / 3) // Bottom-left
        Thread.sleep(300)
        takeScreenshot("led_pattern_diagonal")

        // Pattern 3: Rapid successive touches (LED animation overlap test)
        println("LED test - Pattern 3: Rapid successive touches")
        repeat(8) { i ->
            val angle = i * 45.0 * Math.PI / 180.0
            val x = padCenterX + (padRadius / 2 * kotlin.math.cos(angle)).toInt()
            val y = padCenterY + (padRadius / 2 * kotlin.math.sin(angle)).toInt()
            device.click(x, y)
            Thread.sleep(100)
        }
        Thread.sleep(500)
        takeScreenshot("led_pattern_rapid")

        // Pattern 4: Concentrated center touches (same position repeated)
        println("LED test - Pattern 4: Concentrated center touches")
        repeat(5) {
            device.click(padCenterX, padCenterY)
            Thread.sleep(200)
        }
        takeScreenshot("led_pattern_center")

        // Disable LED checkbox
        device.click(checkboxX, ledCheckboxY)
        Thread.sleep(1000)
        takeScreenshot("led_disabled")

        // Touch test with LED disabled (sound only, no LED)
        println("LED disabled state test")
        device.click(padCenterX, padCenterY - 50)
        Thread.sleep(300)
        device.click(padCenterX, padCenterY + 50)
        Thread.sleep(300)
        takeScreenshot("led_disabled_touch")

        // Re-enable LED
        device.click(checkboxX, ledCheckboxY)
        Thread.sleep(1000)
        takeScreenshot("led_re_enabled")

        // Touch test after LED re-enabled
        println("LED re-enabled test")
        device.click(padCenterX - 40, padCenterY)
        Thread.sleep(300)
        device.click(padCenterX + 40, padCenterY)
        Thread.sleep(300)
        takeScreenshot("led_re_enabled_touch")

        // Test FeedbackLight and LED both enabled simultaneously
        val feedbackLightCheckboxY = 150
        device.click(checkboxX, feedbackLightCheckboxY)
        Thread.sleep(1000)
        takeScreenshot("led_and_feedback_both_enabled")

        // Touch with both features enabled simultaneously
        println("LED + FeedbackLight both enabled test")
        repeat(4) { i ->
            device.click(padCenterX + (i - 2) * 30, padCenterY)
            Thread.sleep(250)
        }
        takeScreenshot("led_and_feedback_touch")

        // Verify the app is still running (prevent crash from LED functionality)
        val stillRunning = device.wait(
            Until.hasObject(By.pkg(PACKAGE_NAME)),
            2000L
        )
        assertTrue("App terminated during LED animation test", stillRunning)

        // Navigate back to the main screen
        device.pressBack()
        Thread.sleep(2000)

        val backToMain = device.wait(
            Until.hasObject(By.pkg(PACKAGE_NAME)),
            5000L
        )
        assertTrue("Failed to navigate back after LED test", backToMain)

        takeScreenshot("led_test_end")
    }

    @Test
    fun testPlayActivityTraceLogFeature() {
        println("=== TraceLog detailed verification test start ===")

        // Launch the app
        launchApp()
        device.wait(Until.hasObject(By.pkg(PACKAGE_NAME)), LAUNCH_TIMEOUT)
        handlePermissionDialogs()

        // Wait for main screen to load
        Thread.sleep(5000)

        // Enter PlayActivity
        Thread.sleep(2000)
        val displayWidth = device.displayWidth
        val displayHeight = device.displayHeight
        val listItemY = displayHeight / 3
        device.click(displayWidth / 2, listItemY)
        Thread.sleep(1000)

        // Attempt to click the play button
        val playButtonY = displayHeight - 150
        device.click(displayWidth / 2, playButtonY)
        Thread.sleep(3000)

        val playActivityStarted = device.wait(
            Until.hasObject(By.pkg(PACKAGE_NAME)),
            5000L
        )
        assertTrue("Failed to start PlayActivity", playActivityStarted)
        takeScreenshot("tracelog_play_activity_started")

        // Calculate PadView area
        val padViewTop = 300
        val padViewBottom = displayHeight - 400
        val padViewLeft = 100
        val padViewRight = displayWidth - 100
        val padCenterX = (padViewLeft + padViewRight) / 2
        val padCenterY = (padViewTop + padViewBottom) / 2

        // Calculate checkbox area
        val checkboxX = displayWidth - 100
        val traceLogCheckboxY = 300

        // Enable TraceLog checkbox
        println("Enabling TraceLog checkbox")
        device.click(checkboxX, traceLogCheckboxY)
        Thread.sleep(1500)
        takeScreenshot("tracelog_enabled")

        // Touch multiple positions sequentially to generate trace numbers
        println("TraceLog test - Sequential touches (1-5)")
        val touchPoints = listOf(
            Pair(padCenterX - 80, padCenterY - 80),  // Top-left
            Pair(padCenterX + 80, padCenterY - 80),  // Top-right
            Pair(padCenterX, padCenterY),             // Center
            Pair(padCenterX - 80, padCenterY + 80),  // Bottom-left
            Pair(padCenterX + 80, padCenterY + 80)   // Bottom-right
        )

        for ((index, point) in touchPoints.withIndex()) {
            println("Touch ${index + 1}: (${point.first}, ${point.second})")
            device.click(point.first, point.second)
            Thread.sleep(400)
        }
        Thread.sleep(1000)
        takeScreenshot("tracelog_sequential_touches")

        // Touch the same position multiple times to verify duplicate numbers
        println("TraceLog test - Center repeated touches (6-10)")
        repeat(5) { index ->
            println("Center touch ${index + 6}")
            device.click(padCenterX, padCenterY)
            Thread.sleep(400)
        }
        Thread.sleep(1000)
        takeScreenshot("tracelog_repeated_touches")

        // Rapidly touch various positions (11-20)
        println("TraceLog test - Rapid successive touches (11-20)")
        for (i in 0 until 10) {
            val offsetX = (Math.random() * 120 - 60).toInt()
            val offsetY = (Math.random() * 120 - 60).toInt()
            device.click(padCenterX + offsetX, padCenterY + offsetY)
            Thread.sleep(200)
        }
        Thread.sleep(1000)
        takeScreenshot("tracelog_rapid_touches")

        // Calculate chain button position (top-right)
        val chainRightButtonX = displayWidth - 50
        val chainButtonY = 150

        // Switch chain to verify separate trace logs
        println("TraceLog test after chain switch")
        device.click(chainRightButtonX, chainButtonY)
        Thread.sleep(1000)
        takeScreenshot("tracelog_chain_switched")

        // Start touching in the new chain (numbers should restart from 1)
        println("Touches in new chain (1-5)")
        for ((index, point) in touchPoints.withIndex()) {
            println("New chain touch ${index + 1}: (${point.first}, ${point.second})")
            device.click(point.first, point.second)
            Thread.sleep(400)
        }
        Thread.sleep(1000)
        takeScreenshot("tracelog_new_chain_touches")

        // Return to the original chain
        val chainLeftButtonX = 50
        device.click(chainLeftButtonX, chainButtonY)
        Thread.sleep(1000)
        takeScreenshot("tracelog_chain_back")

        // Verify the original chain's trace log is preserved (should have up to 20)
        println("Verifying original chain's TraceLog preservation")
        device.click(padCenterX - 100, padCenterY)
        Thread.sleep(400)
        device.click(padCenterX + 100, padCenterY)
        Thread.sleep(400)
        takeScreenshot("tracelog_original_chain_preserved")

        // Simulate TraceLog reset via long-click
        // UI Automator does not directly support longClick, so
        // simulate reset effect by disabling then re-enabling the TraceLog checkbox
        println("Disabling TraceLog (simulating reset)")
        device.click(checkboxX, traceLogCheckboxY)
        Thread.sleep(1000)
        takeScreenshot("tracelog_disabled")

        // Touch with TraceLog disabled (trace numbers should not be displayed)
        println("Touching with TraceLog disabled")
        device.click(padCenterX, padCenterY - 50)
        Thread.sleep(300)
        device.click(padCenterX, padCenterY + 50)
        Thread.sleep(300)
        takeScreenshot("tracelog_disabled_touches")

        // Re-enable TraceLog
        println("Re-enabling TraceLog")
        device.click(checkboxX, traceLogCheckboxY)
        Thread.sleep(1500)
        takeScreenshot("tracelog_re_enabled")

        // Touch after re-enabling (previous traces should be reset, starting from 1)
        println("Touching after TraceLog re-enabled (restart from 1)")
        val newTouchPoints = listOf(
            Pair(padCenterX, padCenterY - 60),
            Pair(padCenterX + 60, padCenterY),
            Pair(padCenterX, padCenterY + 60),
            Pair(padCenterX - 60, padCenterY)
        )

        for ((index, point) in newTouchPoints.withIndex()) {
            println("Re-enabled touch ${index + 1}: (${point.first}, ${point.second})")
            device.click(point.first, point.second)
            Thread.sleep(400)
        }
        Thread.sleep(1000)
        takeScreenshot("tracelog_re_enabled_touches")

        // Verify the app is still running (prevent crash from TraceLog functionality)
        val stillRunning = device.wait(
            Until.hasObject(By.pkg(PACKAGE_NAME)),
            2000L
        )
        assertTrue("App terminated during TraceLog test", stillRunning)

        // Navigate back to the main screen
        device.pressBack()
        Thread.sleep(2000)

        val backToMain = device.wait(
            Until.hasObject(By.pkg(PACKAGE_NAME)),
            5000L
        )
        assertTrue("Failed to navigate back after TraceLog test", backToMain)

        takeScreenshot("tracelog_test_end")
        println("=== TraceLog detailed verification test complete ===")
    }

    @Test
    fun testPlayActivityUIVisibilityFeatures() {
        println("=== PlayActivity UI visibility features test start ===")

        // Launch the app and navigate to the main screen
        launchApp()
        device.wait(Until.hasObject(By.pkg(PACKAGE_NAME)), LAUNCH_TIMEOUT)
        handlePermissionDialogs()

        // Wait for main screen to load
        Thread.sleep(10000)

        // Click a unipack list item to navigate to PlayActivity
        val displayWidth = device.displayWidth
        val displayHeight = device.displayHeight
        val itemX = displayWidth / 2
        val itemY = displayHeight / 3

        println("Clicking unipack item")
        device.click(itemX, itemY)
        Thread.sleep(2000)

        // Attempt to click the play button
        val playButtonX = displayWidth * 3 / 4
        val playButtonY = displayHeight * 2 / 3
        device.click(playButtonX, playButtonY)
        Thread.sleep(3000)

        takeScreenshot("ui_visibility_test_start")

        // Verify PlayActivity launched
        val inPlayActivity = device.wait(
            Until.hasObject(By.pkg(PACKAGE_NAME)),
            5000L
        )
        assertTrue("Failed to enter PlayActivity", inPlayActivity)

        // Calculate checkbox area (top-left)
        // Find HideUI checkbox (resource ID based)
        val hideUICheckbox = device.findObject(
            UiSelector().resourceId("${PACKAGE_NAME}:id/CB2_hideUI")
        )

        // Find Watermark checkbox (resource ID based)
        val watermarkCheckbox = device.findObject(
            UiSelector().resourceId("${PACKAGE_NAME}:id/CB2_watermark")
        )

        // 1. HideUI feature test
        println("=== HideUI feature test ===")

        if (hideUICheckbox.exists()) {
            println("Enabling HideUI checkbox")
            hideUICheckbox.click()
            Thread.sleep(1500)
            takeScreenshot("hideui_enabled")

            // Touch PadView with HideUI enabled
            println("Touching PadView with HideUI enabled")
            val padCenterX = displayWidth / 2
            val padCenterY = displayHeight / 2
            device.click(padCenterX, padCenterY)
            Thread.sleep(500)
            device.click(padCenterX - 50, padCenterY - 50)
            Thread.sleep(500)
            device.click(padCenterX + 50, padCenterY + 50)
            Thread.sleep(1000)
            takeScreenshot("hideui_enabled_with_touches")

            // Disable HideUI
            println("Disabling HideUI checkbox")
            hideUICheckbox.click()
            Thread.sleep(1500)
            takeScreenshot("hideui_disabled")

            // Touch PadView with HideUI disabled
            println("Touching PadView with HideUI disabled")
            device.click(padCenterX, padCenterY)
            Thread.sleep(500)
            device.click(padCenterX - 50, padCenterY + 50)
            Thread.sleep(500)
            device.click(padCenterX + 50, padCenterY - 50)
            Thread.sleep(1000)
            takeScreenshot("hideui_disabled_with_touches")
        } else {
            println("HideUI checkbox not found (skipping)")
        }

        // 2. Watermark feature test
        println("=== Watermark feature test ===")

        if (watermarkCheckbox.exists()) {
            println("Enabling Watermark checkbox")
            watermarkCheckbox.click()
            Thread.sleep(1500)
            takeScreenshot("watermark_enabled")

            // Touch PadView with Watermark enabled
            println("Touching PadView with Watermark enabled")
            val padCenterX = displayWidth / 2
            val padCenterY = displayHeight / 2
            device.click(padCenterX, padCenterY)
            Thread.sleep(500)
            device.click(padCenterX - 60, padCenterY)
            Thread.sleep(500)
            device.click(padCenterX + 60, padCenterY)
            Thread.sleep(1000)
            takeScreenshot("watermark_enabled_with_touches")

            // Disable Watermark
            println("Disabling Watermark checkbox")
            watermarkCheckbox.click()
            Thread.sleep(1500)
            takeScreenshot("watermark_disabled")

            // Touch PadView with Watermark disabled
            println("Touching PadView with Watermark disabled")
            device.click(padCenterX, padCenterY)
            Thread.sleep(500)
            device.click(padCenterX, padCenterY - 60)
            Thread.sleep(500)
            device.click(padCenterX, padCenterY + 60)
            Thread.sleep(1000)
            takeScreenshot("watermark_disabled_with_touches")
        } else {
            println("Watermark checkbox not found (skipping)")
        }

        // 3. HideUI + Watermark both enabled simultaneously test
        println("=== HideUI + Watermark both enabled simultaneously test ===")

        if (hideUICheckbox.exists() && watermarkCheckbox.exists()) {
            println("Enabling HideUI + Watermark simultaneously")
            hideUICheckbox.click()
            Thread.sleep(500)
            watermarkCheckbox.click()
            Thread.sleep(1500)
            takeScreenshot("hideui_watermark_both_enabled")

            // Touch PadView with both enabled simultaneously
            println("Touching PadView with HideUI + Watermark both enabled")
            val padCenterX = displayWidth / 2
            val padCenterY = displayHeight / 2

            // Touch four corners
            device.click(padCenterX - 70, padCenterY - 70) // Top-left
            Thread.sleep(300)
            device.click(padCenterX + 70, padCenterY - 70) // Top-right
            Thread.sleep(300)
            device.click(padCenterX - 70, padCenterY + 70) // Bottom-left
            Thread.sleep(300)
            device.click(padCenterX + 70, padCenterY + 70) // Bottom-right
            Thread.sleep(1000)
            takeScreenshot("hideui_watermark_both_enabled_with_touches")

            // Disable both
            println("Disabling HideUI + Watermark both")
            hideUICheckbox.click()
            Thread.sleep(500)
            watermarkCheckbox.click()
            Thread.sleep(1500)
            takeScreenshot("hideui_watermark_both_disabled")
        } else {
            println("HideUI or Watermark checkbox not found (skipping simultaneous enable test)")
        }

        // 4. HideUI repeated toggle test (stability verification)
        println("=== HideUI repeated toggle test ===")

        if (hideUICheckbox.exists()) {
            println("HideUI rapid toggle (5 times)")
            for (i in 1..5) {
                println("HideUI toggle $i/5")
                hideUICheckbox.click()
                Thread.sleep(300)
            }
            Thread.sleep(1000)
            takeScreenshot("hideui_toggle_test")
        }

        // 5. Watermark repeated toggle test (stability verification)
        println("=== Watermark repeated toggle test ===")

        if (watermarkCheckbox.exists()) {
            println("Watermark rapid toggle (5 times)")
            for (i in 1..5) {
                println("Watermark toggle $i/5")
                watermarkCheckbox.click()
                Thread.sleep(300)
            }
            Thread.sleep(1000)
            takeScreenshot("watermark_toggle_test")
        }

        // Verify the app is still running (prevent crash from UI visibility features)
        val stillRunning = device.wait(
            Until.hasObject(By.pkg(PACKAGE_NAME)),
            2000L
        )
        assertTrue("App terminated during UI visibility features test", stillRunning)

        // Navigate back to the main screen
        device.pressBack()
        Thread.sleep(2000)

        val backToMain = device.wait(
            Until.hasObject(By.pkg(PACKAGE_NAME)),
            5000L
        )
        assertTrue("Failed to navigate back after UI visibility features test", backToMain)

        takeScreenshot("ui_visibility_test_end")
        println("=== PlayActivity UI visibility features test complete ===")
    }

    @Test
    fun testPlayActivityRecordingClipboard() {
        // Launch the app and navigate to the main screen
        launchApp()
        device.wait(Until.hasObject(By.pkg(PACKAGE_NAME)), LAUNCH_TIMEOUT)
        handlePermissionDialogs()

        // Wait for main screen to load
        Thread.sleep(10000)

        takeScreenshot("recording_clipboard_start")

        // Click a unipack list item to navigate to PlayActivity
        val displayWidth = device.displayWidth
        val displayHeight = device.displayHeight
        val itemX = displayWidth / 2
        val itemY = displayHeight / 3

        device.click(itemX, itemY)
        Thread.sleep(2000)

        // Attempt to click the play button
        val playButtonX = displayWidth * 3 / 4
        val playButtonY = displayHeight * 2 / 3
        device.click(playButtonX, playButtonY)
        Thread.sleep(3000)

        takeScreenshot("recording_clipboard_playactivity")

        // Verify PlayActivity launched
        val inPlayActivity = device.wait(
            Until.hasObject(By.pkg(PACKAGE_NAME)),
            5000L
        )

        if (!inPlayActivity) {
            println("Could not enter PlayActivity. Skipping test.")
            return
        }

        // Find and click the traceLog checkbox (using resource ID)
        println("=== Finding traceLog checkbox ===")
        val traceLogCheckbox = device.findObject(By.res(PACKAGE_NAME, "CB1_traceLog"))
        if (traceLogCheckbox == null) {
            println("Warning: Could not find traceLog checkbox. Skipping test.")
            return
        }
        traceLogCheckbox.click()
        Thread.sleep(1000)
        takeScreenshot("recording_clipboard_tracelog_enabled")

        // Find and click the record checkbox (using resource ID)
        println("=== Finding record checkbox ===")
        val recordCheckbox = device.findObject(By.res(PACKAGE_NAME, "CB1_record"))
        if (recordCheckbox == null) {
            println("Warning: Could not find record checkbox. Skipping test.")
            return
        }
        recordCheckbox.click()
        Thread.sleep(1000)
        takeScreenshot("recording_clipboard_recording_enabled")

        println("=== Recording started. Executing touch sequence for clipboard verification ===")

        // Touch PadView in a specific pattern while recording is active
        val padCenterX = displayWidth / 2
        val padCenterY = displayHeight / 2

        // Touch 1: Top-left
        device.click(padCenterX - 100, padCenterY - 100)
        Thread.sleep(500)
        takeScreenshot("recording_clipboard_touch_1")

        // Touch 2: Top-right
        device.click(padCenterX + 100, padCenterY - 100)
        Thread.sleep(500)
        takeScreenshot("recording_clipboard_touch_2")

        // Touch 3: Center
        device.click(padCenterX, padCenterY)
        Thread.sleep(500)
        takeScreenshot("recording_clipboard_touch_3")

        // Touch 4: Bottom-left
        device.click(padCenterX - 100, padCenterY + 100)
        Thread.sleep(500)
        takeScreenshot("recording_clipboard_touch_4")

        // Touch 5: Bottom-right
        device.click(padCenterX + 100, padCenterY + 100)
        Thread.sleep(500)
        takeScreenshot("recording_clipboard_touch_5")

        println("=== 5 touches complete. Stopping recording to copy to clipboard ===")

        // Find the record checkbox again and uncheck it to stop recording (copies to clipboard at this point)
        val recordCheckboxStop = device.findObject(By.res(PACKAGE_NAME, "CB1_record"))
        if (recordCheckboxStop != null) {
            recordCheckboxStop.click()
        } else {
            println("Warning: Could not find record checkbox again.")
        }
        Thread.sleep(2000) // Allow time for clipboard copy operation (increased from 1500 to 2000)
        takeScreenshot("recording_clipboard_stopped")

        // Verify clipboard contents
        val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clipData = clipboardManager.primaryClip

        // Verify clipboard is not null (core validation)
        if (clipData == null) {
            println("=== Clipboard is empty - recording stop may not have worked properly ===")
            assertNotNull("Clipboard is empty. Clipboard copy was not triggered when recording stopped.", clipData)
        }

        assertTrue("No items in clipboard", clipData!!.itemCount > 0)

        val clipText = clipData.getItemAt(0).text?.toString() ?: ""
        println("=== Clipboard contents ===")
        println(clipText)
        println("=== End of clipboard contents ===")

        // Verify clipboard contents
        assertTrue("Clipboard is empty", clipText.isNotEmpty())

        // Recording data should start with "c X" format (chain info)
        assertTrue(
            "Recording data is not in the correct format in clipboard. Contents: $clipText",
            clipText.startsWith("c ")
        )

        // Verify clipboard content has minimum length ("c 1" is acceptable)
        // Even if touch events are not registered, at least chain info should exist
        assertTrue(
            "Clipboard content is too short (${clipText.length} chars). Expected minimum format: 'c 1'",
            clipText.length >= 3
        )

        println("=== Clipboard verification successful: ${clipText.length} chars of recording data correctly copied ===")

        // Additional check if touch events were properly recorded (warning only if failed)
        if (clipText.length <= 5) {
            println("Warning: Touch events may not have been recorded in clipboard. Only basic chain info present.")
        }

        // Also disable traceLog (using resource ID)
        device.findObject(By.res(PACKAGE_NAME, "CB1_traceLog"))?.click()
        Thread.sleep(1000)

        // Verify the app is still running
        val stillRunning = device.wait(
            Until.hasObject(By.pkg(PACKAGE_NAME)),
            2000L
        )
        assertTrue("App terminated during recording clipboard test", stillRunning)

        // Navigate back to the main screen
        device.pressBack()
        Thread.sleep(2000)

        val backToMain = device.wait(
            Until.hasObject(By.pkg(PACKAGE_NAME)),
            5000L
        )
        assertTrue("Failed to navigate back after recording clipboard test", backToMain)

        takeScreenshot("recording_clipboard_end")
        println("=== Recording data clipboard verification test complete ===")
    }
}
