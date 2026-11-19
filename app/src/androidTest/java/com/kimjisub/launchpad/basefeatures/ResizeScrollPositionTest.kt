package com.kimjisub.launchpad.basefeatures

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.SystemClock
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import com.kimjisub.launchpad.activity.MainActivity
import com.kimjisub.launchpad.activity.PlayActivity
import com.kimjisub.launchpad.activity.SettingsActivity
import com.kimjisub.launchpad.activity.FBStoreActivity
import com.kimjisub.launchpad.manager.PreferenceManager
import com.kimjisub.launchpad.network.StoreCatalog
import com.kimjisub.launchpad.network.FirebaseStoreCatalog
import com.kimjisub.launchpad.network.fb.StoreVO
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.loadKoinModules
import org.koin.dsl.module
import java.io.File

/** Real screens on a tablet: compare the first visible row across rotation and window resizing. */
@RunWith(AndroidJUnit4::class)
class ResizeScrollPositionTest : PlaybackScreenTest() {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun libraryKeepsItsFirstVisibleRow() = withTablet {
        repeat(15) { index ->
            val target = File(screen.workspace, "resize-pack-$index")
            screen.pack().copyRecursively(target, overwrite = true)
            File(target, "info").appendText("\ntitle=Resize Pack ${index.toString().padStart(2, '0')}\n")
        }
        screen.launch(MainActivity::class.java)
        screen.node(By.textStartsWith("Resize Pack"))
        verifyRoundTrips("library", lazy = true)
    }

    @Test fun settingsKeepsItsScrollOffset() = withTablet {
        screen.launch(SettingsActivity::class.java)
        verifyRoundTrips("settings", lazy = false)
    }

    @Test fun playOptionsKeepTheirScrollOffset() = withTablet {
        screen.launch(PlayActivity::class.java) { putExtra("path", screen.pack().path) }
        // This rule owns frame advancement, including the loading screen's coroutine.
        compose.waitUntil(timeoutMillis = 10000) {
            screen.onMain { !screen.vm().unipackLoading && !screen.vm().soundLoadingActive && screen.vm().uiLoaded }
        }
        screen.options()
        verifyRoundTrips("options", lazy = false)
    }

    @Test fun storeKeepsItsFirstVisibleRow() = withTablet {
        val prefs = PreferenceManager(screen.context)
        val previousStoreCount = prefs.prevStoreCount
        val fixture = object : StoreCatalog {
            override fun attach(listener: StoreCatalog.Listener) {
                (0 until 30).reversed().forEach { index ->
                    val key = "resize-store-$index"
                    listener.onAdded(StoreVO(code = key, title = "Resize Store $index", producerName = "Test"), key)
                }
                listener.onCount(30)
            }
            override fun detach() {}
        }
        loadKoinModules(module { factory<StoreCatalog> { fixture } })
        try {
            screen.launch(FBStoreActivity::class.java)
            verifyRoundTrips("store", lazy = true)
        } finally {
            screen.close()
            loadKoinModules(module { factory<StoreCatalog> { FirebaseStoreCatalog() } })
            prefs.prevStoreCount = previousStoreCount
        }
    }

    private fun scroll() = compose.onAllNodes(hasScrollAction()).let { it[it.fetchSemanticsNodes().lastIndex] }
    private fun offset(): Float = scroll().fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()

    private fun verifyRoundTrips(name: String, lazy: Boolean) {
        compose.waitForIdle()
        if (lazy) scroll().performScrollToIndex(if (name == "store") 22 else 12)
        else repeat(4) { scroll().performTouchInput { swipeUp() } }
        compose.waitForIdle()
        val before = offset()
        assertTrue("Fixture must be scrolled: $before", before > 0)
        val firstRow = firstVisibleText()
        screen.capture("resize-$name-before")
        rotate(portrait = true)
        screen.capture("resize-$name-tall")
        val tallRange = scroll().fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].maxValue()
        println("Resize scroll $name tall: offset=${offset()} max=$tallRange")
        if (name == "library") {
            // Trailing padding can leave a few pixels of range even when every row is visible.
            assertEquals("All added library rows must be visible in the taller window", 15,
                screen.device.findObjects(By.textStartsWith("Resize Pack")).count { it.visibleBounds.height() > 0 })
            assertTrue("The original pack must also be visible", screen.node(By.text(FeatureScreen.TITLE)).visibleBounds.height() > 0)
        }
        rotate(portrait = false)
        screen.capture("resize-$name-after-rotation")
        assertEquals("$name lost scroll position after rotation", before, offset(), 0.02f)
        assertEquals("$name changed the first visible row", firstRow, firstVisibleText())
        screen.device.executeShellCommand("wm size 2560x3200")
        settle()
        screen.capture("resize-$name-wide")
        screen.device.executeShellCommand("wm size 1600x2560")
        settle()
        screen.capture("resize-$name-after-window-resize")
        assertEquals("$name lost scroll position after window resize", before, offset(), 0.02f)
        assertEquals(firstRow, firstVisibleText())
        println("Resize scroll $name: offset=$before first=$firstRow preserved across rotation and resize")
    }

    private fun firstVisibleText(): String {
        val bounds = scroll().fetchSemanticsNode().boundsInWindow
        return screen.device.findObjects(By.pkg(screen.context.packageName).text(java.util.regex.Pattern.compile(".+")))
            .filter { val r = it.visibleBounds; r.left >= bounds.left && r.top >= bounds.top && r.bottom <= bounds.bottom }
            .minByOrNull { it.visibleBounds.top }?.text ?: error("No visible scroll content")
    }

    private fun rotate(portrait: Boolean) {
        screen.onMain { screen.resumed().requestedOrientation = if (portrait) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        if (portrait) screen.device.setOrientationNatural() else screen.device.setOrientationLeft()
        screen.await("Rotation did not finish") { screen.onMain { screen.resumed().resources.configuration.orientation ==
            if (portrait) Configuration.ORIENTATION_PORTRAIT else Configuration.ORIENTATION_LANDSCAPE } }
        settle()
    }

    private fun settle() {
        SystemClock.sleep(600)
        screen.freshControls()
        compose.waitForIdle()
    }

    private fun withTablet(action: () -> Unit) {
        fun original(command: String) = Regex("Override (?:size|density): (.+)").find(screen.device.executeShellCommand(command))?.groupValues?.get(1)?.trim() ?: "reset"
        val size = original("wm size")
        val density = original("wm density")
        try {
            screen.device.executeShellCommand("wm density 320")
            screen.device.executeShellCommand("wm size 1600x2560")
            screen.device.setOrientationLeft()
            action()
        } finally {
            screen.close()
            screen.workspace.listFiles().orEmpty().filter { it.name.startsWith("resize-pack-") }.forEach { it.deleteRecursively(); screen.repo.delete(it.name) }
            screen.device.executeShellCommand("wm size $size")
            screen.device.executeShellCommand("wm density $density")
            screen.device.unfreezeRotation()
        }
    }
}
