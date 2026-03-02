package com.kimjisub.launchpad.unipack.runner

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ChainObserverTest {

	private lateinit var observer: ChainObserver

	@Before
	fun setUp() {
		observer = ChainObserver()
	}

	// --- basic value setting ---

	@Test
	fun defaultValue_isZero() {
		assertEquals(0, observer.value)
	}

	@Test
	fun setValue_updatesValue() {
		observer.value = 5
		assertEquals(5, observer.value)
	}

	// --- range clamping ---

	@Test
	fun setValue_withinRange_setsExactValue() {
		observer.range = 0..7
		observer.value = 3
		assertEquals(3, observer.value)
	}

	@Test
	fun setValue_belowRange_clampsToFirst() {
		observer.range = 0..7
		observer.value = -5
		assertEquals(0, observer.value)
	}

	@Test
	fun setValue_aboveRange_clampsToLast() {
		observer.range = 0..7
		observer.value = 20
		assertEquals(7, observer.value)
	}

	@Test
	fun setValue_atRangeBoundary_setsExact() {
		observer.range = 3..10
		observer.value = 3
		assertEquals(3, observer.value)
		observer.value = 10
		assertEquals(10, observer.value)
	}

	// --- observer notification ---

	@Test
	fun observer_notifiedOnValueChange() {
		val notifications = mutableListOf<Pair<Int, Int>>()
		observer.addObserver { curr, prev -> notifications.add(curr to prev) }

		observer.value = 5
		assertEquals(1, notifications.size)
		assertEquals(5 to 0, notifications[0])
	}

	@Test
	fun observer_notifiedWithClampedValue() {
		observer.range = 0..3
		val notifications = mutableListOf<Pair<Int, Int>>()
		observer.addObserver { curr, prev -> notifications.add(curr to prev) }

		observer.value = 10
		assertEquals(1, notifications.size)
		assertEquals(3 to 0, notifications[0])
	}

	@Test
	fun multipleObservers_allNotified() {
		val notifications1 = mutableListOf<Int>()
		val notifications2 = mutableListOf<Int>()

		observer.addObserver { curr, _ -> notifications1.add(curr) }
		observer.addObserver { curr, _ -> notifications2.add(curr) }

		observer.value = 7
		assertEquals(listOf(7), notifications1)
		assertEquals(listOf(7), notifications2)
	}

	// --- removeObserver ---

	@Test
	fun removeObserver_stopsNotification() {
		val notifications = mutableListOf<Int>()
		val obs: (Int, Int) -> Unit = { curr, _ -> notifications.add(curr) }

		observer.addObserver(obs)
		observer.value = 1
		assertEquals(1, notifications.size)

		observer.removeObserver(obs)
		observer.value = 2
		assertEquals(1, notifications.size) // no new notification
	}

	// --- clearObserver ---

	@Test
	fun clearObserver_removesAllObservers() {
		val notifications = mutableListOf<Int>()
		observer.addObserver { curr, _ -> notifications.add(curr) }
		observer.addObserver { curr, _ -> notifications.add(curr * 10) }

		observer.value = 1
		assertEquals(2, notifications.size)

		observer.clearObserver()
		observer.value = 2
		assertEquals(2, notifications.size) // no new notifications
	}

	// --- refresh ---

	@Test
	fun refresh_notifiesWithCurrentValue() {
		observer.value = 5
		val notifications = mutableListOf<Pair<Int, Int>>()
		observer.addObserver { curr, prev -> notifications.add(curr to prev) }

		observer.refresh()
		assertEquals(1, notifications.size)
		assertEquals(5 to 5, notifications[0])
	}

	@Test
	fun refresh_withCustomValues() {
		val notifications = mutableListOf<Pair<Int, Int>>()
		observer.addObserver { curr, prev -> notifications.add(curr to prev) }

		observer.refresh(curr = 3, prev = 1)
		assertEquals(1, notifications.size)
		assertEquals(3 to 1, notifications[0])
	}

	// --- sequential value changes ---

	@Test
	fun sequentialChanges_trackPreviousValue() {
		val notifications = mutableListOf<Pair<Int, Int>>()
		observer.addObserver { curr, prev -> notifications.add(curr to prev) }

		observer.value = 1
		observer.value = 3
		observer.value = 7

		assertEquals(3, notifications.size)
		assertEquals(1 to 0, notifications[0])
		assertEquals(3 to 1, notifications[1])
		assertEquals(7 to 3, notifications[2])
	}
}
