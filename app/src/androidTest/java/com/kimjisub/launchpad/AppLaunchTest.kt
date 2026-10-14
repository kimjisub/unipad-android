package com.kimjisub.launchpad

import android.os.Build
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.runner.lifecycle.ActivityLifecycleCallback
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import com.kimjisub.launchpad.activity.SplashActivity
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertNotNull
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
    fun testSplashFooterStaysAboveNavigationBar() {
        // Splash moves to Main after a fixed delay, so the footer is measured on Splash's own
        // main thread at each layout instead of being looked up on screen in time.
        val copyright = str(R.string.copyright)
        val lastLayout = AtomicReference<SplashFooterLayout>()
        val callback = ActivityLifecycleCallback { activity, stage ->
            if (stage == Stage.CREATED && activity is SplashActivity) {
                val decor = activity.window.decorView
                decor.viewTreeObserver.addOnPreDrawListener {
                    splashFooterLayout(decor, copyright)?.let(lastLayout::set)
                    true
                }
            }
        }
        withLifecycleCallback(callback) { launchToMainActivity() }
        val layout = lastLayout.get()
        assertNotNull("Splash copyright was never laid out", layout)
        Log.i("SplashFooterInsets", layout.toString())
        assertTrue(
            "Copyright bottom ${layout.footerBottom} overlaps the navigation bar starting at ${layout.safeBottom}",
            layout.footerBottom <= layout.safeBottom,
        )
        assertTrue("Did not transition to the main screen", waitForMainScreen())
    }

    /** Window coordinates of the copyright's bottom edge and of the top of the bottom navigation bar. */
    private data class SplashFooterLayout(val footerBottom: Float, val safeBottom: Int)

    private fun splashFooterLayout(decor: View, copyright: String): SplashFooterLayout? {
        val insets = ViewCompat.getRootWindowInsets(decor) ?: return null
        val navigationBottom = insets
            .getInsets(WindowInsetsCompat.Type.navigationBars() or WindowInsetsCompat.Type.displayCutout())
            .bottom
        val root = findComposeRoot(decor) ?: return null
        val footer = findText(root.semanticsOwner.unmergedRootSemanticsNode, copyright) ?: return null
        return SplashFooterLayout(footer.boundsInWindow.bottom, decor.height - navigationBottom)
    }

    private fun findComposeRoot(view: View): ViewRootForTest? = when (view) {
        is ViewRootForTest -> view
        is ViewGroup -> (0 until view.childCount).firstNotNullOfOrNull { findComposeRoot(view.getChildAt(it)) }
        else -> null
    }

    private fun findText(node: SemanticsNode, text: String): SemanticsNode? =
        if (node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == text } == true) node
        else node.children.firstNotNullOfOrNull { findText(it, text) }

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
