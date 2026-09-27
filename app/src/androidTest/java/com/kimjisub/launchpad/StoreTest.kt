package com.kimjisub.launchpad

import android.graphics.Rect
import android.os.Build
import android.view.WindowInsets
import android.view.WindowManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
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

    private companion object {
        const val MAX_SCROLLS = 60
    }

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

    /**
     * Scroll to the end of the store and check that the last row is clear of the navigation bar and
     * display cutout (a gesture handle below it, or a 3-button bar beside it in landscape), then tap it.
     */
    @Test
    fun testStoreLastRowClearOfSystemBars() {
        assumeTrue("Window insets need API 30", Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
        launchToMainScreen()
        openStore()

        val list = waitForStoreList()
        val safe = safeArea()
        val lastRow = scrollToLastRow(list, safe)
        assertNotNull("Store list has no items", lastRow)
        takeScreenshot("store_list_end")

        val row = lastRow!!.visibleBounds
        assertTrue("Last row $row ends under the bottom system bar (safe area $safe)", row.bottom <= safe.bottom)
        assertTrue("Last row $row runs under a side system bar (safe area $safe)", row.right <= safe.right && row.left >= safe.left)

        lastRow.click()
        assertTrue(
            "Tapping the last row did not select it",
            waitForAnyElementWithPolling(
                listOf(By.pkg(PACKAGE_NAME).text(str(R.string.download)), By.pkg(PACKAGE_NAME).text(str(R.string.downloaded))),
                5000L,
            )
        )
        takeScreenshot("store_list_end_selected")
    }

    /**
     * Scrolls until the lowest row stays put, since the list keeps reporting it can scroll near its end.
     * The list reaches under the gesture handle, so swipes start above [safe] or the system takes them.
     */
    private fun scrollToLastRow(list: UiObject2, safe: Rect): UiObject2? {
        val bounds = list.visibleBounds
        list.setGestureMargins(0, 0, 0, (bounds.bottom - safe.bottom).coerceAtLeast(0) + bounds.height() / 10)
        var previous: Rect? = null
        repeat(MAX_SCROLLS) {
            list.scroll(Direction.DOWN, 1f)
            device.waitForIdle()
            val last = list.children.filter { it.isClickable }.maxByOrNull { it.visibleBounds.bottom } ?: return null
            if (last.visibleBounds == previous) return last
            previous = last.visibleBounds
        }
        fail("Store list never reached its end")
        return null
    }

    private fun safeArea(): Rect {
        val metrics = context.getSystemService(WindowManager::class.java).maximumWindowMetrics
        val insets = metrics.windowInsets.getInsetsIgnoringVisibility(
            WindowInsets.Type.navigationBars() or WindowInsets.Type.displayCutout()
        )
        return Rect(metrics.bounds).apply {
            left += insets.left
            top += insets.top
            right -= insets.right
            bottom -= insets.bottom
        }
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
