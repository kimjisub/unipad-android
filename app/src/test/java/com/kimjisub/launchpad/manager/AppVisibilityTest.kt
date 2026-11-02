package com.kimjisub.launchpad.manager

import android.app.Activity
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVisibilityTest {
	private val visibility = AppVisibility()
	private fun screen(changingConfigurations: Boolean = false) = mockk<Activity>().also {
		every { it.isChangingConfigurations } returns changingConfigurations
	}

	@Test fun coveringOneScreenWithAnotherKeepsTheAppVisible() {
		val play = screen()
		val list = screen()
		visibility.onActivityStarted(play)
		visibility.onActivityStarted(list)
		visibility.onActivityStopped(play)
		assertTrue(visibility.visible.value)
		visibility.onActivityStopped(list)
		assertFalse(visibility.visible.value)
	}

	@Test fun returningToTheListMakesTheAppVisibleWithoutRestartingPlay() {
		val play = screen()
		visibility.onActivityStarted(play)
		visibility.onActivityStopped(play)
		assertFalse(visibility.visible.value)
		visibility.onActivityStarted(screen())
		assertTrue(visibility.visible.value)
	}

	@Test fun rotationDoesNotHideTheAppAndTheReplacementCanLeave() {
		val old = screen(changingConfigurations = true)
		val replacement = screen()
		visibility.onActivityStarted(old)
		visibility.onActivityStopped(old)
		assertTrue(visibility.visible.value)
		visibility.onActivityStarted(replacement)
		visibility.onActivityStopped(replacement)
		assertFalse(visibility.visible.value)
	}
}
