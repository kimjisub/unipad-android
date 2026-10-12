package com.kimjisub.launchpad.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The buckets `play_start` reports for the play screen's window; no exact size may leave them. */
class ScreenLayoutTest {

	@Test
	fun shortSideBucketsFollowTheWindowSizeClassBounds() {
		val expected = listOf(
			1 to "lt_600dp", 599 to "lt_600dp", 600 to "600dp_839dp", 839 to "600dp_839dp",
			840 to "840dp_plus", 2_000 to "840dp_plus",
		)
		for ((dp, bucket) in expected) {
			assertEquals("short side $dp dp", bucket, ScreenShortSide.of(dp).value)
		}
	}

	@Test
	fun theShortSideIsTheSmallerOfWidthAndHeight() {
		assertEquals(ScreenShortSide.COMPACT, ScreenLayout.of(1_280, 599, false)!!.shortSide)
		assertEquals(ScreenShortSide.MEDIUM, ScreenLayout.of(600, 1_280, false)!!.shortSide)
		assertEquals(ScreenShortSide.MEDIUM, ScreenLayout.of(1_280, 839, false)!!.shortSide)
		assertEquals(ScreenShortSide.EXPANDED, ScreenLayout.of(840, 1_280, false)!!.shortSide)
	}

	@Test
	fun aWiderWindowIsLandscapeAndASquareOneIsPortraitAsOnAndroid() {
		assertEquals(ScreenOrientation.LANDSCAPE, ScreenLayout.of(801, 800, false)!!.orientation)
		assertEquals(ScreenOrientation.PORTRAIT, ScreenLayout.of(800, 800, false)!!.orientation)
		assertEquals(ScreenOrientation.PORTRAIT, ScreenLayout.of(800, 1_280, false)!!.orientation)
	}

	@Test
	fun multiWindowCoversSplitScreenAndFreeFormWindows() {
		assertEquals(WindowMode.FULL_SCREEN, ScreenLayout.of(1_280, 800, false)!!.window)
		assertEquals(WindowMode.MULTI_WINDOW, ScreenLayout.of(1_280, 800, true)!!.window)
	}

	@Test
	fun anUndefinedSizeReportsNoLayout() {
		assertNull(ScreenLayout.of(0, 800, false))
		assertNull(ScreenLayout.of(1_280, 0, true))
	}

	@Test
	fun onlyTheThreeBucketNamesAreSent() {
		assertEquals(
			mapOf(
				UsageParam.ORIENTATION to "portrait",
				UsageParam.SCREEN_SHORT_SIDE to "600dp_839dp",
				UsageParam.WINDOW_MODE to "multi_window",
			),
			ScreenLayout.of(700, 1_000, true)!!.parameters,
		)
	}
}
