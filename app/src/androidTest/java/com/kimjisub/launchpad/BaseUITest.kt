package com.kimjisub.launchpad

import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.Build
import android.view.View
import android.view.ViewGroup
import androidx.test.runner.lifecycle.ActivityLifecycleCallback
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.kimjisub.design.view.ChainView
import com.kimjisub.design.view.PadView
import com.kimjisub.launchpad.activity.MainActivity
import com.kimjisub.launchpad.activity.PlayActivity
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Configurator
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.Until
import java.util.regex.Pattern
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.After
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Base class for UI Automator tests
 * Provides common setup and helper methods
 */
abstract class BaseUITest {

    protected lateinit var device: UiDevice
    protected lateinit var context: Context
    private var originalIdleTimeout: Long? = null

    companion object {
        const val LAUNCH_TIMEOUT = 10000L
        const val PACKAGE_NAME = "com.kimjisub.launchpad.dev" // debug build
        const val MAIN_TIMEOUT = 20000L
        const val PLAY_TIMEOUT = 20000L
        const val MAIN_RESUME_TIMEOUT = 45000L
        private const val PLAY_FLAG_TAP_DP = 50
        private const val SNACKBAR_TIMEOUT = 5000L
        // The resource package differs between build types, so match the id alone.
        private val SNACKBAR_TEXT = Pattern.compile(".*:id/snackbar_text")
        private const val SCREENSHOT_DIR = "/data/local/tmp/unipad_tests"
    }

    @Before
    fun setup() {
        // Initialize UiDevice instance
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        context = ApplicationProvider.getApplicationContext()
        // Animated pads need not become globally idle. Helpers still wait for each observed state.
        val configurator = Configurator.getInstance()
        originalIdleTimeout = configurator.waitForIdleTimeout
        configurator.waitForIdleTimeout = 500L

        // Press Home button to start from a clean state
        device.pressHome()

        // Wait until the home screen appears
        val launcherPackage = device.launcherPackageName
        assertNotNull(launcherPackage)
        device.wait(
            androidx.test.uiautomator.Until.hasObject(By.pkg(launcherPackage).depth(0)),
            LAUNCH_TIMEOUT
        )

        TestUniPack.install(context)
    }

    @After
    fun restoreIdleTimeout() {
        originalIdleTimeout?.let { Configurator.getInstance().waitForIdleTimeout = it }
    }

    /**
     * Launch the app
     */
    protected fun launchApp() {
        val intent = context.packageManager.getLaunchIntentForPackage(PACKAGE_NAME)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        assertNotNull("Could not find launch intent for the app", intent)
        // Await the requested activity's completed onCreate, rather than global queue idleness.
        // Splash can move to Main before startActivitySync's idle callback gets its turn.
        // Retain that API's 45-second launch bound and each caller's screen assertions/timeouts.
        val created = CountDownLatch(1)
        val callback = ActivityLifecycleCallback { activity, stage ->
            if (stage == Stage.CREATED && activity.javaClass.name == intent!!.component!!.className) {
                created.countDown()
            }
        }
        withLifecycleCallback(callback) {
            context.startActivity(intent!!)
            assertTrue("Requested launch activity did not finish creation", created.await(45, TimeUnit.SECONDS))
        }
    }

    private fun withLifecycleCallback(callback: ActivityLifecycleCallback, action: () -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val monitor = ActivityLifecycleMonitorRegistry.getInstance()
        instrumentation.runOnMainSync { monitor.addLifecycleCallback(callback) }
        try {
            action()
        } finally {
            instrumentation.runOnMainSync { monitor.removeLifecycleCallback(callback) }
        }
    }

    /**
     * Handle permission dialogs (multiple possible)
     */
    protected fun handlePermissionDialogs(maxAttempts: Int = 5) {
        repeat(maxAttempts) {
            if (!handlePermissionDialog()) {
                return // No more permission dialogs
            }
            Thread.sleep(1000) // Brief wait for the next dialog
        }
    }

    /**
     * Handle a single permission dialog
     * @return true if dialog was found and handled, false otherwise
     */
    protected fun handlePermissionDialog(vararg _keywords: String): Boolean {
        // Find "Allow" button (supporting multiple languages)
        val allowButtons = listOf(
            "허용", "Allow", "ALLOW",
            "앱 사용 중에만 허용", "While using the app",
            "이번만 허용", "Only this time"
        )

        for (buttonText in allowButtons) {
            try {
                // Find button by text
                val allowButton = device.findObject(
                    UiSelector()
                        .textMatches(".*${buttonText}.*")
                        .clickable(true)
                )

                if (allowButton.exists()) {
                    println("Permission dialog found: $buttonText")
                    allowButton.click()
                    Thread.sleep(500)
                    return true
                }

                // Also try by resource ID
                val allowButtonById = device.findObject(
                    UiSelector()
                        .resourceIdMatches(".*permission_allow.*")
                        .clickable(true)
                )

                if (allowButtonById.exists()) {
                    println("Permission dialog found (by ID)")
                    allowButtonById.click()
                    Thread.sleep(500)
                    return true
                }
            } catch (e: Exception) {
                println("Error clicking permission button: ${e.message}")
            }
        }

        return false
    }

    /**
     * Helper function to find a UI element using polling
     * @param selector BySelector to search for
     * @param timeoutMs Maximum time to wait in milliseconds
     * @param pollingIntervalMs Interval between checks in milliseconds
     * @return true if element was found within timeout, false otherwise
     */
    protected fun waitForElementWithPolling(
        selector: androidx.test.uiautomator.BySelector,
        timeoutMs: Long = 5000L,
        pollingIntervalMs: Long = 500L
    ): Boolean {
        val startTime = System.currentTimeMillis()
        val endTime = startTime + timeoutMs

        while (System.currentTimeMillis() < endTime) {
            if (device.hasObject(selector)) {
                return true
            }
            Thread.sleep(pollingIntervalMs)
        }

        return false
    }

    /**
     * Poll to check if any of multiple UI elements exist
     * @param selectors List of BySelectors to search for
     * @param timeoutMs Maximum time to wait in milliseconds
     * @param pollingIntervalMs Interval between checks in milliseconds
     * @return true if any element was found within timeout, false otherwise
     */
    protected fun waitForAnyElementWithPolling(
        selectors: List<androidx.test.uiautomator.BySelector>,
        timeoutMs: Long = 5000L,
        pollingIntervalMs: Long = 500L
    ): Boolean {
        val startTime = System.currentTimeMillis()
        val endTime = startTime + timeoutMs

        while (System.currentTimeMillis() < endTime) {
            for (selector in selectors) {
                if (device.hasObject(selector)) {
                    return true
                }
            }
            Thread.sleep(pollingIntervalMs)
        }

        return false
    }

    protected fun str(resId: Int): String = context.getString(resId)

    /**
     * Launch the app and wait until MainActivity's Compose screen is shown.
     * The main screen has no view ids since the Compose rewrite, so it is recognised by the
     * store / settings content descriptions and the guide chip text.
     *
     * Splash finishes itself before starting MainActivity, so for a moment the app has no window, and on a
     * loaded emulator MainActivity resumed 10-20 seconds after Splash was created. Wait for it to resume,
     * answering the storage prompt Splash shows on Android 10, before looking for its window.
     */
    protected fun launchToMainScreen() {
        val mainResumed = CountDownLatch(1)
        val callback = ActivityLifecycleCallback { activity, stage ->
            if (stage == Stage.RESUMED && activity is MainActivity) mainResumed.countDown()
        }
        withLifecycleCallback(callback) {
            launchApp()
            val deadline = System.currentTimeMillis() + MAIN_RESUME_TIMEOUT
            while (!mainResumed.await(500, TimeUnit.MILLISECONDS)) {
                assertTrue("Main screen activity did not resume", System.currentTimeMillis() < deadline)
                handlePermissionDialog()
            }
        }
        assertTrue("App did not start", device.wait(Until.hasObject(By.pkg(PACKAGE_NAME)), MAIN_TIMEOUT))
        handlePermissionDialogs()
        assertTrue("Main screen did not appear", waitForMainScreen())
    }

    protected fun waitForMainScreen(timeoutMs: Long = MAIN_TIMEOUT): Boolean =
        waitForAnyElementWithPolling(
            listOf(
                By.pkg(PACKAGE_NAME).desc(str(R.string.store)),
                By.pkg(PACKAGE_NAME).desc(str(R.string.setting)),
                By.pkg(PACKAGE_NAME).textContains(str(R.string.guide_download_new)),
            ),
            timeoutMs
        )

    /** FBStoreActivity is shown when its total panel's pack count label appears. */
    protected fun waitForStoreScreen(timeoutMs: Long = 10000L): Boolean =
        device.wait(Until.hasObject(By.pkg(PACKAGE_NAME).text(str(R.string.STP_count))), timeoutMs)

    /**
     * The exact tagged list row of [TestUniPack], scrolled into view. A title also appears in the
     * detail panel, so finding a clickable ancestor of title text was ambiguous.
     */
    protected fun findTestPackRow(): UiObject2 {
        val selector = By.res("main_pack_${TestUniPack.FOLDER_NAME}")
        var row = device.wait(Until.findObject(selector), 5000L)
        if (row == null) {
            device.findObject(By.scrollable(true))?.scrollUntil(Direction.DOWN, Until.findObject(selector))
            row = device.findObject(selector)
        }
        assertNotNull("Test pack '${TestUniPack.TITLE}' is not in the list", row)
        return row!!
    }

    protected fun clickableAncestor(node: UiObject2): UiObject2 {
        val clickable = clickableAncestorOrNull(node)
        assertNotNull("No clickable ancestor for '${node.text}'", clickable)
        return clickable!!
    }

    private fun clickableAncestorOrNull(node: UiObject2): UiObject2? {
        var current: UiObject2? = node
        while (current != null && !current.isClickable) current = current.parent
        return current
    }

    /** Tap the test pack's row so its detail panel opens and its Play flag slides out. */
    protected fun selectTestPack(): UiObject2 {
        findTestPackRow() // Scroll the exact row into view before attempting to select it.
        val detail = By.res("main_detail_${TestUniPack.FOLDER_NAME}")
        val opened = waitUntil(10000L) {
            if (device.hasObject(detail)) true else {
                // Main refresh can replace the list between lookup and tap. Refetch and retry
                // only while our own detail is absent, so an already selected pack is not toggled off.
                clickFresh { device.findObject(By.res("main_pack_${TestUniPack.FOLDER_NAME}")) }
                false
            }
        }
        assertTrue("Pack panel did not open for the test pack", opened)
        assertTrue("Selected pack has no delete control", device.wait(Until.hasObject(By.desc(str(R.string.cd_delete))), 5000L))
        return findTestPackRow()
    }

    /** Select the test pack (unless already selected) and press its Play flag; returns once the play screen is ready. */
    protected fun openTestPackInPlay() {
        val row = selectTestPack()
        Thread.sleep(700) // Play flag slide-in animation (FLAG_ANIMATION_MS = 500)
        val bounds = row.visibleBounds
        val flagCenterX = bounds.left + (PLAY_FLAG_TAP_DP * context.resources.displayMetrics.density).toInt()
        device.click(flagCenterX, bounds.centerY())
        assertTrue("Play screen did not open", waitForPlayScreen())
    }

    /** PlayActivity is ready when its menu button is shown (after the loading overlay). */
    protected fun waitForPlayScreen(timeoutMs: Long = PLAY_TIMEOUT): Boolean =
        device.wait(Until.hasObject(By.pkg(PACKAGE_NAME).desc(str(R.string.menu))), timeoutMs)

    /** Tap Menu until the option panel is open; Menu fades back in after the panel closes. */
    protected fun openPlayOptions() {
        val opened = waitUntil(5000L) {
            if (device.hasObject(By.res("play_options"))) {
                isPlayOptionsOpen() // Wait for slide-in, without toggling Menu during the animation.
            } else {
                clickFresh { device.findObject(By.desc(str(R.string.menu))) }
                false
            }
        }
        assertTrueWithLabels("Play option panel did not open", opened)
    }

    protected fun closePlayOptions() {
        device.pressBack()
        assertTrue("Play option panel did not close", device.wait(Until.gone(By.res("play_options")), 5000L))
    }

    /** A switch row label in the play option panel; the panel scrolls, so search both ways. */
    protected fun playOption(labelRes: Int): UiObject2 =
        findPlayControl(labelRes, By.res("play_option_$labelRes").hasDescendant(By.checkable(true)))

    /** Play modes are segmented buttons, not switch rows. */
    protected fun playMode(labelRes: Int): UiObject2 = findPlayControl(labelRes, By.text(str(labelRes)))

    private fun findPlayControl(labelRes: Int, selector: androidx.test.uiautomator.BySelector): UiObject2 {
        device.wait(Until.findObject(selector), 1000L)?.let { return it }
        for (direction in listOf(Direction.DOWN, Direction.UP)) {
            // Reacquire the panel for each search: switches and scrolling recompose its nodes.
            val found = device.findObject(By.res("play_options"))
                ?.scrollUntil(direction, Until.findObject(selector))
            if (found != null) return found
        }
        val row = device.findObject(selector)
        assertNotNull("Play option '${str(labelRes)}' not found", row)
        return row!!
    }

    /**
     * Toggle a play option and return its new checked state.
     * A message bar (e.g. "Copied" after stopping a recording) covers the bottom rows of the panel
     * and takes the tap, so wait for it to leave first.
     */
    protected fun togglePlayOption(labelRes: Int): Boolean {
        device.wait(Until.gone(By.res(SNACKBAR_TEXT)), SNACKBAR_TIMEOUT)
        val before = isPlayOptionChecked(labelRes)
        assertTrue("Play option '${str(labelRes)}' could not be tapped", clickFresh { playOption(labelRes) })
        assertTrue(
            "Play option '${str(labelRes)}' did not toggle",
            waitUntil { isPlayOptionChecked(labelRes) != before }
        )
        return !before
    }

    protected fun isPlayOptionChecked(labelRes: Int): Boolean = isSwitchNextToChecked(playOption(labelRes))

    /**
     * Checked state of the switch in the same row as [label]. Compose exposes the label text and
     * the switch as separate nodes, so walk up from the label to the row that holds a switch.
     */
    protected fun isSwitchNextToChecked(label: UiObject2): Boolean {
        var node: UiObject2? = label
        while (node != null) {
            node.findObject(By.checkable(true))?.let { return it.isChecked }
            node = node.parent
        }
        return false
    }

    /** The tagged panel must be fully visible, rather than merely starting its slide-in. */
    protected fun isPlayOptionsOpen(): Boolean = try {
        val panel = device.findObject(By.res("play_options"))
        // The node exists while sliding in. Recomposition may also replace it during this query.
        panel != null && panel.visibleBounds.width() >= (280 * context.resources.displayMetrics.density).toInt()
    } catch (_: StaleObjectException) {
        false // The opening wait will refetch the current node; this is not a closing condition.
    }

    /** The play screen is shown: its Menu button, or its option panel when that is open. */
    protected fun isOnPlayScreen(): Boolean =
        device.hasObject(By.pkg(PACKAGE_NAME).desc(str(R.string.menu))) || isPlayOptionsOpen()

    /** Leave the play screen with the option panel's Quit (Back only toggles the panel). */
    protected fun quitPlayToMain() {
        if (!isPlayOptionsOpen()) openPlayOptions()
        val clicked = clickFresh {
            device.findObject(By.desc(str(R.string.quit)))
                ?: device.findObject(By.pkg(PACKAGE_NAME).scrollable(true))
                    ?.scrollUntil(Direction.UP, Until.findObject(By.desc(str(R.string.quit))))
        }
        assertTrue("Quit not found in the play option panel", clicked)
        assertTrue("Did not return to the main screen after quitting play", waitForMainScreen())
    }

    /** Actual native chain views on the resumed play activity, top to bottom. */
    protected fun chainButtons(): List<Rect> {
        var chains = emptyList<Rect>()
        assertTrue("Chain views did not lay out", waitUntil(5000L) {
            chains = nativeViewBounds(ChainView::class.java).sortedBy { it.top }
            chains.isNotEmpty()
        })
        return chains
    }

    /** Read the actual visible native layout, without accessibility idle waits on each pad tap. */
    private fun nativeViewBounds(type: Class<out View>): List<Rect> {
        val bounds = mutableListOf<Rect>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .filterIsInstance<PlayActivity>().singleOrNull() ?: return@runOnMainSync
            fun collect(view: View) {
                if (!view.isShown) return
                if (type.isInstance(view) && view.width > 0 && view.height > 0) {
                    val xy = IntArray(2).also(view::getLocationOnScreen)
                    bounds += Rect(xy[0], xy[1], xy[0] + view.width, xy[1] + view.height)
                }
                if (view is ViewGroup) for (i in 0 until view.childCount) collect(view.getChildAt(i))
            }
            collect(activity.window.decorView)
        }
        return bounds
    }

    /**
     * assertTrue whose message lists the labels on screen. They are read only on failure: reading
     * them touches every node and, while the play screen animates, took longer than AutoPlay itself.
     */
    protected fun assertTrueWithLabels(message: String, condition: Boolean) {
        if (!condition) fail("$message; labels shown: ${visibleLabels()}")
    }

    /** Content descriptions and texts currently on screen, for failure messages. */
    protected fun visibleLabels(): List<String> =
        try {
            device.findObjects(By.pkg(PACKAGE_NAME)).mapNotNull { o ->
                listOfNotNull(o.contentDescription, o.text).firstOrNull { it.isNotEmpty() }
            }.distinct()
        } catch (e: StaleObjectException) {
            emptyList()
        }

    /** Screen area occupied by the actual 8x8 native pad grid. */
    protected fun padArea(): Rect {
        val pads = nativePadBounds()
        return Rect(pads.first()).apply { pads.drop(1).forEach { union(it) } }
    }

    private fun nativePadBounds(): List<Rect> {
        var pads = emptyList<Rect>()
        assertTrue("The 8x8 test pad grid did not lay out", waitUntil(5000L) {
            pads = nativeViewBounds(PadView::class.java).sortedWith(compareBy<Rect> { it.top }.thenBy { it.left })
            pads.size == 64
        })
        return pads
    }

    /** Tap the exact native pad at row [x], column [y] of the 8x8 test pack, both 0-based. */
    protected fun tapPad(x: Int, y: Int) {
        require(x in 0..7 && y in 0..7)
        val pad = nativePadBounds()[x * 8 + y]
        device.click(pad.centerX(), pad.centerY())
    }

    protected fun tapChain(index: Int) {
        val chains = chainButtons()
        assertTrue("Chain button ${index + 1} not found (${chains.size} shown)", index < chains.size)
        device.click(chains[index].centerX(), chains[index].centerY())
    }

    /** Hardware volume keys show the system volume panel over the right edge (Menu button). */
    protected fun waitForVolumePanelGone() {
        device.wait(Until.gone(By.pkg("com.android.systemui").res("com.android.systemui", "volume_dialog")), 8000L)
    }

    /**
     * Click what [find] returns, looking it up again when the node went stale in between: the play
     * screen recomposes its buttons while LEDs and AutoPlay run. Returns false if never found.
     */
    protected fun clickFresh(timeoutMs: Long = 3000L, find: () -> UiObject2?): Boolean =
        waitUntil(timeoutMs) {
            try {
                find()?.click() != null
            } catch (e: StaleObjectException) {
                false
            }
        }

    /**
     * Run [block] with UI Automator's idle wait shortened to [idleTimeoutMs]. Every query first waits
     * for the screen to go idle (10 s by default); while AutoPlay runs with Feedback light on, the
     * play screen redraws on every pad and never goes idle, so each query would stall for 10 s.
     */
    protected fun <T> withShortIdleWait(idleTimeoutMs: Long = 500L, block: () -> T): T {
        val configurator = Configurator.getInstance()
        val previous = configurator.waitForIdleTimeout
        configurator.waitForIdleTimeout = idleTimeoutMs
        try {
            return block()
        } finally {
            configurator.waitForIdleTimeout = previous
        }
    }

    protected fun waitUntil(timeoutMs: Long = 3000L, condition: () -> Boolean): Boolean {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            if (condition()) return true
            Thread.sleep(200)
        }
        return condition()
    }

    /**
     * Save a screenshot to /data/local/tmp/unipad_tests (pull with adb). Taken by the shell: the app
     * cannot write to shared storage on API 30+, which left the old /sdcard/Pictures path empty.
     */
    protected fun takeScreenshot(name: String) {
        val path = "$SCREENSHOT_DIR/${name}_API${Build.VERSION.SDK_INT}.png"
        try {
            device.executeShellCommand("mkdir -p $SCREENSHOT_DIR")
            device.executeShellCommand("screencap -p $path")
            println("Screenshot saved: $path")
        } catch (e: Exception) {
            println("Failed to save screenshot: ${e.message}")
        }
    }
}
