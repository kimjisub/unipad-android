package com.kimjisub.launchpad

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.kimjisub.launchpad.activity.PreviewBitmaps
import com.kimjisub.launchpad.activity.ThemeActivity
import com.kimjisub.launchpad.activity.ThemePreview
import com.kimjisub.launchpad.manager.PreferenceManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThemePreviewLayoutTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun narrowPreviewShowsEveryChainButton() = checkPreview(180.dp, 260.dp)
    @Test fun widePreviewShowsTheWholeLogo() = checkPreview(320.dp, 120.dp)
    @Test fun squarePreviewShowsPadsChainsAndLogo() = checkPreview(220.dp, 220.dp)
    @Test fun logoFreePreviewShowsPadsAndChains() = checkPreview(180.dp, 260.dp, hasLogo = false)

    @Test fun realThemePreviewRestoresSavedLogoFreeTheme() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = PreferenceManager(context)
        val originalTheme = prefs.selectedTheme
        try {
            prefs.selectedTheme = "asset://sskin"
            checkRealPreview()
            assertEquals("Preview check must restore the saved theme", "asset://sskin", prefs.selectedTheme)
        } finally {
            prefs.selectedTheme = originalTheme
        }
    }

    @Test fun realThemePreviewShowsEveryPart() = checkRealPreview()

    private fun checkRealPreview() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = PreferenceManager(context)
        val originalTheme = prefs.selectedTheme
        val arguments = InstrumentationRegistry.getArguments()
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        // Optional physical-display checks use the registered Pixel Fold emulator profile.
        val pose = arguments.getString("themePreviewPose")
        val rotation = arguments.getString("themePreviewRotation")
        require(pose == null || pose in listOf("open", "closed"))
        require(rotation == null || rotation in listOf("0", "1"))
        val previousRotation = device.executeShellCommand("wm user-rotation").trim()
        val previousFixed = device.executeShellCommand("wm fixed-to-user-rotation").trim()
        var scenario: ActivityScenario<ThemeActivity>? = null
        try {
            prefs.selectedTheme = context.packageName
            if (pose != null) {
                println(device.executeShellCommand("cmd device_state state ${if (pose == "closed") 0 else 2}"))
                device.wakeUp()
                device.executeShellCommand("wm dismiss-keyguard")
            }
            if (rotation != null) {
                device.executeShellCommand("wm fixed-to-user-rotation enabled")
                device.executeShellCommand("wm user-rotation lock $rotation")
            }
            scenario = ActivityScenario.launch(ThemeActivity::class.java)
            compose.waitUntil(timeoutMillis = 10000) {
                compose.onAllNodesWithTag("theme_preview_chains", true).fetchSemanticsNodes().isNotEmpty()
            }
            if (rotation != null) {
                compose.waitUntil(timeoutMillis = 10000) { device.displayRotation == rotation.toInt() }
            }
            compose.waitForIdle()
            println("Theme preview display: ${device.displayWidth} x ${device.displayHeight}, rotation=${device.displayRotation}")
            if (pose != null) println(device.executeShellCommand("cmd device_state state"))
            val capture = "theme-preview-${pose ?: "default"}-${rotation ?: "auto"}.png"
            assertTrue("Could not save theme preview", TestCaptures.screenshot(device, capture))
            assertPreviewFits()
        } finally {
            prefs.selectedTheme = originalTheme
            scenario?.close()
            if (rotation != null) {
                device.executeShellCommand("wm user-rotation ${previousRotation.takeIf { it.matches(Regex("free|lock [0-3]")) } ?: "free"}")
                device.executeShellCommand("wm fixed-to-user-rotation ${previousFixed.takeIf { it in listOf("default", "enabled", "disabled") } ?: "default"}")
            }
            if (pose != null) device.executeShellCommand("cmd device_state state reset")
        }
    }

    private fun checkPreview(width: Dp, height: Dp, hasLogo: Boolean = true) {
        val button = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.BLUE)
        }
        val logo = Bitmap.createBitmap(80, 20, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.YELLOW)
        }
        val bitmaps = PreviewBitmaps(null, button.asImageBitmap(), null, null,
            button.asImageBitmap(), false, if (hasLogo) logo.asImageBitmap() else null)
        val scenario = ActivityScenario.launch(ThemeActivity::class.java)
        try {
            scenario.onActivity { activity ->
                activity.setContent { Box(Modifier.size(width, height)) { ThemePreview(bitmaps) } }
            }
            compose.waitForIdle()
            assertPreviewFits(hasLogo)
        } finally {
            scenario.close()
            button.recycle()
            logo.recycle()
        }
    }

    private fun assertPreviewFits(hasLogo: Boolean = true) {
        val preview = compose.onNodeWithTag("theme_preview", useUnmergedTree = true).fetchSemanticsNode()
        val bounds = preview.boundsInRoot
        val tags = listOf("theme_preview_pads", "theme_preview_chains") +
            if (hasLogo) listOf("theme_preview_logo") else emptyList()
        for (tag in tags) {
            val node = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
            val visible = node.boundsInRoot
            assertTrue("$tag must have a visible width", visible.width > 0)
            assertTrue("$tag must have a visible height", visible.height > 0)
            assertEquals("$tag is horizontally clipped", node.size.width.toFloat(), visible.width, 1f)
            assertEquals("$tag is vertically clipped", node.size.height.toFloat(), visible.height, 1f)
            assertTrue("$tag must stay inside preview: $visible in $bounds",
                visible.left >= bounds.left && visible.top >= bounds.top &&
                    visible.right <= bounds.right && visible.bottom <= bounds.bottom)
        }
        val pads = compose.onNodeWithTag("theme_preview_pads", true).fetchSemanticsNode().boundsInRoot
        val chains = compose.onNodeWithTag("theme_preview_chains", true).fetchSemanticsNode().boundsInRoot
        if (hasLogo) {
            val logoBounds = compose.onNodeWithTag("theme_preview_logo", true).fetchSemanticsNode().boundsInRoot
            assertTrue("Pads must leave the logo visible", !pads.overlaps(logoBounds))
            assertTrue("Chains must leave the logo visible", !chains.overlaps(logoBounds))
        } else {
            compose.onNodeWithTag("theme_preview_logo", true).assertDoesNotExist()
        }
        assertEquals("All eight chains must fit beside the pads", pads.height, chains.height, 1f)
        assertEquals("Chain buttons must keep the pad artwork's proportions", pads.width / 8, chains.width, 1f)
    }
}
