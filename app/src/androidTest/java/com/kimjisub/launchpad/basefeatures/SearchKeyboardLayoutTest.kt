package com.kimjisub.launchpad.basefeatures

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import com.kimjisub.launchpad.R
import com.kimjisub.launchpad.activity.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SearchKeyboardLayoutTest : PlaybackScreenTest() {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun importButtonIsFullyReachableAboveKeyboardAtDefaultFont() = checkImportButton("1.0")

    @Test fun importButtonIsFullyReachableAboveKeyboardAtLargestFont() = checkImportButton("2.0")

    private fun checkImportButton(fontScale: String) {
        val previous = screen.device.executeShellCommand("settings get system font_scale").trim()
        try {
            screen.device.executeShellCommand("settings put system font_scale $fontScale")
            screen.launch(MainActivity::class.java)
            compose.waitForIdle()
            screen.node(By.text(FeatureScreen.TITLE))
            screen.await("Font scale did not reach the activity") {
                screen.onMain { screen.resumed().resources.configuration.fontScale == fontScale.toFloat() }
            }
            compose.onNodeWithContentDescription(screen.text(R.string.transfer_search)).performClick()
            compose.onNode(hasSetTextAction()).performTextInput("Base")
            compose.waitForIdle()
            var keyboard = -1 to 0L
            screen.await("Keyboard did not settle") {
                val height = screen.onMain {
                    ViewCompat.getRootWindowInsets(screen.resumed().window.decorView)
                        ?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: 0
                }
                val now = android.os.SystemClock.elapsedRealtime()
                if (height != keyboard.first) keyboard = height to now
                height > 0 && now - keyboard.second >= 500
            }
            val keyboardTop = screen.onMain { screen.bounds(screen.resumed().window.decorView).bottom - keyboard.first }
            for (description in listOf(R.string.transfer_search, R.string.store, R.string.import_unipack)) {
                val control = compose.onNode(hasContentDescription(screen.text(description)) and hasClickAction())
                    .fetchSemanticsNode()
                assertTrue("Search action ${screen.text(description)} must remain above the keyboard", control.boundsInWindow.bottom <= keyboardTop)
                assertEquals("Search action must remain fully visible", control.size.height.toFloat(), control.boundsInWindow.height, 1f)
            }
            val label = screen.text(R.string.guide_import_external)
            val target = By.clickable(true).hasDescendant(By.text(label))
            val buttonMatcher = hasText(label) and hasClickAction()
            val prefix = "search-import-font-${fontScale.replace('.', '-')}"
            screen.capture("$prefix-before-drag")
            var previousBounds: android.graphics.Rect? = null
            for (drag in 1..20) {
                val field = screen.node(By.focused(true)).visibleBounds
                val button = compose.onAllNodes(buttonMatcher).fetchSemanticsNodes().singleOrNull()
                if (button != null && button.boundsInWindow.bottom <= keyboardTop &&
                    button.boundsInWindow.height >= button.size.height - 1f) break
                val touchSlop = screen.onMain { android.view.ViewConfiguration.get(screen.context).scaledTouchSlop }
                val clippedAtTop = button != null && button.positionInWindow.y < field.bottom + 12
                val remaining = if (clippedAtTop) field.bottom + 12 - button.positionInWindow.y
                    else (button?.let { it.positionInWindow.y + it.size.height } ?: screen.device.displayHeight.toFloat()) - keyboardTop + 8
                val maxDistance = keyboardTop - field.top - 16
                assertTrue("No room for a physical drag: field=$field keyboardTop=$keyboardTop touchSlop=$touchSlop", maxDistance >= touchSlop + 12)
                val distance = (remaining.toInt() + touchSlop).coerceIn(touchSlop + 12, maxDistance)
                val start = if (clippedAtTop) field.bottom + 8 else keyboardTop - 8
                val end = if (clippedAtTop) minOf(keyboardTop - 8, start + distance) else start - distance
                // Start in the results and keep the finger down while passing over the search field.
                // A long physical drag distinguishes limited gesture travel from a clipped button.
                screen.device.swipe(field.centerX(), start, field.centerX(), end, 20)
                compose.waitForIdle()
                val bounds = screen.device.findObject(target)?.visibleBounds
                println("font=$fontScale drag=$drag from=(${field.centerX()},$start) to=(${field.centerX()},$end) field=$field button=$bounds keyboardTop=$keyboardTop")
                if (bounds != null && bounds == previousBounds) break
                previousBounds = bounds
            }
            screen.capture("$prefix-after-drag")
            val button = compose.onNode(buttonMatcher).fetchSemanticsNode()
            val visible = button.boundsInWindow
            println("font=$fontScale buttonVisible=$visible buttonSize=${button.size} keyboardTop=$keyboardTop")
            assertTrue("Import button bottom $visible must be above keyboard $keyboardTop", visible.bottom <= keyboardTop)
            assertEquals("Import button must retain its full height above the list's top", button.size.height.toFloat(), visible.height, 1f)
            assertEquals("Import button must retain its full width", button.size.width.toFloat(), visible.width, 1f)
            screen.device.pressBack()
            compose.waitForIdle()
            // Dismissing the keyboard returns the sorting/search controls without clearing the query.
            compose.onNode(hasContentDescription(screen.text(R.string.transfer_search)) and hasClickAction()).assertExists()
            compose.onNode(hasSetTextAction() and hasText("Base")).assertExists()
            screen.capture("$prefix-keyboard-dismissed")
            screen.node(By.clazz("android.widget.EditText")).click()
            screen.await("Search keyboard did not reopen") {
                screen.onMain {
                    ViewCompat.getRootWindowInsets(screen.resumed().window.decorView)
                        ?.isVisible(WindowInsetsCompat.Type.ime()) == true
                }
            }
            compose.onNode(hasContentDescription(screen.text(R.string.transfer_search)) and hasClickAction()).performClick()
            compose.waitForIdle()
            assertTrue("Search close action must close the field while the keyboard is open",
                compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isEmpty())
            screen.capture("$prefix-search-closed")
        } finally {
            screen.device.executeShellCommand(if (previous == "null") "settings delete system font_scale"
                else "settings put system font_scale $previous")
        }
    }
}
