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

	@Test fun crashesAnrAndRoutineExitsAreExcluded() {
		for (reason in listOf(0, 1, 4, 5, 6, 8, 10, 11, 12, 15, 16, 99)) {
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
}
