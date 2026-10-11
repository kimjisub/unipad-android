package com.kimjisub.launchpad.manager

import com.kimjisub.launchpad.manager.AppUpdateSchedule.Companion.CHECK_INTERVAL_MS
import com.kimjisub.launchpad.manager.AppUpdateSchedule.Companion.DECLINE_REST_MS
import com.kimjisub.launchpad.manager.AppUpdateSchedule.Companion.INSTALL_REST_MS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateScheduleTest {
	private val store = MemoryAppUpdateStore()
	private var now = START
	private fun schedule() = AppUpdateSchedule(store) { now }

	@Test
	fun firstRunMayCheckAndOffer() {
		val schedule = schedule()
		assertTrue(schedule.isAutoCheckDue())
		assertTrue(schedule.mayOfferDownload())
		assertTrue(schedule.mayOfferInstall())
	}

	@Test
	fun autoCheckWaitsExactly24Hours() {
		schedule().markAutoCheck()
		now = START + CHECK_INTERVAL_MS - 1
		assertFalse(schedule().isAutoCheckDue())
		now = START + CHECK_INTERVAL_MS
		assertTrue(schedule().isAutoCheckDue())
	}

	@Test
	fun declineRestsExactly7DaysAcrossRestarts() {
		schedule().markDeclined()
		assertEquals(7 * 24 * 60 * 60 * 1000L, DECLINE_REST_MS)
		// A new schedule over the same store is the app started again.
		now = START + DECLINE_REST_MS - 1
		assertFalse(schedule().mayOfferDownload())
		now = START + DECLINE_REST_MS
		assertTrue(schedule().mayOfferDownload())
	}

	@Test
	fun postponedInstallRestsExactly24Hours() {
		schedule().markInstallPostponed()
		now = START + INSTALL_REST_MS - 1
		assertFalse(schedule().mayOfferInstall())
		assertTrue("postponing the install does not stop new-version offers", schedule().mayOfferDownload())
		now = START + INSTALL_REST_MS
		assertTrue(schedule().mayOfferInstall())
	}

	@Test
	fun clockMovedBackRestartsTheRestFromThen() {
		schedule().markDeclined()
		val back = START - 30L * 24 * 60 * 60 * 1000
		now = back
		assertFalse(schedule().mayOfferDownload())
		assertEquals(back, store.declinedAt)
		now = back + DECLINE_REST_MS - 1
		assertFalse(schedule().mayOfferDownload())
		now = back + DECLINE_REST_MS
		assertTrue(schedule().mayOfferDownload())
	}

	@Test
	fun clockMovedBackDoesNotRunChecksEarly() {
		schedule().markAutoCheck()
		now = START - 1
		assertFalse(schedule().isAutoCheckDue())
		now = START - 1 + CHECK_INTERVAL_MS
		assertTrue(schedule().isAutoCheckDue())
	}

	private companion object {
		const val START = 1_790_000_000_000L
	}
}

class MemoryAppUpdateStore : AppUpdateSchedule.Store {
	override var lastAutoCheckAt: Long? = null
	override var declinedAt: Long? = null
	override var installPostponedAt: Long? = null
	override var downloadRequested: Boolean = false
}
