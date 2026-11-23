package com.kimjisub.launchpad.midi

import android.content.Context
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kimjisub.launchpad.R
import com.kimjisub.launchpad.activity.ComputerPadActivity
import com.kimjisub.launchpad.activity.ComputerPadController
import com.kimjisub.launchpad.activity.ComputerPadScreen
import com.kimjisub.launchpad.activity.ComputerPadTags
import com.kimjisub.launchpad.ui.theme.UniPadTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

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
	private val connection = UsbPeripheralConnection(nothingFound, MidiTransportGate())
	private lateinit var scenario: ActivityScenario<ComputerPadActivity>

	@Before fun launch() {
		ComputerPadActivity.connectionFactory = { UsbPeripheralConnection(nothingFound, MidiTransportGate()) }
		scenario = ActivityScenario.launch(ComputerPadActivity::class.java)
	}

	@After fun cleanup() {
		scenario.close()
		ComputerPadActivity.connectionFactory = originalFactory
		connection.close()
	}

	private fun show(width: Int, height: Int, fontScale: Float = 1f) {
		scenario.onActivity { activity ->
			activity.setContent {
				val density = LocalDensity.current
				CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
					UniPadTheme {
						Box(Modifier.size(width.dp, height.dp)) { ComputerPadScreen(ComputerPadController(connection, true)) { } }
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
}
