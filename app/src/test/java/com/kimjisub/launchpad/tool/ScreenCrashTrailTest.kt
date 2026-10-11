package com.kimjisub.launchpad.tool

import android.app.Activity
import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenCrashTrailTest {

	private class RecordedCrashContext : CrashContext {
		val keys = mutableMapOf<String, String>()
		val logs = mutableListOf<String>()
		override fun setKey(key: String, value: String) { keys[key] = value }
		override fun log(message: String) { logs += message }
	}

	private class ListScreen : Activity()
	private class PlayScreen : Activity()

	@Test
	fun theScreenInFrontIsTheLastOneResumed() {
		val context = RecordedCrashContext()
		val trail = ScreenCrashTrail(context)
		val list = ListScreen()
		val play = PlayScreen()

		trail.onActivityResumed(list)
		trail.onActivityPaused(list)
		trail.onActivityResumed(play)

		assertEquals("PlayScreen", context.keys[ScreenCrashTrail.KEY_SCREEN])
	}

	@Test
	fun leavingAScreenIsKeptInTheTrail() {
		val context = RecordedCrashContext()
		val trail = ScreenCrashTrail(context)
		val list = ListScreen()
		val play = PlayScreen()

		trail.onActivityResumed(list)
		trail.onActivityPaused(list)
		trail.onActivityResumed(play)
		trail.onActivityPaused(play)
		trail.onActivityDestroyed(play)

		assertEquals(
			listOf("ListScreen resumed", "ListScreen paused", "PlayScreen resumed", "PlayScreen paused", "PlayScreen destroyed"),
			context.logs,
		)
		assertEquals("PlayScreen", context.keys[ScreenCrashTrail.KEY_SCREEN])
	}
}
