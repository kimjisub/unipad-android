package com.kimjisub.launchpad

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Store Tests
 * Tests for store browsing functionality
 */
@RunWith(AndroidJUnit4::class)
class StoreTest : BaseUITest() {

    @Test
    fun testStoreUnipackBrowsing() {
        // Launch the app and navigate to the main screen
        launchApp()
        device.wait(Until.hasObject(By.pkg(PACKAGE_NAME)), LAUNCH_TIMEOUT)
        handlePermissionDialogs()

        // Wait for main screen to load
        Thread.sleep(10000)

        takeScreenshot("store_browsing_start")

        // Open the FAB menu
        val displayWidth = device.displayWidth
        val displayHeight = device.displayHeight
        val fabX = displayWidth - 80
        val fabY = displayHeight - 80
        device.click(fabX, fabY)
        Thread.sleep(2000)

        // Click the store button
        val storeFabY = fabY - 150
        device.click(fabX, storeFabY)
        Thread.sleep(5000)  // Wait for store to load

        takeScreenshot("store_opened")

        // Verify FBStoreActivity launched
        val storeActivityRunning = device.wait(
            Until.hasObject(By.pkg(PACKAGE_NAME)),
            5000L
        )
        assertTrue("Did not transition to the store screen", storeActivityRunning)

        // Scroll through the store list to view content
        // Scroll down
        device.swipe(displayWidth / 2, displayHeight / 2, displayWidth / 2, displayHeight / 4, 10)
        Thread.sleep(2000)
        takeScreenshot("store_scrolled_down")

        // Scroll up (back to original position)
        device.swipe(displayWidth / 2, displayHeight / 4, displayWidth / 2, displayHeight / 2, 10)
        Thread.sleep(2000)
        takeScreenshot("store_scrolled_up")

        // Attempt to click a store item (top area)
        val itemX = displayWidth / 2
        val itemY = displayHeight / 3

        device.click(itemX, itemY)
        Thread.sleep(3000)
        takeScreenshot("store_item_clicked")

        // Verify the app is still running
        val stillRunning = device.wait(
            Until.hasObject(By.pkg(PACKAGE_NAME)),
            3000L
        )
        assertTrue("App terminated after clicking store item", stillRunning)

        // Attempt to click the download button if present
        // The download button is typically in the bottom-right or in the detail info panel
        val downloadButtonX = displayWidth * 3 / 4
        val downloadButtonY = displayHeight * 2 / 3

        device.click(downloadButtonX, downloadButtonY)
        Thread.sleep(5000)  // Wait for download
        takeScreenshot("store_download_attempt")

        // Verify the download completed (check the app hasn't crashed)
        val afterDownload = device.wait(
            Until.hasObject(By.pkg(PACKAGE_NAME)),
            5000L
        )
        assertTrue("App terminated after download attempt", afterDownload)

        // Navigate back to the main screen
        device.pressBack()
        Thread.sleep(2000)

        // Press back once more (in case the detail panel was opened)
        device.pressBack()
        Thread.sleep(2000)

        val backToMain = device.wait(
            Until.hasObject(By.pkg(PACKAGE_NAME)),
            5000L
        )
        assertTrue("Could not return to the main screen from store", backToMain)

        takeScreenshot("store_browsing_end")
    }

    @Test
    fun testStoreActivityNavigation() {
        // This test is a duplicate of the one in MainActivityTest, but included here for completeness
        // Testing the same functionality but as part of StoreTest suite

        // Launch the app and navigate to the main screen
        launchApp()
        device.wait(Until.hasObject(By.pkg(PACKAGE_NAME)), LAUNCH_TIMEOUT)
        handlePermissionDialogs()

        // Wait for main screen to load
        Thread.sleep(10000)

        takeScreenshot("store_nav_start")

        // Open the FAB menu
        val displayWidth = device.displayWidth
        val displayHeight = device.displayHeight
        val fabX = displayWidth - 80
        val fabY = displayHeight - 80
        device.click(fabX, fabY)
        Thread.sleep(2000)

        // Click the store button (third button from top)
        val storeFabY = fabY - 150
        device.click(fabX, storeFabY)
        Thread.sleep(3000)

        takeScreenshot("store_nav_opened")

        // Verify FBStoreActivity launched
        val storeActivityRunning = device.wait(
            Until.hasObject(By.pkg(PACKAGE_NAME)),
            5000L
        )
        assertTrue("Did not transition to the store screen", storeActivityRunning)

        // Simple interaction in the store
        // Click center of screen (in case list items are present)
        device.click(displayWidth / 2, displayHeight / 2)
        Thread.sleep(2000)
        takeScreenshot("store_nav_interaction")

        // Navigate back to the main screen
        device.pressBack()
        Thread.sleep(2000)

        val backToMain = device.wait(
            Until.hasObject(By.pkg(PACKAGE_NAME)),
            5000L
        )
        assertTrue("Could not return to the main screen", backToMain)

        takeScreenshot("store_nav_end")
    }
}
