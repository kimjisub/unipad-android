package com.kimjisub.launchpad.tool

import android.content.ActivityNotFoundException
import androidx.activity.result.ActivityResultLauncher
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A picker launch reports a device without a picker app instead of crashing. */
class ActivityResultLaunchersTest {

	private val launcher = mockk<ActivityResultLauncher<String>>()

	@Test
	fun launchesWhenAnAppHandlesIt() {
		every { launcher.launch("application/zip") } just runs

		assertTrue(launcher.tryLaunch("application/zip"))
		verify { launcher.launch("application/zip") }
	}

	@Test
	fun reportsFalseWhenNoAppHandlesIt() {
		every { launcher.launch(any()) } throws ActivityNotFoundException("No Activity found to handle Intent")

		assertFalse(launcher.tryLaunch("application/zip"))
	}
}
