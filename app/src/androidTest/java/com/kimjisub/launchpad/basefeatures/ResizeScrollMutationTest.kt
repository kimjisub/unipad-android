package com.kimjisub.launchpad.basefeatures

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.kimjisub.launchpad.TestCaptures
import com.kimjisub.launchpad.activity.SettingsActivity
import com.kimjisub.launchpad.ui.compose.rememberResizeLazyListState
import com.kimjisub.launchpad.ui.compose.resizeVerticalScroll
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercise the production helpers under real user-input and fling scroll locks. */
@RunWith(AndroidJUnit4::class)
class ResizeScrollMutationTest {
    @get:Rule val compose = createAndroidComposeRule<SettingsActivity>()
    private var viewportHeight by mutableStateOf(120.dp)
    private var rows by mutableIntStateOf(12)
    private var resetKey by mutableIntStateOf(0)
    private var listState: LazyListState? = null

    private fun content(lazy: Boolean, wrapWidth: Boolean = false) {
        compose.runOnUiThread {
            compose.activity.setContent {
                Box(Modifier.fillMaxWidth().height(viewportHeight)) {
                    if (lazy) {
                        val state = rememberResizeLazyListState(resetKey)
                        listState = state
                        LazyColumn(state = state, modifier = (if (wrapWidth) Modifier.fillMaxHeight() else Modifier.fillMaxSize()).testTag("scroll")) {
                            items(rows) { Text("Row $it", Modifier.height(20.dp), fontSize = 12.sp, lineHeight = 16.sp) }
                        }
                    } else {
                        Column(Modifier.fillMaxSize().resizeVerticalScroll().testTag("scroll")) {
                            repeat(rows) { Text("Row $it", Modifier.height(20.dp), fontSize = 12.sp, lineHeight = 16.sp) }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun value() = compose.onNodeWithTag("scroll").fetchSemanticsNode()
        .config[SemanticsProperties.VerticalScrollAxisRange].value()

    private fun resize(height: Int) {
        compose.runOnIdle { viewportHeight = height.dp }
        compose.waitForIdle()
    }

    private fun capture(name: String) {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        assertTrue(TestCaptures.screenshot(device, "resize-mutation/$name.png"))
    }

    private fun roundTrip(lazy: Boolean, drag: Boolean, wrapWidth: Boolean = false) {
        content(lazy, wrapWidth)
        compose.onNodeWithTag("scroll").performTouchInput { swipeUp() }
        compose.waitForIdle()
        if (drag) {
            compose.onNodeWithTag("scroll").performTouchInput {
                down(center)
                repeat(10) { moveBy(Offset(0f, -15f)) }
            }
            resize(112)
            compose.onNodeWithTag("scroll").performTouchInput {
                repeat(5) { moveBy(Offset.Zero) }
                up()
            }
            compose.waitForIdle()
            resize(120)
        }
        val before = value()
        val rowBefore = listState?.let { it.firstVisibleItemIndex to it.firstVisibleItemScrollOffset }
        assertTrue("The fixture must be scrolled", before > 0)
        val label = "${if (lazy) "list" else "column"}-drag-$drag-wrap-$wrapWidth"
        capture("$label-before")
        resize(280)
        assertEquals("All content must fit", 0f, value(), 0f)
        capture("$label-tall")
        resize(120)
        capture("$label-after")
        println("Resize mutation $label before=$before after=${value()} rowBefore=$rowBefore")
        if (lazy) assertEquals(rowBefore, listState!!.let { it.firstVisibleItemIndex to it.firstVisibleItemScrollOffset })
        else assertEquals("A resize during dragging must not disable future restoration", before, value(), 1f)
    }

    @Test fun columnResizeControl() = roundTrip(lazy = false, drag = false)
    @Test fun listResizeControl() = roundTrip(lazy = true, drag = false)
    @Test fun columnRestoresAfterResizingDuringDrag() = roundTrip(lazy = false, drag = true)
    @Test fun listRestoresAfterResizingDuringDrag() = roundTrip(lazy = true, drag = true)
    @Test fun listRestoresAfterWidthChangesDuringFling() = roundTrip(lazy = true, drag = false, wrapWidth = true)

    @Test fun anExplicitResetDuringDraggingStillStartsAtTheTop() {
        content(lazy = true)
        compose.onNodeWithTag("scroll").performTouchInput { swipeUp() }
        compose.waitForIdle()
        assertTrue(value() > 0)
        compose.onNodeWithTag("scroll").performTouchInput {
            down(center)
            repeat(10) { moveBy(Offset(0f, -15f)) }
        }
        compose.runOnIdle { resetKey++ }
        compose.onNodeWithTag("scroll").performTouchInput { up() }
        compose.waitForIdle()
        assertEquals(0 to 0, listState!!.let { it.firstVisibleItemIndex to it.firstVisibleItemScrollOffset })
    }

    @Test fun contentShrinkAndGrowthDoNotRestoreAnObsoleteOffset() {
        content(lazy = false)
        compose.onNodeWithTag("scroll").performTouchInput { swipeUp() }
        compose.waitForIdle()
        assertTrue(value() > 0)
        compose.runOnIdle { rows = 5 }
        compose.waitForIdle()
        assertEquals(0f, value(), 0f)
        compose.runOnIdle { rows = 12 }
        compose.waitForIdle()
        assertEquals("Growing content alone must not jump to an obsolete offset", 0f, value(), 0f)
    }

    private fun fling(growContent: Boolean) {
        rows = 40
        content(lazy = false)
        compose.mainClock.autoAdvance = false
        try {
            compose.onNodeWithTag("scroll").performTouchInput { swipeUp(durationMillis = 120) }
            compose.mainClock.advanceTimeByFrame()
            if (growContent) compose.runOnUiThread { rows = 45 }
            compose.mainClock.advanceTimeByFrame()
            val afterLayout = value()
            val max = compose.onNodeWithTag("scroll").fetchSemanticsNode()
                .config[SemanticsProperties.VerticalScrollAxisRange].maxValue()
            assertTrue("The fling must have room to continue", afterLayout < max)
            compose.mainClock.advanceTimeBy(160)
            println("Resize fling grow=$growContent afterLayout=$afterLayout after=${value()}")
            assertTrue("A content-height change must not cancel an active fling", value() > afterLayout + 1f)
        } finally {
            compose.mainClock.autoAdvance = true
            compose.waitForIdle()
        }
    }

    @Test fun flingControl() = fling(growContent = false)
    @Test fun contentGrowthKeepsTheFlingRunning() = fling(growContent = true)
}
