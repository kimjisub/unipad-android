package com.kimjisub.launchpad

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.kimjisub.launchpad.manager.PreferenceManager
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * MainActivity Tests
 * Tests for main screen functionality (action buttons, sorting, deletion, store navigation)
 *
 * The main screen is Compose: the old FAB menu (floatingMenu, store, setting, loadUniPack) was
 * replaced by the sort bar's Store / Import icons, the total panel's Settings icon and the guide
 * chips under the list. Elements are found by content description or text, not view id.
 */
@RunWith(AndroidJUnit4::class)
class MainActivityTest : BaseUITest() {

    private val prefs by lazy { PreferenceManager(context) }

    /** Former FAB menu: every entry it offered is now a button on the main screen. */
    @Test
    fun testFloatingActionMenuInteraction() {
        launchToMainScreen()
        takeScreenshot("fab_menu_before")

        assertNotNull("Store button not found", device.findObject(By.desc(str(R.string.store))))
        assertNotNull("Import UniPack button not found", device.findObject(By.desc(str(R.string.import_unipack))))
        assertNotNull("Settings button not found", device.findObject(By.desc(str(R.string.setting))))

        device.findObject(By.desc(str(R.string.setting))).click()
        assertTrue(
            "Settings button did not open settings",
            device.wait(Until.hasObject(By.text(str(R.string.settings_storage))), 5000L)
        )
        takeScreenshot("fab_menu_opened")

        device.pressBack()
        assertTrue("Could not return to the main screen", waitForMainScreen())
        takeScreenshot("fab_menu_closed")
    }

    @Test
    fun testMainScreenSorting() {
        val originalMethod = prefs.sortMethod
        val originalOrder = prefs.sortOrder
        try {
            launchToMainScreen()
            takeScreenshot("sorting_main_screen")

            val titles = listOf(
                str(R.string.sort_title),
                str(R.string.sort_producer),
                str(R.string.sort_download_date),
            )

            for ((index, title) in titles.withIndex()) {
                sortAnchor(titles).click()
                val option = device.wait(Until.findObject(By.text(title).clickable(true)), 3000L)
                    ?: device.wait(Until.findObject(By.text(title)), 1000L)
                assertNotNull("Sort option '$title' not in the dropdown", option)
                option!!.click()
                assertTrue(
                    "Sort method did not change to '$title'",
                    waitUntil { prefs.sortMethod == index }
                )
                assertTrue(
                    "Sort bar does not show '$title'",
                    device.wait(Until.hasObject(By.text(title)), 3000L)
                )
                takeScreenshot("sort_by_$index")
            }

            // Order toggle: the arrow icon inside the sort anchor
            val orderBefore = prefs.sortOrder
            sortOrderArrow(titles).click()
            assertTrue("Sort order did not toggle", waitUntil { prefs.sortOrder != orderBefore })
            closeSortDropdown(titles)
            takeScreenshot("sort_order_changed")

            sortOrderArrow(titles).click()
            assertTrue("Sort order did not toggle back", waitUntil { prefs.sortOrder == orderBefore })
            closeSortDropdown(titles)
            takeScreenshot("sort_order_restored")

            assertTrue("Main screen lost after sorting", waitForMainScreen(3000L))
            takeScreenshot("sorting_test_end")
        } finally {
            prefs.sortMethod = originalMethod
            prefs.sortOrder = originalOrder
        }
    }

    @Test
    fun testUnipackDeletion() {
        launchToMainScreen()
        takeScreenshot("before_unipack_deletion")

        selectTestPack()
        takeScreenshot("pack_panel_opened")

        device.findObject(By.desc(str(R.string.cd_delete))).click()
        assertTrue(
            "Delete confirmation dialog did not appear",
            device.wait(Until.hasObject(By.text(str(R.string.doYouWantToDeleteUniPack))), 5000L)
        )
        takeScreenshot("after_delete_button_click")

        device.findObject(By.text(str(R.string.accept))).click()
        takeScreenshot("after_deletion_confirm")

        assertTrue("Test pack folder was not deleted", waitUntil(10000L) { !TestUniPack.exists(context) })
        assertTrue(
            "Deleted pack is still listed",
            device.wait(Until.gone(By.textContains(TestUniPack.TITLE)), 10000L)
        )
        assertTrue("Main screen lost after deletion", waitForMainScreen(5000L))
        takeScreenshot("after_unipack_deletion")
    }

    @Test
    fun testStoreActivityNavigation() {
        launchToMainScreen()
        takeScreenshot("before_store_navigation")

        device.findObject(By.desc(str(R.string.store))).click()
        assertTrue("Did not transition to the store screen", waitForStoreScreen())
        takeScreenshot("store_activity")

        device.pressBack()
        assertTrue("Could not return to the main screen", waitForMainScreen())
        takeScreenshot("back_from_store")
    }

    /**
     * Tapping the order arrow currently also expands the sort dropdown (reported separately as an
     * app issue). Close it so the next lookup finds the anchor, not a dropdown item.
     */
    private fun closeSortDropdown(titles: List<String>) {
        if (titles.count { device.hasObject(By.text(it)) } > 1) {
            device.pressBack()
            assertTrue("Sort dropdown did not close", waitUntil { titles.count { device.hasObject(By.text(it)) } == 1 })
        }
    }

    /**
     * The order arrow drawn after the sort title. It has no description, so it is the clickable
     * whose center lies on the title's row and past the title's right edge (before Store).
     */
    private fun sortOrderArrow(titles: List<String>): UiObject2 {
        var arrow: UiObject2? = null
        waitUntil {
            val title = titles.firstNotNullOfOrNull { device.findObject(By.text(it)) } ?: return@waitUntil false
            val t = title.visibleBounds
            arrow = device.findObjects(By.pkg(PACKAGE_NAME).clickable(true)).firstOrNull {
                val b = it.visibleBounds
                b.centerY() in t.top..t.bottom && b.centerX() > t.right && b.centerX() < t.right + t.height() * 2
            }
            arrow != null
        }
        assertNotNull("Sort order arrow not found", arrow)
        return arrow!!
    }

    /** The clickable sort dropdown anchor in the sort bar (shows the current method). */
    private fun sortAnchor(titles: List<String>): UiObject2 {
        val anchor = titles.firstNotNullOfOrNull { device.wait(Until.findObject(By.text(it)), 1000L) }
        assertNotNull("Sort dropdown not found", anchor)
        return clickableAncestor(anchor!!)
    }
}
