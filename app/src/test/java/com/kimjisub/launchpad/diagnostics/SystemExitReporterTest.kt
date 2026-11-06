package com.kimjisub.launchpad.diagnostics

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.content.SharedPreferences
import com.google.firebase.crashlytics.CustomKeysAndValues
import com.google.firebase.crashlytics.FirebaseCrashlytics
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

// All framework calls are mocked; these JVM tests do not run on an Android OS.
@SuppressLint("NewApi")
class SystemExitReporterTest {
	private val context = mockk<Context>()
	private val manager = mockk<ActivityManager>()
	private val preferences = mockk<SharedPreferences>()
	private val editor = mockk<SharedPreferences.Editor>()
	private val crashlytics = mockk<FirebaseCrashlytics>(relaxed = true)
	private var checkpoint: Long? = null
	private var pendingCheckpoint = 0L
	private var history = emptyList<ApplicationExitInfo>()
	private var backgroundScreen: String? = null

	@Before fun setUp() {
		mockkStatic(ActivityManager::class, FirebaseCrashlytics::class)
		every { ActivityManager.isLowMemoryKillReportSupported() } returns true
		every { FirebaseCrashlytics.getInstance() } returns crashlytics
		every { context.packageName } returns "com.example.app"
		every { context.getSharedPreferences(any(), Context.MODE_PRIVATE) } returns preferences
		every { context.getSystemService(ActivityManager::class.java) } returns manager
		every { manager.getHistoricalProcessExitReasons("com.example.app", 0, 0) } answers { history }
		every { preferences.contains("last_processed_timestamp") } answers { checkpoint != null }
		every { preferences.getLong("last_processed_timestamp", 0) } answers { checkpoint ?: 0 }
		every { preferences.getString("background_screen", null) } answers { backgroundScreen }
		every { preferences.getBoolean("background_pack_open", false) } returns true
		every { preferences.getLong("background_since", 0) } returns 12
		every { preferences.getInt("background_pid", 0) } returns EXIT_PID
		every { preferences.edit() } returns editor
		every { editor.putLong("last_processed_timestamp", any()) } answers {
			pendingCheckpoint = secondArg()
			editor
		}
		every { editor.commit() } answers { checkpoint = pendingCheckpoint; true }
	}

	@After fun tearDown() {
		unmockkStatic(ActivityManager::class, FirebaseCrashlytics::class)
	}

	@Test fun firstCollectionPersistsBaselineWithoutReportingOldExits() {
		history = listOf(exit(3, 20))
		val before = System.currentTimeMillis()
		SystemExitReporter.collect(context)
		assertTrue(checkpoint!! >= before)
		verify(exactly = 0) { crashlytics.recordException(any<Throwable>(), any<CustomKeysAndValues>()) }
	}

	@Test fun reopeningDoesNotReportTheSameSystemExitTwice() {
		checkpoint = 10
		history = listOf(exit(3, 11), exit(4, 12))
		SystemExitReporter.collect(context)
		SystemExitReporter.collect(context)
		assertEquals(12L, checkpoint)
		verify(exactly = 1) { crashlytics.recordException(any<Throwable>(), any<CustomKeysAndValues>()) }
		verify(exactly = 0) { history[0].traceInputStream }
	}

	@Test fun eachCauseUsesADistinctExceptionTypeAndAtMostFiveAreRecorded() {
		checkpoint = 10
		history = listOf(exit(3, 11), exit(7, 12), exit(9, 13), exit(14, 14),
			exit(13, 15, "MemoryLimiter:AnonSwap"), exit(3, 16))
		val exceptionTypes = mutableListOf<String>()
		every { crashlytics.recordException(any<Throwable>(), any<CustomKeysAndValues>()) } answers {
			exceptionTypes += firstArg<Throwable>().javaClass.simpleName
		}
		SystemExitReporter.collect(context)
		assertEquals(5, exceptionTypes.size)
		assertEquals(5, exceptionTypes.toSet().size)
		assertEquals(16L, checkpoint)
	}

	@Test fun anrIsReportedInItsOwnGroupWithTheSystemReason() {
		checkpoint = 10
		backgroundScreen = "PlayActivity"
		val reason = "bg anr: Broadcast of Intent { act=com.google.android.c2dm.intent.RECEIVE " +
			"dat=content://x/" + "y".repeat(2000) + " }"
		history = listOf(exit(6, 20, reason, importance = 400), exit(3, 21))
		val errors = mutableListOf<Throwable>()
		val keys = mutableListOf<CustomKeysAndValues>()
		every { crashlytics.recordException(capture(errors), capture(keys)) } returns Unit
		SystemExitReporter.collect(context)
		assertEquals(listOf("LowMemoryExit", "AnrReasonExit"), errors.map { it.javaClass.simpleName })
		val values = keys[1].values()
		assertEquals("6", values["exit_reason"])
		assertEquals("bg anr: Broadcast of Intent { act=com.google.android.c2dm.intent.RECEIVE <redacted> }",
			values["exit_description"])
		assertEquals("400", values["exit_importance"])
		assertEquals("PlayActivity", values["exit_last_screen"])
		assertEquals("8", values["exit_background_ms"])
		assertEquals(21L, checkpoint)
		verify(exactly = 0) { history[0].traceInputStream }
	}

	@Test fun backgroundExitCarriesTheScreenTheAppLeft() {
		checkpoint = 10
		backgroundScreen = "PlayActivity"
		history = listOf(exit(14, 20, importance = 400))
		val keys = slot<CustomKeysAndValues>()
		every { crashlytics.recordException(any<Throwable>(), capture(keys)) } returns Unit
		SystemExitReporter.collect(context)
		val values = keys.captured.values()
		assertEquals("PlayActivity", values["exit_last_screen"])
		assertEquals("true", values["exit_pack_open"])
		assertEquals("8", values["exit_background_ms"])
	}

	@Test fun perceptibleLowMemoryExitIsReportedWithTheScreenTheAppLeft() {
		checkpoint = 10
		backgroundScreen = "PlayActivity"
		history = listOf(exit(3, 20, importance = 230))
		val error = slot<Throwable>()
		val keys = slot<CustomKeysAndValues>()
		every { crashlytics.recordException(capture(error), capture(keys)) } returns Unit
		SystemExitReporter.collect(context)
		assertEquals("LowMemoryExit", error.captured.javaClass.simpleName)
		val values = keys.captured.values()
		assertEquals("PlayActivity", values["exit_last_screen"])
		assertEquals("true", values["exit_pack_open"])
		assertEquals("8", values["exit_background_ms"])
	}

	@Test fun exitInFrontCarriesNoLeftScreen() {
		checkpoint = 10
		backgroundScreen = "PlayActivity"
		history = listOf(exit(14, 20))
		val keys = slot<CustomKeysAndValues>()
		every { crashlytics.recordException(any<Throwable>(), capture(keys)) } returns Unit
		SystemExitReporter.collect(context)
		assertFalse("exit_last_screen" in keys.captured.values())
	}

	@Test fun unavailableHistoryLeavesCheckpointForTheNextLaunch() {
		checkpoint = 10
		every { manager.getHistoricalProcessExitReasons(any(), any(), any()) } throws SecurityException()
		assertThrows(SecurityException::class.java) { SystemExitReporter.collect(context) }
		assertEquals(10L, checkpoint)
		verify(exactly = 0) { editor.commit() }
	}

	@Suppress("UNCHECKED_CAST")
	private fun CustomKeysAndValues.values() = CustomKeysAndValues::class.java
		.getDeclaredField("keysAndValues").apply { isAccessible = true }.get(this) as Map<String, String>

	private fun exit(reason: Int, timestamp: Long, description: String? = null, importance: Int = 100): ApplicationExitInfo {
		val info = mockk<ApplicationExitInfo>()
		every { info.reason } returns reason
		every { info.status } returns 0
		every { info.description } returns description
		every { info.importance } returns importance
		every { info.timestamp } returns timestamp
		every { info.pss } returns 4096
		every { info.rss } returns 8192
		every { info.pid } returns EXIT_PID
		return info
	}

	private companion object {
		const val EXIT_PID = 7
	}
}
