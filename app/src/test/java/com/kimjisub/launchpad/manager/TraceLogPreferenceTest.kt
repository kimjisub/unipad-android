package com.kimjisub.launchpad.manager

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TraceLogPreferenceTest {
	private val context = mockk<Context>()
	private val preferences = mockk<SharedPreferences>()

	init {
		every { context.getSharedPreferences("data", Context.MODE_PRIVATE) } returns preferences
	}

	@Test
	fun missingChoiceDefaultsToNumbersWithoutWriting() {
		every { preferences.getBoolean("TraceLogClassic", any()) } answers { secondArg() }
		assertTrue(PreferenceManager(context).traceLogClassic)
		verify(exactly = 0) { preferences.edit() }
	}

	@Test
	fun bothStoredChoicesSurviveReadingAgain() {
		for (stored in listOf(false, true)) {
			every { preferences.getBoolean("TraceLogClassic", any()) } returns stored
			repeat(2) { assertEquals(stored, PreferenceManager(context).traceLogClassic) }
		}
		verify(exactly = 0) { preferences.edit() }
	}
}
