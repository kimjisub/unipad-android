package com.kimjisub.launchpad.diagnostics

import org.junit.Assert.*
import org.junit.Test

class SystemExitPolicyTest {
	@Test fun lowMemoryOnlyWhenPerceptible() {
		for (importance in listOf(100, 125, 200, 230)) {
			assertTrue(SystemExitPolicy.shouldReport(SystemExit(3, importance = importance), true))
		}
		for (importance in listOf(231, 300, 400)) {
			assertFalse(SystemExitPolicy.shouldReport(SystemExit(3, importance = importance), true))
		}
	}

	@Test fun killSignalFallbackOnlyWithoutLowMemoryReporting() {
		assertTrue(SystemExitPolicy.shouldReport(SystemExit(2, status = 9, importance = 230), false))
		assertFalse(SystemExitPolicy.shouldReport(SystemExit(2, status = 9), true))
		assertFalse(SystemExitPolicy.shouldReport(SystemExit(2, status = 9, importance = 231), false))
		assertFalse(SystemExitPolicy.shouldReport(SystemExit(2, status = 15), false))
	}

	@Test fun memoryLimiterReportsEvenCachedProcesses() {
		assertTrue(SystemExitPolicy.shouldReport(SystemExit(13, description = "MemoryLimiter:AnonSwap", importance = 400), true))
		assertFalse(SystemExitPolicy.shouldReport(SystemExit(13, description = "remove task"), true))
		assertFalse(SystemExitPolicy.shouldReport(SystemExit(13), true))
	}

	@Test fun systemFaultsReportRegardlessOfImportance() {
		for (reason in listOf(7, 9, 14)) {
			assertTrue(SystemExitPolicy.shouldReport(SystemExit(reason, importance = 400), true))
		}
	}

	@Test fun anrIsReportedInFrontAndInTheBackground() {
		for (importance in listOf(100, 125, 230, 400)) {
			assertTrue(SystemExitPolicy.shouldReport(SystemExit(6, importance = importance), true))
			assertTrue(SystemExitPolicy.shouldReport(SystemExit(6, importance = importance), false))
		}
	}

	@Test fun descriptionKeepsTheSystemReasonWithinTheKeyLimit() {
		val reason = "user request after error: Input dispatching timed out (8a1b2c3 com.kimjisub.launchpad.dev/" +
			"com.kimjisub.launchpad.activity.MainActivity (server) is not responding. Waited 5004ms for MotionEvent(" +
			"deviceId=-1, source=TOUCHSCREEN, action=DOWN, pointers=[0: (80.0, 1800.0)]), policyFlags=0x6b000000)."
		assertEquals(reason, SystemExitPolicy.description(SystemExit(6, description = reason)))
		assertEquals("bg anr", SystemExitPolicy.description(SystemExit(6, description = "bg anr")))
		assertEquals("", SystemExitPolicy.description(SystemExit(6)))
		assertEquals("a".repeat(1024), SystemExitPolicy.description(SystemExit(6, description = "a".repeat(5000))))
	}

	@Test fun intentDataAndExtrasAreDroppedKeepingActionAndComponent() {
		fun reason(text: String) = SystemExitPolicy.description(SystemExit(6, description = text))
		assertEquals("bg anr: Broadcast of Intent { act=android.intent.action.VIEW cmp=com.kimjisub.launchpad/.Receiver <redacted> }",
			reason("bg anr: Broadcast of Intent { act=android.intent.action.VIEW dat=content://downloads/my%20pack.zip " +
				"flg=0x10 cmp=com.kimjisub.launchpad/.Receiver (has extras) }"))
		// Names that look like other fields, a line break or a colon in the data are dropped with the rest.
		assertEquals("bg anr: Broadcast of Intent { <redacted> }",
			reason("bg anr: Broadcast of Intent { dat=content://x/my pack v=2.zip flg=0x10 }"))
		assertEquals("Broadcast of Intent { <redacted> }", reason("Broadcast of Intent { dat=/sdcard/a\nb.zip }"))
		assertEquals("Broadcast of Intent { <redacted> }", reason("Broadcast of Intent { dat=MyPack:final.zip }"))
		assertEquals("ends dat=<redacted>", reason("ends dat=file:///sdcard/my pack.zip"))
		val service = "bg anr: executing service com.kimjisub.launchpad/.MessagingService"
		assertEquals(service, reason(service))
	}

	@Test fun anrsAndOtherExitsEachKeepTheirOwnAllowance() {
		val anrs = (1..7L).map { SystemExit(6, timestamp = it) }
		val memory = (11..17L).map { SystemExit(3, timestamp = it) }
		val batch = SystemExitPolicy.select(anrs + memory, 0, 100, true)
		assertEquals(listOf(17L, 16, 15, 14, 13, 7, 6, 5, 4, 3), batch.reports.map { it.timestamp })
		assertEquals(17L, batch.checkpoint)
	}

	@Test fun crashesAndRoutineExitsAreExcluded() {
		for (reason in listOf(0, 1, 4, 5, 8, 10, 11, 12, 15, 16, 99)) {
			assertFalse("reason $reason", SystemExitPolicy.shouldReport(SystemExit(reason), false))
		}
	}

	@Test fun firstLaunchOnlyEstablishesBaseline() {
		val result = SystemExitPolicy.select(listOf(SystemExit(3, timestamp = 20)), null, 100, true)
		assertTrue(result.reports.isEmpty())
		assertEquals(100L, result.checkpoint)
		assertEquals(100L, SystemExitPolicy.select(emptyList(), null, 100, true).checkpoint)
	}

	@Test fun newerRecordsOnlyAndIgnoredRecordsAdvanceCheckpoint() {
		val history = listOf(SystemExit(3, timestamp = 9), SystemExit(3, timestamp = 10),
			SystemExit(3, timestamp = 11), SystemExit(4, timestamp = 12))
		val batch = SystemExitPolicy.select(history, 10, 100, true)
		assertEquals(listOf(11L), batch.reports.map { it.timestamp })
		assertEquals(12L, batch.checkpoint)
		assertTrue(SystemExitPolicy.select(history, batch.checkpoint, 100, true).reports.isEmpty())
	}

	@Test fun selectsNewestFiveRegardlessOfHistoryOrder() {
		val history = (1L..9L).map { SystemExit(3, timestamp = it) }.reversed()
		val batch = SystemExitPolicy.select(history, 0, 100, true)
		assertEquals(listOf(9L, 8L, 7L, 6L, 5L), batch.reports.map { it.timestamp })
		assertEquals(9L, batch.checkpoint)
	}

	@Test fun emptyHistoryDoesNotMoveExistingCheckpoint() {
		assertEquals(10L, SystemExitPolicy.select(emptyList(), 10, 100, true).checkpoint)
	}

	@Test fun leftScreenAppliesOnlyToABackgroundExitAfterLeaving() {
		val record = BackgroundScreen("PlayActivity", true, since = 50, pid = 7)
		fun at(timestamp: Long, importance: Int) =
			SystemExitPolicy.backgroundScreenAt(SystemExit(14, timestamp = timestamp, importance = importance, pid = 7), record)

		assertEquals(record, at(50, 400))
		assertEquals(record, at(60, 300))
		assertEquals("a perceptible app has left the screen", record, at(60, 230))
		assertNull("the app was killed before this record was made", at(49, 400))
		assertNull("the app came back and was killed in front", at(60, 100))
		assertNull("the app came back and was killed while visible", at(60, 200))
		assertNull(SystemExitPolicy.backgroundScreenAt(SystemExit(14, timestamp = 60, importance = 400, pid = 7), null))
	}

	@Test fun leftScreenDoesNotDescribeAnotherProcess() {
		val record = BackgroundScreen("PlayActivity", true, since = 50, pid = 7)
		val serviceProcess = SystemExit(14, timestamp = 60, importance = 400, pid = 8)

		assertNull(SystemExitPolicy.backgroundScreenAt(serviceProcess, record))
	}
}
