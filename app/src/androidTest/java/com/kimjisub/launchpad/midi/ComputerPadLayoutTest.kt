package com.kimjisub.launchpad.midi

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.util.Log
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.kimjisub.launchpad.R
import com.kimjisub.launchpad.TestCaptures
import com.kimjisub.launchpad.activity.ComputerPadActivity
import com.kimjisub.launchpad.activity.ComputerPadController
import com.kimjisub.launchpad.activity.ComputerPadScreen
import com.kimjisub.launchpad.activity.ComputerPadTags
import com.kimjisub.launchpad.ui.compose.barsAndCutout
import com.kimjisub.launchpad.ui.theme.UniPadTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/** Window size and text size, drawn inside the pad screen's own activity with nothing connected. */
@RunWith(AndroidJUnit4::class)
class ComputerPadLayoutTest {
	@get:Rule val compose = createEmptyComposeRule()
	private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
	private val originalFactory = ComputerPadActivity.connectionFactory
	private val nothingFound = object : PeripheralMidiAccess {
		override fun devices() = emptyList<PeripheralDevice>()
		override fun watch(changed: () -> Unit, removed: (Int) -> Unit) = AutoCloseable { }
		override fun open(id: Int, completed: (PeripheralMidiAccess.Device?) -> Unit) = completed(null)
	}
	private val oneFound = object : PeripheralMidiAccess {
		override fun devices() = listOf(PeripheralDevice(1, "computer", listOf(PeripheralDevice.Port(0, "press")), listOf(PeripheralDevice.Port(0, "lights"))))
		override fun watch(changed: () -> Unit, removed: (Int) -> Unit) = AutoCloseable { }
		override fun open(id: Int, completed: (PeripheralMidiAccess.Device?) -> Unit) = completed(null)
	}
	private val connection = UsbPeripheralConnection(nothingFound, MidiTransportGate())
	private val found = UsbPeripheralConnection(oneFound, MidiTransportGate())
	private lateinit var scenario: ActivityScenario<ComputerPadActivity>
	/** How the phone was held before a test turned it; the next test starts from the same orientation. */
	private var heldBefore: Boolean? = null

	@Before fun launch() {
		ComputerPadActivity.connectionFactory = { UsbPeripheralConnection(nothingFound, MidiTransportGate()) }
		scenario = ActivityScenario.launch(ComputerPadActivity::class.java)
	}

	@After fun cleanup() {
		heldBefore?.let { landscape ->
			turn(landscape)
			scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
		}
		scenario.close()
		ComputerPadActivity.connectionFactory = originalFactory
		connection.close()
		found.close()
	}

	/** The screen in a [width] by [height] dp window of its own; the activity's system bars are not part of it. */
	private fun show(width: Int, height: Int, fontScale: Float = 1f, shown: UsbPeripheralConnection = connection) {
		scenario.onActivity { activity ->
			activity.setContent {
				val density = LocalDensity.current
				CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
					UniPadTheme {
						Box(Modifier.size(width.dp, height.dp).consumeWindowInsets(WindowInsets.barsAndCutout).testTag(WINDOW)) {
							// A new screen for each size: the same composition would keep the previous size's scroll positions.
							key(width, height, fontScale, shown) { ComputerPadScreen(ComputerPadController(shown, true)) { } }
						}
					}
				}
			}
		}
		compose.waitForIdle()
	}

	@Test fun aWindowTooSmallFor32dpPadsLocksInputAndSaysSo() {
		show(300, 220)
		compose.onNodeWithTag(ComputerPadTags.TOO_SMALL).assertIsDisplayed()
	}

	/** With larger text the lock note can be taller than the pads' square; all of it can still be read. */
	@Test fun theTooSmallNoteCanBeReadWholeWithLargeText() {
		val failures = mutableListOf<String>()
		for ((width, height) in listOf(300 to 220, 220 to 300)) for (fontScale in listOf(1f, 1.3f, 2f)) {
			show(width, height, fontScale)
			val label = "${width}x$height dp, text x$fontScale"
			val note = compose.onNodeWithTag(ComputerPadTags.TOO_SMALL)
			val text = compose.onNodeWithText(context.getString(R.string.computer_pad_too_small))
			val layout = mutableListOf<TextLayoutResult>()
			text.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layout) }
			if (layout.single().hasVisualOverflow) failures += "$label: the note's text is cut off by its own height"
			// The note's end, scrolled to when the note scrolls, lies inside it.
			if (SemanticsActions.ScrollBy in note.fetchSemanticsNode().config) note.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 100_000f) }
			val box = note.fetchSemanticsNode().boundsInRoot
			val shown = text.fetchSemanticsNode()
			val end = shown.positionInRoot.y + shown.size.height
			if (end > box.bottom + 1f) failures += "$label: the note's text ends at $end below the note's ${box.bottom}"
			capture("too_small_${width}x${height}_text${(fontScale * 10).toInt()}")
		}
		assertTrue(failures.joinToString("\n"), failures.isEmpty())
	}

	/** Sizes stay inside a portrait phone window, which would otherwise clamp them. */
	@Test fun aPhoneSizedWindowHasNoLockAndButtonsAreAtLeast48dp() {
		show(400, 700)
		compose.onNodeWithTag(ComputerPadTags.TOO_SMALL).assertDoesNotExist()
		compose.onNode(hasText(context.getString(R.string.computer_pad_search_again)) and hasClickAction()).assertHeightIsAtLeast(48.dp)
		compose.onNode(hasText(context.getString(R.string.computer_pad_help)) and hasClickAction()).assertHeightIsAtLeast(48.dp)
	}

	@Test fun doubleSizedTextScrollsTheGuideButNotThePads() {
		show(400, 700, fontScale = 2f)
		compose.onNodeWithTag(ComputerPadTags.TOO_SMALL).assertDoesNotExist()
		val grid = compose.onNodeWithTag(ComputerPadTags.GRID).fetchSemanticsNode().boundsInRoot
		assertTrue("the pads must keep their place: $grid", grid.top >= 0f && grid.width > 0f)
		// The actions stay below the guide without scrolling; the guide above them scrolls.
		compose.onNode(hasText(context.getString(R.string.computer_pad_help)) and hasClickAction()).assertIsDisplayed()
		compose.onNodeWithText(context.getString(R.string.computer_pad_preview)).performScrollTo().assertIsDisplayed()
		assertEquals(grid, compose.onNodeWithTag(ComputerPadTags.GRID).fetchSemanticsNode().boundsInRoot)
	}

	/**
	 * A phone split in two, at the default, a larger and the largest common text size, on the step that
	 * offers three actions. The way back and every action are on screen whole, directly or by scrolling.
	 */
	@Test fun aShortWindowKeepsTheWayBackAndEveryActionWithinReach() {
		found.refresh()
		compose.waitUntil(5000) { found.devices.value.isNotEmpty() }
		val actions = listOf(R.string.computer_pad_connect, R.string.computer_pad_search_again, R.string.computer_pad_help)
		val failures = mutableListOf<String>()
		val measured = mutableListOf<String>()
		for (window in SHORT_WINDOWS) for (fontScale in listOf(1f, 1.3f, 2f)) {
			turn(window.landscape)
			show(window.width, window.height, fontScale, found)
			val label = "${window.name} ${window.width}x${window.height} dp, text x$fontScale"
			compose.onNodeWithText(context.getString(R.string.computer_pad_found)).assertExists()
			val bounds = compose.onNodeWithTag(WINDOW).fetchSemanticsNode().boundsInRoot
			// A window this device's screen cannot hold is clamped and says nothing about the asked size.
			val density = context.resources.displayMetrics.density
			if (abs(window.width * density - bounds.width) > 1f || abs(window.height * density - bounds.height) > 1f) {
				Log.i(TAG, "$label skipped: this screen holds only ${bounds.width / density}x${bounds.height / density} dp")
				continue
			}
			measured += label
			failures += listOfNotNull(
				reach(label, bounds, hasContentDescription(context.getString(R.string.computer_pad_back)) and hasClickAction()),
				*actions.map { reach(label, bounds, hasText(context.getString(it)) and hasClickAction()) }.toTypedArray(),
			)
			capture("short_${window.name}_text${(fontScale * 10).toInt()}")
		}
		assertTrue("this screen held none of the windows", measured.isNotEmpty())
		assertTrue(failures.joinToString("\n"), failures.isEmpty())
	}

	/** Scrolls to the node when something holding it scrolls; null when it then lies whole inside [window]. */
	private fun reach(label: String, window: Rect, matcher: SemanticsMatcher): String? {
		val node = compose.onNode(matcher)
		val scrolls = generateSequence(node.fetchSemanticsNode().parent) { it.parent }.any { SemanticsActions.ScrollBy in it.config }
		if (scrolls) node.performScrollTo()
		val semantics = node.fetchSemanticsNode()
		val top = semantics.positionInRoot.y
		val bottom = top + semantics.size.height
		// boundsInRoot is clipped by a scrolling parent, so a button squeezed into a too-short one shows there.
		val visible = semantics.boundsInRoot.height
		val name = semantics.config.getOrElseNullable(SemanticsProperties.Text) { null }?.joinToString()
			?: semantics.config.getOrElseNullable(SemanticsProperties.ContentDescription) { null }?.joinToString()
		return if (top >= window.top - 1f && bottom <= window.bottom + 1f && visible >= semantics.size.height - 1f) null
		else "$label: '$name' spans $top..$bottom (visible $visible of ${semantics.size.height}) in a window of ${window.top}..${window.bottom}, scrolls=$scrolls"
	}

	private fun turn(landscape: Boolean) {
		if (heldBefore == null) scenario.onActivity {
			heldBefore = it.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
		}
		scenario.onActivity {
			it.requestedOrientation = if (landscape) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
		}
		compose.waitUntil(10_000) {
			var turned = false
			scenario.onActivity { turned = (it.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) == landscape }
			turned
		}
	}

	private fun capture(name: String) {
		compose.waitForIdle()
		TestCaptures.screenshot(UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()), "computer_pad/$name.png")
	}

	/** [landscape] is how the phone is held; the window's own shape decides the screen's layout. */
	private data class ShortWindow(val name: String, val landscape: Boolean, val width: Int, val height: Int)

	private companion object {
		const val TAG = "ComputerPadLayoutTest"
		const val WINDOW = "short_window"
		val SHORT_WINDOWS = listOf(
			// Upright phone split top and bottom: a nearly square half, wider than tall.
			ShortWindow("upright_half", landscape = false, width = 411, height = 400),
			// The same half a little taller than wide, which stacks the guide above the pads.
			ShortWindow("upright_half_tall", landscape = false, width = 411, height = 440),
			// Phone on its side split left and right.
			ShortWindow("sideways_half", landscape = true, width = 450, height = 380),
		)
	}
}
