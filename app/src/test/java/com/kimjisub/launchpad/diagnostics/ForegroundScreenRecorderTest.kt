package com.kimjisub.launchpad.diagnostics

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@SuppressLint("NewApi")
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ForegroundScreenRecorderTest {
	private open class Screen : Activity()
	private class PackScreen : Screen()
	private val saved = mutableMapOf<String, Any?>()
	private val preferences = mockk<SharedPreferences>()
	private val editor = mockk<SharedPreferences.Editor>()
	private val context = mockk<Context>()
	private var clock = 100L
	private val recorder = ForegroundScreenRecorder(context, PackScreen::class.java, pid = 7, processStartedAt = 50) { clock }

	@Before fun setUp() {
		every { context.getSharedPreferences(SystemExitReporter.PREFS, Context.MODE_PRIVATE) } returns preferences
		every { preferences.edit() } returns editor
		every { editor.putString(any(), any()) } answers { saved[firstArg()] = secondArg<String>(); editor }
		every { editor.putBoolean(any(), any()) } answers { saved[firstArg()] = secondArg<Boolean>(); editor }
		every { editor.putLong(any(), any()) } answers { saved[firstArg()] = secondArg<Long>(); editor }
		every { editor.putInt(any(), any()) } answers { saved[firstArg()] = secondArg<Int>(); editor }
		every { editor.remove(any()) } answers { saved.remove(firstArg<String>()); editor }
		every { editor.commit() } returns true
		every { preferences.contains(any()) } answers { saved.containsKey(firstArg<String>()) }
		every { preferences.getString(any(), any()) } answers { saved[firstArg()] as String? ?: secondArg() }
		every { preferences.getBoolean(any(), any()) } answers { saved[firstArg()] as Boolean? ?: secondArg() }
		every { preferences.getLong(any(), any()) } answers { saved[firstArg()] as Long? ?: secondArg() }
		every { preferences.getInt(any(), any()) } answers { saved[firstArg()] as Int? ?: secondArg() }
	}

	@Test fun startupReadsPreviousProcessBeforeReplacingItsRecordEvenWhenAScreenHasResumed() = runTest {
		saved.putAll(mapOf("foreground_screen" to "OldScreen", "foreground_pack_open" to true,
			"foreground_since" to 20L, "foreground_pid" to 6,
			"process_started_at" to 10L, "process_started_pid" to 6))
		var previous: ForegroundScreen? = null
		recorder.start(backgroundScope) { previous = ForegroundScreenRecorder.read(preferences) }
		val screen = mockk<Screen>()
		recorder.onActivityCreated(screen, null)
		recorder.onActivityResumed(screen)
		verify(exactly = 0) { editor.commit() }
		runCurrent()
		assertEquals(ForegroundScreen("OldScreen", true, 20, 6, 10), previous)
		val current = ForegroundScreenRecorder.read(preferences)!!
		assertTrue(current.screen, "Screen" in current.screen)
		assertEquals(ForegroundScreen(current.screen, false, 100, 7, 50), current)
	}

	@Test fun backgroundStartLeavesPreviousRecordAndCheckpointUntouchedUntilFirstScreen() = runTest {
		saved.putAll(mapOf("foreground_screen" to "OldScreen", "foreground_pack_open" to true,
			"foreground_since" to 20L, "foreground_pid" to 6,
			"process_started_at" to 10L, "process_started_pid" to 6,
			"last_processed_timestamp" to 30L))
		val before = saved.toMap()
		var collections = 0
		recorder.start(backgroundScope) { collections++ }
		runCurrent()
		assertEquals(0, collections)
		assertEquals(before, saved)
		verify(exactly = 0) { context.getSharedPreferences(any(), any()) }

		clock = 1000
		recorder.onActivityCreated(mockk<Screen>(), null)
		runCurrent()
		assertEquals(1, collections)
		assertEquals(50L, saved["process_started_at"])
		assertEquals(7, saved["process_started_pid"])
		assertEquals(30L, saved["last_processed_timestamp"])
		assertNull(ForegroundScreenRecorder.read(preferences))
	}

	@Test fun secondScreenAndRotationDoNotCollectAgain() = runTest {
		var collections = 0
		recorder.start(backgroundScope) { collections++ }
		val pack = mockk<PackScreen>()
		every { pack.isChangingConfigurations } returns true
		recorder.onActivityCreated(pack, null)
		recorder.onActivityResumed(pack)
		runCurrent()
		assertEquals(1, collections)
		recorder.onActivityPaused(pack)
		recorder.onActivityDestroyed(pack)
		val replacement = mockk<PackScreen>()
		recorder.onActivityCreated(replacement, mockk())
		recorder.onActivityResumed(replacement)
		runCurrent()
		recorder.onActivityCreated(mockk<Screen>(), null)
		runCurrent()
		assertEquals(1, collections)
	}

	@Test fun resumedScreenIsSavedOnceAndReturningStartsANewStay() = runTest {
		recorder.start(backgroundScope) {}
		runCurrent()
		val screen = mockk<Screen>()
		recorder.onActivityCreated(screen, null)
		recorder.onActivityResumed(screen)
		runCurrent()
		clock = 200
		recorder.onActivityResumed(screen)
		runCurrent()
		verify(exactly = 2) { editor.commit() } // Startup and one transition; nothing per frame or pad press.
		assertEquals(100L, ForegroundScreenRecorder.read(preferences)!!.since)
		recorder.onActivityPaused(screen)
		recorder.onActivityResumed(screen)
		runCurrent()
		assertEquals(200L, ForegroundScreenRecorder.read(preferences)!!.since)
	}

	@Test fun returningToListBeforePackDestructionClearsPackWithoutRestartingTheStay() = runTest {
		recorder.start(backgroundScope) {}
		val list = mockk<Screen>()
		val pack = mockk<PackScreen>()
		every { pack.isChangingConfigurations } returns false
		recorder.onActivityCreated(list, null)
		recorder.onActivityStarted(list)
		recorder.onActivityResumed(list)
		recorder.onActivityPaused(list)
		recorder.onActivityCreated(pack, null)
		recorder.onActivityStarted(pack)
		recorder.onActivityResumed(pack)
		recorder.onActivityStopped(list)
		runCurrent()

		// Back resumes the list before stopping and destroying the departing pack activity.
		recorder.onActivityPaused(pack)
		recorder.onActivityStarted(list)
		clock = 300
		recorder.onActivityResumed(list)
		recorder.onActivityStopped(pack)
		runCurrent()
		val beforeDestruction = ForegroundScreenRecorder.read(preferences)!!
		assertTrue(beforeDestruction.packOpen)
		clock = 400
		recorder.onActivityDestroyed(pack)
		runCurrent()
		assertEquals(beforeDestruction.copy(packOpen = false), ForegroundScreenRecorder.read(preferences))
	}

	@Test fun lastPackClosingAlsoCorrectsAListRecordStillWaitingForStorage() = runTest {
		recorder.start(backgroundScope) {}
		val pack = mockk<PackScreen>()
		every { pack.isChangingConfigurations } returns false
		recorder.onActivityCreated(pack, null)
		recorder.onActivityResumed(pack)
		recorder.onActivityPaused(pack)
		clock = 300
		recorder.onActivityResumed(mockk<Screen>())
		recorder.onActivityStopped(pack)
		clock = 400
		recorder.onActivityDestroyed(pack)
		runCurrent()
		val record = ForegroundScreenRecorder.read(preferences)!!
		assertFalse(record.packOpen)
		assertEquals(300L, record.since)
		verify(exactly = 2) { editor.commit() }
	}

	@Test fun closingOneOfTwoPacksKeepsTheRemainingPackOpen() = runTest {
		recorder.start(backgroundScope) {}
		val first = mockk<PackScreen>()
		val second = mockk<PackScreen>()
		every { first.isChangingConfigurations } returns false
		every { second.isChangingConfigurations } returns false
		recorder.onActivityCreated(first, null)
		recorder.onActivityCreated(second, null)
		recorder.onActivityResumed(mockk<Screen>())
		runCurrent()
		val record = ForegroundScreenRecorder.read(preferences)!!
		recorder.onActivityDestroyed(first)
		runCurrent()
		assertEquals(record, ForegroundScreenRecorder.read(preferences))
		verify(exactly = 2) { editor.commit() }
		recorder.onActivityDestroyed(second)
		runCurrent()
		assertEquals(record.copy(packOpen = false), ForegroundScreenRecorder.read(preferences))
	}

	@Test fun rebuildingAPackDoesNotRecordItAsClosed() = runTest {
		recorder.start(backgroundScope) {}
		val pack = mockk<PackScreen>()
		every { pack.isChangingConfigurations } returns true
		recorder.onActivityCreated(pack, null)
		recorder.onActivityResumed(pack)
		recorder.onActivityPaused(pack)
		runCurrent()
		val record = ForegroundScreenRecorder.read(preferences)!!
		recorder.onActivityDestroyed(pack)
		runCurrent()
		assertEquals(record, ForegroundScreenRecorder.read(preferences))
		verify(exactly = 2) { editor.commit() }
	}

	@Test fun waitingForStorageKeepsOnlyTheLatestTransitionAndLeavesBackgroundKeysAlone() = runTest {
		saved["background_screen"] = "PlayActivity"
		saved["background_since"] = 12L
		recorder.start(backgroundScope) {}
		val pack = mockk<PackScreen>()
		recorder.onActivityCreated(pack, null)
		recorder.onActivityResumed(pack)
		clock = 300
		recorder.onActivityResumed(mockk<Screen>())
		runCurrent()
		assertEquals(300L, ForegroundScreenRecorder.read(preferences)!!.since)
		verify(exactly = 2) { editor.commit() }
		assertEquals("PlayActivity", saved["background_screen"])
		assertEquals(12L, saved["background_since"])
	}
}
