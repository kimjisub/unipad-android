package com.kimjisub.launchpad

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.kimjisub.launchpad.db.AppDatabase
import com.kimjisub.launchpad.db.ent.Unipack
import com.kimjisub.launchpad.manager.PreferenceManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

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

    companion object {
        /** A saved row for a pack that is not the one being deleted. */
        private const val OTHER_PACK_ID = "zz_ui_test_other_pack"
    }

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
        seedHistory(TestUniPack.FOLDER_NAME)
        seedHistory(OTHER_PACK_ID)
        try {
            launchToMainScreen()
            takeScreenshot("before_unipack_deletion")

            confirmDeleteOfTestPack(accept = true)
            takeScreenshot("after_deletion_confirm")

            assertTrue("Test pack folder was not deleted", waitUntil(10000L) { !TestUniPack.exists(context) })
            assertTrue(
                "Deleted pack is still listed",
                device.wait(Until.gone(By.textContains(TestUniPack.TITLE)), 10000L)
            )
            assertTrue("Saved row of the deleted pack remains", waitUntil(5000L) { history(TestUniPack.FOLDER_NAME) == null })
            assertEquals("Another pack's history changed", 1L to true, history(OTHER_PACK_ID))
            assertTrue("Main screen lost after deletion", waitForMainScreen(5000L))
            takeScreenshot("after_unipack_deletion")

            // Reinstall under the same folder name: the list reload recreates the row without the old history.
            TestUniPack.install(context)
            launchToMainScreen()
            findTestPackRow()
            assertTrue("Reinstalled pack has no row", waitUntil(5000L) { history(TestUniPack.FOLDER_NAME) != null })
            assertEquals("Reinstalled pack kept the old history", 0L to false, history(TestUniPack.FOLDER_NAME))
            takeScreenshot("after_unipack_reinstall")
        } finally {
            unipackDao.delete(OTHER_PACK_ID)
        }
    }

    @Test
    fun testUnipackDeletionCancel() {
        seedHistory(TestUniPack.FOLDER_NAME)
        launchToMainScreen()

        confirmDeleteOfTestPack(accept = false)
        takeScreenshot("after_deletion_cancel")

        assertTrue(
            "Delete dialog stayed open after cancel",
            device.wait(Until.gone(By.text(str(R.string.doYouWantToDeleteUniPack))), 5000L)
        )
        assertTrue("Cancel deleted the pack files", TestUniPack.exists(context))
        assertEquals("Cancel changed the pack's history", 1L to true, history(TestUniPack.FOLDER_NAME))
        findTestPackRow()
    }

    /** A folder the app cannot empty: the error is shown and the history stays. */
    @Test
    fun testUnipackDeletionFailureKeepsHistory() {
        val sounds = File(TestUniPack.folder(context), "sounds")
        assumeTrue("Storage ignores permission bits here", sounds.setWritable(false, false) && !sounds.canWrite())
        try {
            seedHistory(TestUniPack.FOLDER_NAME)
            launchToMainScreen()

            confirmDeleteOfTestPack(accept = true)

            assertTrue(
                "Delete failure was not reported",
                device.wait(Until.hasObject(By.text(str(R.string.errOccur))), 10000L)
            )
            takeScreenshot("after_deletion_failure")
            assertTrue("Pack files are gone", TestUniPack.folder(context).exists())
            assertEquals("History was dropped although the files remain", 1L to true, history(TestUniPack.FOLDER_NAME))
            device.findObject(By.text(str(android.R.string.ok))).click()
        } finally {
            sounds.setWritable(true, false)
        }
    }

    private val unipackDao by lazy { AppDatabase.getInstance(context).unipackDAO() }

    /** One play and a bookmark, the history a reinstall must not bring back. */
    private fun seedHistory(id: String) {
        unipackDao.delete(id)
        unipackDao.insert(Unipack.create(id))
        unipackDao.addOpenCount(id)
        unipackDao.toggleBookmark(id)
    }

    /** (openCount, bookmark) of the saved row, or null when there is none. */
    private fun history(id: String): Pair<Long, Boolean>? =
        AppDatabase.getInstance(context).openHelper.readableDatabase
            .query("SELECT openCount, bookmark FROM Unipack WHERE id=?", arrayOf(id)).use {
                if (it.moveToFirst()) it.getLong(0) to (it.getInt(1) == 1) else null
            }

    private fun confirmDeleteOfTestPack(accept: Boolean) {
        selectTestPack()
        device.findObject(By.desc(str(R.string.cd_delete))).click()
        assertTrue(
            "Delete confirmation dialog did not appear",
            device.wait(Until.hasObject(By.text(str(R.string.doYouWantToDeleteUniPack))), 5000L)
        )
        takeScreenshot("delete_dialog")
        device.findObject(By.text(str(if (accept) R.string.accept else R.string.cancel))).click()
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
