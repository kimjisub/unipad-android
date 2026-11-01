package com.kimjisub.launchpad.diagnostics

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@SuppressLint("NewApi")
class BackgroundScreenRecorderTest {
	private open class Screen : Activity()
	private class PackScreen : Screen()

	private val saved = mutableMapOf<String, Any?>()
	private val preferences = mockk<SharedPreferences>()
	private val editor = mockk<SharedPreferences.Editor>()
	private val context = mockk<Context>()
	private var clock = 100L
	private val recorder = BackgroundScreenRecorder(context, PackScreen::class.java, pid = 7) { clock }

	@Before fun setUp() {
		every { context.getSharedPreferences(SystemExitReporter.PREFS, Context.MODE_PRIVATE) } returns preferences
		every { preferences.edit() } returns editor
		every { editor.putString(any(), any()) } answers { saved[firstArg()] = secondArg<String>(); editor }
		every { editor.putBoolean(any(), any()) } answers { saved[firstArg()] = secondArg<Boolean>(); editor }
		every { editor.putLong(any(), any()) } answers { saved[firstArg()] = secondArg<Long>(); editor }
		every { editor.putInt(any(), any()) } answers { saved[firstArg()] = secondArg<Int>(); editor }
		every { editor.apply() } returns Unit
		every { preferences.getString(any(), any()) } answers { saved[firstArg()] as String? ?: secondArg() }
		every { preferences.getBoolean(any(), any()) } answers { saved[firstArg()] as Boolean? ?: secondArg() }
		every { preferences.getLong(any(), any()) } answers { saved[firstArg()] as Long? ?: secondArg() }
		every { preferences.getInt(any(), any()) } answers { saved[firstArg()] as Int? ?: secondArg() }
	}

	private fun screen() = mockk<Screen>().also { every { it.isChangingConfigurations } returns false }
	private fun packScreen() = mockk<PackScreen>().also { every { it.isChangingConfigurations } returns false }

	@Test fun nothingIsRecordedBeforeTheAppLeaves() {
		assertNull(BackgroundScreenRecorder.read(preferences))
	}

	@Test fun leavingRecordsTheScreenAndTime() {
		val screen = screen()
		recorder.onActivityCreated(screen, null)
		recorder.onActivityStarted(screen)
		clock = 250
		recorder.onActivityStopped(screen)

		val record = BackgroundScreenRecorder.read(preferences)!!
		assertTrue(record.screen, "Screen" in record.screen)
		assertFalse(record.packOpen)
		assertEquals(250L, record.since)
		assertEquals(7, record.pid)
	}

	@Test fun aPackScreenStillAliveBehindAnotherCounts() {
		val pack = packScreen()
		val settings = screen()
		recorder.onActivityCreated(pack, null)
		recorder.onActivityStarted(pack)
		recorder.onActivityCreated(settings, null)
		recorder.onActivityStarted(settings)
		recorder.onActivityStopped(pack)
		recorder.onActivityStopped(settings)

		assertTrue(BackgroundScreenRecorder.read(preferences)!!.packOpen)
	}

	@Test fun aClosedPackScreenDoesNotCount() {
		val pack = packScreen()
		val list = screen()
		recorder.onActivityCreated(pack, null)
		recorder.onActivityStarted(pack)
		recorder.onActivityCreated(list, null)
		recorder.onActivityStarted(list)
		recorder.onActivityStopped(pack)
		recorder.onActivityDestroyed(pack)
		recorder.onActivityStopped(list)

		assertFalse(BackgroundScreenRecorder.read(preferences)!!.packOpen)
	}

	@Test fun aPackScreenClosedAfterTheAppLeftStopsCounting() {
		val pack = packScreen()
		recorder.onActivityCreated(pack, null)
		recorder.onActivityStarted(pack)
		clock = 250
		recorder.onActivityStopped(pack)
		assertTrue(BackgroundScreenRecorder.read(preferences)!!.packOpen)

		clock = 900
		recorder.onActivityDestroyed(pack)

		val record = BackgroundScreenRecorder.read(preferences)!!
		assertFalse(record.packOpen)
		assertEquals("the app left at the same moment", 250L, record.since)
	}

	@Test fun aPackScreenRebuildingAfterTheAppLeftStillCounts() {
		val pack = packScreen()
		recorder.onActivityCreated(pack, null)
		recorder.onActivityStarted(pack)
		recorder.onActivityStopped(pack)

		every { pack.isChangingConfigurations } returns true
		recorder.onActivityDestroyed(pack)

		assertTrue(BackgroundScreenRecorder.read(preferences)!!.packOpen)
	}

	@Test fun movingBetweenScreensAndRotatingRecordNothing() {
		val first = screen()
		val second = screen()
		recorder.onActivityStarted(first)
		recorder.onActivityStarted(second)
		recorder.onActivityStopped(first)
		assertNull(BackgroundScreenRecorder.read(preferences))

		every { second.isChangingConfigurations } returns true
		recorder.onActivityStopped(second)
		assertNull(BackgroundScreenRecorder.read(preferences))
	}
}
