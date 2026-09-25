package com.kimjisub.launchpad

import android.content.Context
import android.content.Intent
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.kimjisub.launchpad.activity.TransferActivity
import com.kimjisub.launchpad.manager.WorkspaceManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.regex.Pattern

/**
 * The configuration step of TransferActivity hosts the pack search field. These tests open that
 * step, use the search field, and push the activity through recreation and background/foreground
 * cycles, failing if the field is missing or the process dies while the field attaches.
 */
@RunWith(AndroidJUnit4::class)
class TransferConfigurationLifecycleTest {

	private lateinit var context: Context
	private lateinit var device: UiDevice
	private lateinit var workspaceDir: File
	private val createdDirs = mutableListOf<File>()
	private val packNames = List(PACK_COUNT) { "$PACK_PREFIX$it" }

	@Before
	fun setUp() {
		context = ApplicationProvider.getApplicationContext()
		device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
		workspaceDir = WorkspaceManager(context).availableWorkspaces.first().file
		packNames.forEach { name ->
			File(workspaceDir, name).also {
				it.mkdirs()
				createdDirs += it
			}
		}
	}

	@After
	fun tearDown() {
		createdDirs.forEach { it.deleteRecursively() }
	}

	@Test
	fun workspaceSourceSurvivesLifecycleAndEdits() {
		val intent = transferIntent().putExtra(TransferActivity.EXTRA_SOURCE_TYPE, "workspace")
			.putExtra(TransferActivity.EXTRA_SOURCE_PATH, workspaceDir.path)
		ActivityScenario.launch<TransferActivity>(intent).use { scenario ->
			repeat(ROUNDS) { round ->
				searchAndSelect(round)
				exerciseLifecycle(scenario, round)
			}
			searchAndSelect(ROUNDS)
			assertEquals(Lifecycle.State.RESUMED, scenario.state)
		}
	}

	@Test
	fun sourceSelectionSurvivesLifecycle() {
		ActivityScenario.launch<TransferActivity>(transferIntent()).use { scenario ->
			val workspaceName = WorkspaceManager(context).availableWorkspaces.first().name
			var sourcePickerShown = true
			repeat(ROUNDS) { round ->
				if (sourcePickerShown) clickText(workspaceName)
				searchAndSelect(round)
				// Screen state lives in the activity, so only recreation brings back the source picker.
				sourcePickerShown = exerciseLifecycle(scenario, round)
			}
			assertEquals(Lifecycle.State.RESUMED, scenario.state)
		}
	}

	/** Filters the list down to one test pack through the search field, toggles it, then widens the query again. */
	private fun searchAndSelect(round: Int) {
		val target = "$PACK_PREFIX${round % PACK_COUNT}"

		showAllPacks()
		enterQuery(target)
		assertTrue("$target should be listed for the query", device.wait(Until.hasObject(itemRow(target)), WAIT_MS))
		// Every other pack was on screen a moment ago, so its row leaving proves the filter reached the list.
		(packNames - target).forEach {
			assertTrue("$it should be filtered out", device.wait(Until.gone(itemRow(it)), WAIT_MS))
		}
		toggleItem(target)

		showAllPacks()
	}

	private fun showAllPacks() {
		enterQuery(PACK_PREFIX)
		packNames.forEach {
			assertTrue("$it should be listed for the prefix", device.wait(Until.hasObject(itemRow(it)), WAIT_MS))
		}
	}

	private fun toggleItem(name: String) {
		val countBefore = selectedCount()
		val wasChecked = device.hasObject(itemRow(name, checked = true))
		clickItemRow(name)

		val expectedCount = if (wasChecked) countBefore - 1 else countBefore + 1
		assertTrue(
			"Selected count should change from $countBefore to $expectedCount after toggling $name",
			device.wait(Until.hasObject(By.text(context.getString(R.string.transfer_selected_count, expectedCount))), WAIT_MS),
		)
		assertTrue("$name checkbox should flip", device.wait(Until.hasObject(itemRow(name, checked = !wasChecked)), WAIT_MS))
	}

	private fun exerciseLifecycle(scenario: ActivityScenario<TransferActivity>, round: Int): Boolean {
		val recreate = round % 2 == 0
		if (recreate) {
			scenario.recreate()
			// Each round leaves PACK_PREFIX in the query, which lives in the composition and is not restored.
			// Until it is gone, UI Automator can still be reading the tree of the activity that was replaced.
			assertTrue("Replaced screen should leave", device.wait(Until.gone(searchFieldSelector().text(PACK_PREFIX)), WAIT_MS))
		} else {
			scenario.moveToState(Lifecycle.State.CREATED)
			scenario.moveToState(Lifecycle.State.RESUMED)
		}
		device.waitForIdle()
		assertFalse("TransferActivity finished unexpectedly", scenario.state == Lifecycle.State.DESTROYED)
		return recreate
	}

	private fun transferIntent() = Intent(context, TransferActivity::class.java)

	private fun enterQuery(query: String) {
		retryOnStale { searchField().text = query }
		assertTrue("Search field should hold \"$query\"", device.wait(Until.hasObject(searchFieldSelector().text(query)), WAIT_MS))
	}

	private fun searchFieldSelector() = By.clazz(EDIT_TEXT_CLASS)

	private fun searchField(): UiObject2 {
		val field = device.wait(Until.findObject(searchFieldSelector()), WAIT_MS)
		assertNotNull("Search field is not shown", field)
		return field
	}

	private fun clickText(text: String) = clickWhenShown(By.text(text))

	private fun clickItemRow(name: String) = clickWhenShown(itemRow(name))

	private fun clickWhenShown(selector: BySelector) = retryOnStale {
		val target = device.wait(Until.findObject(selector), WAIT_MS)
		assertNotNull("Click target is not shown: $selector", target)
		target.click()
		device.waitForIdle()
	}

	/**
	 * Filtering and toggling recompose the list, so a node found just before it is used can be
	 * replaced by the time it is read or clicked. A stale node is never acted on, so the lookup is
	 * repeated a bounded number of times before the failure is reported.
	 */
	private fun <T> retryOnStale(action: () -> T): T {
		repeat(STALE_RETRIES - 1) {
			device.waitForIdle()
			try {
				return action()
			} catch (_: StaleObjectException) {
			}
		}
		device.waitForIdle()
		return action()
	}

	/**
	 * A pack row in the transfer checklist: a clickable row holding its checkbox and a TextView label.
	 * The search field (EditText) carries the same text while filtering, so matching on text alone hits it instead.
	 * The checkbox state is part of the selector because the row is recomposed right after a toggle.
	 */
	private fun itemRow(name: String, checked: Boolean? = null) = By.clickable(true)
		.hasChild(By.clazz(CHECK_BOX_CLASS).apply { checked?.let { checked(it) } })
		.hasChild(By.clazz(TEXT_VIEW_CLASS).text(name))

	private fun selectedCount(): Int {
		val (prefix, suffix) = context.getString(R.string.transfer_selected_count).split("%d")
		val pattern = Pattern.compile(Pattern.quote(prefix) + "(\\d+)" + Pattern.quote(suffix))
		val text = retryOnStale {
			val label = device.wait(Until.findObject(By.text(pattern)), WAIT_MS)
			assertNotNull("Selected count is not shown", label)
			label.text
		}
		return pattern.matcher(text).run {
			assertTrue(matches())
			group(1)!!.toInt()
		}
	}

	private companion object {
		const val PACK_PREFIX = "zz_transfer_lifecycle_"
		const val PACK_COUNT = 3
		const val ROUNDS = 6
		const val WAIT_MS = 5_000L
		const val STALE_RETRIES = 3
		const val EDIT_TEXT_CLASS = "android.widget.EditText"
		const val TEXT_VIEW_CLASS = "android.widget.TextView"
		const val CHECK_BOX_CLASS = "android.widget.CheckBox"
	}
}
