package com.kimjisub.launchpad

import android.content.res.Configuration
import android.os.Build
import android.os.SystemClock
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.kimjisub.launchpad.activity.MainActivity
import com.kimjisub.launchpad.adapter.UniPackItem
import com.kimjisub.launchpad.unipack.UniPack
import com.kimjisub.launchpad.viewmodel.MainTotalPanelViewModel
import com.kimjisub.launchpad.db.AppDatabase
import com.kimjisub.launchpad.db.ent.Unipack
import com.kimjisub.launchpad.manager.PreferenceManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

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
    fun testMainNavigationButtonsAndSettingsReturn() {
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

    /**
     * Choosing UniPad's language in the system settings (Android 13+) changes the sort bar and its
     * list right away, while the main screen's view model survives the change, and after a relaunch.
     * Below Android 13 there is no per-app language; the sort bar shows the device language.
     */
    @Test
    fun testSortNamesFollowAppLanguageChange() {
        val originalMethod = prefs.sortMethod
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            try {
                prefs.sortMethod = 2
                launchToMainScreen()
                assertTrue(
                    "Sort bar does not show '${str(R.string.sort_download_date)}'",
                    device.wait(Until.hasObject(By.text(str(R.string.sort_download_date))), 5000L)
                )
            } finally {
                prefs.sortMethod = originalMethod
            }
            return
        }
        val sortRes = listOf(R.string.sort_title, R.string.sort_producer, R.string.sort_download_date)
        val english = sortRes.map { strIn(Locale.ENGLISH, it) }
        val korean = sortRes.map { strIn(Locale.KOREAN, it) }
        val originalLocales = AppLanguage.get()
        try {
            AppLanguage.set("en")
            prefs.sortMethod = 2
            launchToMainScreen()
            assertTrue("Sort bar does not show '${english[2]}'", device.wait(Until.hasObject(By.text(english[2])), 5000L))

            AppLanguage.set("ko")
            assertTrue(
                "Sort bar still shows '${english[2]}' after switching to Korean",
                device.wait(Until.hasObject(By.text(korean[2])), 10000L)
            )
            sortAnchor(korean).click()
            assertTrue(
                "Sort list is not in Korean",
                waitUntil(3000L) { korean.all { device.hasObject(By.text(it)) } }
            )
            takeScreenshot("sort_names_korean_without_relaunch")
            device.pressBack()

            launchToMainActivity()
            assertTrue(
                "Sort bar is not Korean after a relaunch",
                device.wait(Until.hasObject(By.text(korean[2])), 10000L)
            )
            takeScreenshot("sort_names_korean_after_relaunch")
        } finally {
            AppLanguage.set(originalLocales)
            prefs.sortMethod = originalMethod
        }
    }

    private fun strIn(locale: Locale, resId: Int): String {
        val configuration = Configuration(context.resources.configuration).apply { setLocale(locale) }
        return context.createConfigurationContext(configuration).getString(resId)
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
        val unreachable = UnreachableFolder(File(TestUniPack.folder(context), "sounds"))
        try {
            assertTrue("Failure fixture must nest a folder beyond the path limit", unreachable.create())
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
            assertTrue("Failure fixture left folders behind", unreachable.remove())
        } finally {
            unreachable.remove()
        }
    }

    /** A deletion failure run stopped before its cleanup must not break the next test's pack. */
    @Test
    fun testTestPackInstallClearsLeftoverDeletionFixture() {
        val sounds = File(TestUniPack.folder(context), "sounds")
        for (stranded in listOf(false, true)) {
            val unreachable = UnreachableFolder(sounds)
            try {
                assertTrue("Failure fixture must nest a folder beyond the path limit", unreachable.create())
                if (stranded) assertTrue("Fixture could not be moved under its spare folder", unreachable.strandUnderSpare())
                assertFalse("Fixture must survive a delete by path", TestUniPack.folder(context).deleteRecursively())

                TestUniPack.install(context)

                assertEquals(
                    "Test pack install left part of the fixture (stranded=$stranded)",
                    listOf("silence.wav"),
                    sounds.list()?.sorted()
                )
            } finally {
                unreachable.remove()
            }
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

    /** Coming back from Store or Settings reads the pack list once. */
    @Test
    fun testReturningToMainReadsThePackListOnce() {
        launchToMainScreen()
        val start = settledListRefreshes()

        device.findObject(By.desc(str(R.string.store))).click()
        assertTrue("Did not transition to the store screen", waitForStoreScreen())
        device.pressBack()
        assertTrue("Could not return to the main screen", waitForMainScreen())
        val afterStore = settledListRefreshes()
        assertEquals("returning from the store must re-read the list once", start + 1, afterStore)

        openSettings()
        device.pressBack()
        assertTrue("Could not return to the main screen", waitForMainScreen())
        assertEquals("returning from settings must re-read the list once", afterStore + 1, settledListRefreshes())
    }

    /** Opening Main and recreating it (rotation, language or dark mode) read the pack list once each. */
    @Test
    fun testLaunchAndRecreateReadThePackListOnce() {
        launchToMainScreen()
        assertEquals("opening the main screen must read the list once", 1, settledListRefreshes())

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .filterIsInstance<MainActivity>().single().recreate()
        }
        assertTrue("Could not return to the main screen", waitForMainScreen())
        assertEquals("a recreated main screen must read the list once", 1, settledListRefreshes())
    }

    /** An old scan may finish during deletion; queued scans must still wait for the files to be gone. */
    @Test
    fun testRefreshWaitsForDeletionEvenWhenAnEarlierReadFinishes() {
        val scanStarted = CountDownLatch(1)
        val allowScan = CountDownLatch(1)
        val deleteStarted = CountDownLatch(1)
        val allowDelete = CountDownLatch(1)
        val firstComparison = AtomicBoolean(true)
        val secondFolder = File(TestUniPack.folder(context).parentFile, OTHER_PACK_ID)
        try {
            assertTrue("Could not create a second sortable pack", TestUniPack.folder(context).copyRecursively(secondFolder))
            launchToMainScreen()
            val start = settledListRefreshes()
            lateinit var main: MainActivity
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                main = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<MainActivity>().single()
                @Suppress("UNCHECKED_CAST")
                val items = MainActivity::class.java.getDeclaredField("unipackList")
                    .apply { isAccessible = true }.get(main) as List<UniPackItem>
                val item = items.single { it.unipack.id == TestUniPack.FOLDER_NAME }
                val pack = item.unipack
                // Hold only the real deletion's file step; WorkspaceManager still removes its saved row.
                val heldPack = object : UniPack() {
                    override val id = pack.id
                    override fun lastModified() = pack.lastModified()
                    override fun loadInfo() = this
                    override fun loadDetail() = this
                    override fun checkFile() = pack.checkFile()
                    override fun getPathString() = pack.getPathString()
                    override fun getByteSize() = pack.getByteSize()
                    override fun delete(): Boolean {
                        deleteStarted.countDown()
                        check(allowDelete.await(15, TimeUnit.SECONDS)) { "Deletion gate was not released" }
                        return pack.delete()
                    }
                }
                @Suppress("UNCHECKED_CAST")
                val sort = delegate(main, "currentSort") as MutableState<Pair<MainTotalPanelViewModel.SortMethod, Boolean>?>
                val original = sort.value!!
                sort.value = original.first.copy(comparator = Comparator { a, b ->
                    if (firstComparison.compareAndSet(true, false)) {
                        scanStarted.countDown()
                        check(allowScan.await(15, TimeUnit.SECONDS)) { "List scan gate was not released" }
                    }
                    original.first.comparator.compare(a, b)
                }) to original.second
                requestRefresh(main)
                @Suppress("UNCHECKED_CAST")
                val target = delegate(main, "deleteTargetItem") as MutableState<UniPackItem?>
                target.value = item.copy(unipack = heldPack)
            }
            assertTrue("List scan never reached sorting", scanStarted.await(5, TimeUnit.SECONDS))
            assertTrue("Delete confirmation did not open", device.wait(Until.hasObject(By.text(str(R.string.accept))), 5000L))
            device.findObject(By.text(str(R.string.accept))).click()
            assertTrue("Deletion never started", deleteStarted.await(5, TimeUnit.SECONDS))
            InstrumentationRegistry.getInstrumentation().runOnMainSync { requestRefresh(main) }
            allowScan.countDown()
            assertEquals("a queued read must wait while files are being deleted", start + 1, settledListRefreshes())
            assertTrue("Pack files must remain behind the deletion gate", TestUniPack.exists(context))
            allowDelete.countDown()
            assertTrue("Pack files were not deleted", waitUntil(10000L) { !TestUniPack.exists(context) })
            assertEquals("all queued reads must coalesce into one after deletion", start + 2, settledListRefreshes())
            assertEquals("Deleted pack history remains", null, history(TestUniPack.FOLDER_NAME))
        } finally {
            allowScan.countDown()
            allowDelete.countDown()
            secondFolder.deleteRecursively()
            unipackDao.delete(OTHER_PACK_ID)
        }
    }

    private fun requestRefresh(main: MainActivity) {
        MainActivity::class.java.getDeclaredMethod("update").apply { isAccessible = true }.invoke(main)
    }

    /** Completed list reads of the resumed main screen, once no read is running or starts for a while. */
    private fun settledListRefreshes(): Int {
        val deadline = SystemClock.uptimeMillis() + 15_000
        var last = -1
        var stableSince = SystemClock.uptimeMillis()
        while (SystemClock.uptimeMillis() < deadline) {
            var count = -1
            var refreshing = true
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                val main = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<MainActivity>().singleOrNull() ?: return@runOnMainSync
                count = (delegate(main, "scrollToTopTrigger") as MutableIntState).intValue
                refreshing = (delegate(main, "listRefreshing") as MutableState<*>).value as Boolean
            }
            if (count != last || refreshing) {
                last = count
                stableSince = SystemClock.uptimeMillis()
            } else if (SystemClock.uptimeMillis() - stableSince >= 2_000) {
                return count
            }
            SystemClock.sleep(100)
        }
        throw AssertionError("main screen list did not settle (last count $last)")
    }

    private fun delegate(main: MainActivity, property: String): Any =
        MainActivity::class.java.getDeclaredField("$property\$delegate").apply { isAccessible = true }.get(main)!!

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
