package com.kimjisub.launchpad.diagnostics

import com.kimjisub.launchpad.analytics.ScreenLayout
import com.kimjisub.launchpad.tool.CrashContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayCrashKeysTest {

	private class RecordedCrashContext : CrashContext {
		val keys = mutableMapOf<String, String>()
		val writes = mutableListOf<String>()
		override fun setKey(key: String, value: String) {
			keys[key] = value
			writes += key
		}
		override fun log(message: String) = Unit
	}

	private val context = RecordedCrashContext()
	private val crashKeys = PlayCrashKeys(context)
	private val screen = Any()
	private val nextScreen = Any()

	private fun keys(orientation: String, shortSide: String, window: String) = mapOf(
		"play_orientation" to orientation,
		"play_screen_short_side" to shortSide,
		"play_window_mode" to window,
	)

	private val noValues = keys("", "", "")

	@Test
	fun theKeysFollowTheWindow() {
		crashKeys.screenLayoutChanged(screen, ScreenLayout.of(1_280, 800, false))
		assertEquals(keys("landscape", "600dp_839dp", "full_screen"), context.keys)

		crashKeys.screenLayoutChanged(screen, ScreenLayout.of(500, 900, true))
		assertEquals(keys("portrait", "lt_600dp", "multi_window"), context.keys)
	}

	@Test
	fun theSameWindowWritesNothingAgain() {
		crashKeys.screenLayoutChanged(screen, ScreenLayout.of(1_280, 800, false))
		context.writes.clear()

		crashKeys.screenLayoutChanged(screen, ScreenLayout.of(1_279, 801, false))

		assertTrue(context.writes.isEmpty())
	}

	@Test
	fun nothingIsWrittenWhileTheWindowIsUnknown() {
		crashKeys.screenLayoutChanged(screen, null)
		crashKeys.screenClosed(screen)

		assertTrue(context.writes.isEmpty())
	}

	@Test
	fun aMomentOfUndefinedSizeKeepsTheWindowKnownBefore() {
		crashKeys.screenLayoutChanged(screen, ScreenLayout.of(1_280, 800, false))
		crashKeys.screenLayoutChanged(screen, ScreenLayout.of(0, 0, false))

		assertEquals(keys("landscape", "600dp_839dp", "full_screen"), context.keys)
	}

	@Test
	fun closingTheScreenEmptiesTheKeys() {
		crashKeys.screenLayoutChanged(screen, ScreenLayout.of(1_280, 800, false))
		crashKeys.screenClosed(screen)

		assertEquals(noValues, context.keys)
	}

	@Test
	fun theSameWindowInALaterVisitIsWrittenAgain() {
		crashKeys.screenLayoutChanged(screen, ScreenLayout.of(1_280, 800, false))
		crashKeys.screenClosed(screen)
		crashKeys.screenLayoutChanged(nextScreen, ScreenLayout.of(1_280, 800, false))

		assertEquals(keys("landscape", "600dp_839dp", "full_screen"), context.keys)
	}

	@Test
	fun aScreenClearedUpLateLeavesTheKeysOfTheOneThatReplacedIt() {
		crashKeys.screenLayoutChanged(screen, ScreenLayout.of(1_280, 800, false))
		crashKeys.screenLayoutChanged(nextScreen, ScreenLayout.of(1_280, 800, false))

		crashKeys.screenClosed(screen)
		assertEquals(keys("landscape", "600dp_839dp", "full_screen"), context.keys)

		crashKeys.screenClosed(nextScreen)
		assertEquals(noValues, context.keys)
	}
}
