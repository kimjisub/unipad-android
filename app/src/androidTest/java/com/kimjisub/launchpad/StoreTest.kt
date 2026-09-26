package com.kimjisub.launchpad

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Store Tests
 * Tests for store browsing functionality
 *
 * The store is opened from the main sort bar's Store icon. Its list is loaded from Firebase, so
 * these tests need network access on the device.
 */
@RunWith(AndroidJUnit4::class)
class StoreTest : BaseUITest() {

    /**
     * Browse the store and open a pack's detail panel. The Download button is only checked for
     * presence and not pressed: pressing it downloads a real pack and counts in the production
     * download statistics.
     */
    @Test
    fun testStoreUnipackBrowsing() {
        launchToMainScreen()
        takeScreenshot("store_browsing_start")

        openStore()
        takeScreenshot("store_opened")

        val list = waitForStoreList()
        list.scroll(Direction.DOWN, 0.5f)
        takeScreenshot("store_scrolled_down")
        list.scroll(Direction.UP, 0.5f)
        takeScreenshot("store_scrolled_up")

        val firstItem = waitForStoreList().children.firstOrNull { it.isClickable }
        assertNotNull("Store list has no items", firstItem)
        firstItem!!.click()
        val downloadState = By.pkg(PACKAGE_NAME).text(str(R.string.download))
        val downloadedState = By.pkg(PACKAGE_NAME).text(str(R.string.downloaded))
        assertTrue(
            "Store pack detail did not open",
            waitForAnyElementWithPolling(listOf(downloadState, downloadedState), 5000L)
        )
        takeScreenshot("store_item_clicked")

        // First Back closes the detail, the second leaves the store
        device.pressBack()
        assertTrue(
            "Back did not close the store pack detail",
            device.wait(Until.gone(downloadedState), 3000L) && device.wait(Until.gone(downloadState), 3000L)
        )
        assertTrue("Store closed on the first Back", waitForStoreScreen(2000L))

        device.pressBack()
        assertTrue("Could not return to the main screen from store", waitForMainScreen())
        takeScreenshot("store_browsing_end")
    }

    @Test
    fun testStoreActivityNavigation() {
        // This test is a duplicate of the one in MainActivityTest, but included here for completeness
        // Testing the same functionality but as part of StoreTest suite
        launchToMainScreen()
        takeScreenshot("store_nav_start")

        openStore()
        takeScreenshot("store_nav_opened")

        device.pressBack()
        assertTrue("Could not return to the main screen", waitForMainScreen())
        takeScreenshot("store_nav_end")
    }

    private fun openStore() {
        device.findObject(By.desc(str(R.string.store)))?.click()
            ?: device.findObject(By.textContains(str(R.string.guide_download_new))).click()
        assertTrue("Did not transition to the store screen", waitForStoreScreen())
    }

    private fun waitForStoreList(): UiObject2 {
        val list = device.wait(Until.findObject(By.pkg(PACKAGE_NAME).scrollable(true)), 15000L)
        assertFalse(
            "Store could not reach the server (no network on the device?)",
            list == null && device.hasObject(By.text(str(R.string.UnableToAccessServer)))
        )
        assertNotNull("Store list did not load", list)
        return list!!
    }
}
