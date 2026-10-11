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
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
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

    @Test fun unavailableHistoryLeavesCheckpointForTheNextLaunch() {
        checkpoint = 10
        every { manager.getHistoricalProcessExitReasons(any(), any(), any()) } throws SecurityException()
        assertThrows(SecurityException::class.java) { SystemExitReporter.collect(context) }
        assertEquals(10L, checkpoint)
        verify(exactly = 0) { editor.commit() }
    }

    private fun exit(reason: Int, timestamp: Long, description: String? = null): ApplicationExitInfo {
        val info = mockk<ApplicationExitInfo>()
        every { info.reason } returns reason
        every { info.status } returns 0
        every { info.description } returns description
        every { info.importance } returns 100
        every { info.timestamp } returns timestamp
        every { info.pss } returns 4096
        every { info.rss } returns 8192
        return info
    }
}
