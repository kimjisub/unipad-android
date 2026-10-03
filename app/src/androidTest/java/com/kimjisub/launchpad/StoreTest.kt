package com.kimjisub.launchpad

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Build
import android.util.Log
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleCallback
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.By
import com.kimjisub.launchpad.activity.FBStoreActivity
import com.kimjisub.launchpad.activity.MainActivity
import com.kimjisub.launchpad.activity.SplashActivity
import com.kimjisub.launchpad.manager.PreferenceManager
import com.kimjisub.launchpad.network.FirebaseStoreCatalog
import com.kimjisub.launchpad.network.StoreCatalog
import com.kimjisub.launchpad.network.fb.StoreVO
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.loadKoinModules
import org.koin.dsl.module
import java.util.concurrent.CopyOnWriteArrayList
import java.util.regex.Pattern

/** Real activity, navigation and Compose list, with an activity-scoped, offline catalogue. */
@RunWith(AndroidJUnit4::class)
class StoreTest : BaseUITest() {
    @get:Rule
    val composeTestRule = createEmptyComposeRule()

    private var previousStoreCount = 0L
    private var catalogueInstalled = false
    private var mainScenario: ActivityScenario<MainActivity>? = null

    private val packs = List(30) { index ->
        StoreVO(
            code = "ui_store_$index",
            title = "Store Test Pack $index",
            producerName = "Store Test Producer $index",
            isLED = index % 2 == 0,
            isAutoPlay = index % 3 == 0,
            downloadCount = index,
            URL = "https://example.invalid/ui-store-$index.zip",
        )
    }
    private val fixture = object : StoreCatalog {
        override fun attach(listener: StoreCatalog.Listener) {
            // Firebase inserts each new child at the head; reverse delivery gives an ordered list.
            packs.asReversed().forEach { listener.onAdded(it, requireNotNull(it.code)) }
            listener.onCount(packs.size.toLong())
        }
        override fun detach() {}
    }

    @Before
    fun installOfflineCatalogue() {
        previousStoreCount = PreferenceManager(context).prevStoreCount
        loadKoinModules(module { factory<StoreCatalog> { fixture } })
        catalogueInstalled = true
    }

    @After
    fun restoreCatalogue() {
        if (!catalogueInstalled) return
        // Dispose the activity before restoring the binding, also when an assertion failed.
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            listOf(Stage.RESUMED, Stage.STARTED, Stage.CREATED).forEach { stage ->
                ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(stage)
                    .filterIsInstance<FBStoreActivity>().toList().forEach { it.finish() }
            }
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        mainScenario?.close()
        loadKoinModules(module { factory<StoreCatalog> { FirebaseStoreCatalog() } })
        PreferenceManager(context).prevStoreCount = previousStoreCount
    }

    /** Browse both ends, select a known pack, check its actual detail, close detail then store. */
    @Test
    fun testStoreUnipackBrowsing() {
        launchMain()
        openStore()
        takeScreenshot("store_opened")

        composeTestRule.onNodeWithTag("store_list").performScrollToIndex(packs.lastIndex)
        composeTestRule.onNodeWithTag(rowTag(packs.lastIndex)).assertIsDisplayed()
        takeScreenshot("store_scrolled_down")
        composeTestRule.onNodeWithTag("store_list").performScrollToIndex(0)
        composeTestRule.onNodeWithTag(rowTag(0)).assertIsDisplayed().performClick()
        assertDetailOf(0)
        takeScreenshot("store_item_clicked")

        device.pressBack()
        composeTestRule.waitUntil(5000L) {
            composeTestRule.onAllNodesWithTag("store_detail").fetchSemanticsNodes().isEmpty()
        }
        composeTestRule.onNodeWithTag("store_list").assertIsDisplayed()
        assertTrue("Store closed on the first Back", waitForStoreScreen())
        device.pressBack()
        assertTrue("Could not return to the main screen from store", waitForMainScreen())
        takeScreenshot("store_browsing_end")
    }

    @Test
    fun testStoreActivityNavigation() {
        launchMain()
        openStore()
        device.pressBack()
        assertTrue("Could not return to the main screen", waitForMainScreen())
        takeScreenshot("store_nav_end")
    }

    /** The known final row must be fully outside system bars/cutouts, and open its own detail. */
    @Test
    fun testStoreLastRowClearOfSystemBars() {
        launchMain()
        openStore()
        composeTestRule.onNodeWithTag("store_list").performScrollToIndex(packs.lastIndex)
        val last = composeTestRule.onNodeWithTag(rowTag(packs.lastIndex)).assertIsDisplayed()
        val row = last.fetchSemanticsNode().boundsInWindow
        val safe = safeArea()
        assertTrue("Last row $row extends below the safe area $safe", row.bottom <= safe.bottom)
        assertTrue("Last row $row extends behind a side bar/cutout $safe", row.left >= safe.left && row.right <= safe.right)
        takeScreenshot("store_list_end")
        last.performClick()
        assertDetailOf(packs.lastIndex)
        takeScreenshot("store_list_end_selected")
    }

    /** Actual app startup and browsing must not request notification permission or download. */
    @Test
    fun testNoPermissionDialogBeforeDownload() {
        launchThroughSplashWithoutPermissionDialog()
        assertTrue("Main screen did not appear without a notification dialog", waitForMainScreen())
        assertNoPermissionDialog()
        openStore()
        composeTestRule.onNodeWithTag(rowTag(0)).performClick()
        assertDetailOf(0)
        assertNoPermissionDialog()
        assertNotificationPermissionDenied()
        takeScreenshot("store_without_permission_dialog")
    }

    private fun assertDetailOf(index: Int) {
        composeTestRule.onNodeWithTag("store_detail").assertIsDisplayed()
        for (text in listOf(requireNotNull(packs[index].title), requireNotNull(packs[index].producerName), str(R.string.download))) {
            composeTestRule.onNode(hasText(text) and hasAnyAncestor(hasTestTag("store_detail")), useUnmergedTree = true)
                .assertIsDisplayed()
        }
    }

    private fun safeArea(): Rect {
        var safe: Rect? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .filterIsInstance<FBStoreActivity>().single()
            val insets = requireNotNull(ViewCompat.getRootWindowInsets(activity.window.decorView))
                .getInsets(WindowInsetsCompat.Type.navigationBars() or WindowInsetsCompat.Type.displayCutout())
            val decor = activity.window.decorView
            safe = Rect(0, 0, decor.width, decor.height).apply {
                left += insets.left
                top += insets.top
                right -= insets.right
                bottom -= insets.bottom
            }
        }
        return requireNotNull(safe)
    }

    private fun rowTag(index: Int) = "store_pack_${packs[index].code}"

    private fun assertNotificationPermissionDenied() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            assertEquals(
                "Notification permission must remain denied during startup and browsing",
                PackageManager.PERMISSION_DENIED,
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS),
            )
        }
    }

    private fun launchThroughSplashWithoutPermissionDialog() {
        assertNotificationPermissionDenied()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val monitor = ActivityLifecycleMonitorRegistry.getInstance()
        val createdActivities = CopyOnWriteArrayList<Class<*>>()
        val callback = ActivityLifecycleCallback { activity, stage ->
            if (activity is SplashActivity || activity is MainActivity) {
                Log.i("StoreStartupPermissionTest", "$stage:${activity.javaClass.simpleName}")
                if (stage == Stage.CREATED) createdActivities.add(activity.javaClass)
            }
        }
        instrumentation.runOnMainSync { monitor.addLifecycleCallback(callback) }
        try {
            // No permission handling here: an unexpected dialog must block/fail the test.
            launchApp()
            assertNoPermissionDialog()
            assertTrue("Main screen did not appear without a notification dialog", waitForMainScreen())
            waitForMainButton()
            assertEquals(
                "The permission check must cover Splash followed by Main",
                listOf(SplashActivity::class.java, MainActivity::class.java),
                createdActivities.toList(),
            )
            assertNoPermissionDialog()
            assertNotificationPermissionDenied()
        } finally {
            instrumentation.runOnMainSync { monitor.removeLifecycleCallback(callback) }
        }
    }

    private fun assertNoPermissionDialog() {
        assertFalse("A system permission dialog is showing", device.hasObject(By.pkg(Pattern.compile(".*permissioncontroller.*"))))
    }

    private fun waitForMainButton() {
        // A registered Compose root can still be hidden during the activity/window transition.
        composeTestRule.waitUntil(MAIN_TIMEOUT) {
            try {
                composeTestRule.onNodeWithContentDescription(str(R.string.store)).assertIsDisplayed()
                true
            } catch (_: AssertionError) {
                false
            }
        }
        composeTestRule.onNodeWithContentDescription(str(R.string.store)).assertIsDisplayed()
    }

    private fun launchMain() {
        // Create the real activity after the rule is active, and await its resumed lifecycle.
        mainScenario = ActivityScenario.launch(Intent(context, MainActivity::class.java))
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) handlePermissionDialogs()
        waitForMainButton()
    }

    private fun openStore() {
        composeTestRule.onNodeWithContentDescription(str(R.string.store)).performClick()
        composeTestRule.waitUntil(10000L) {
            // Main's root is removed before Store registers its root. An empty set here is
            // a pending transition; require the real list and its visibility below as before.
            composeTestRule.onAllNodesWithTag("store_list")
                .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
        composeTestRule.onNodeWithTag("store_list").assertIsDisplayed()
        assertTrue("Did not transition to the store screen", waitForStoreScreen())
    }
}
