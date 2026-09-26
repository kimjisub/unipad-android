package com.kimjisub.launchpad.unipack.runner

import com.kimjisub.launchpad.unipack.runner.LedRunner.LedChange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LedChangeQueueTest {

	private var scheduled = 0
	private val queue = LedChangeQueue { scheduled++ }

	private fun pads(from: Int, count: Int) = (from until from + count).map { LedChange.PadOn(it % 8, it / 8 % 8, 0, it) }

	@Test
	fun manyOffers_scheduleOneDrainUntilItRuns() {
		repeat(1000) { queue.offer(pads(it, 1)) }

		assertEquals(1, scheduled)
		assertEquals(pads(0, 1000), queue.drain())
	}

	@Test
	fun offerAfterADrain_schedulesTheNextOne() {
		queue.offer(pads(0, 3))
		queue.drain()
		queue.offer(pads(3, 3))

		assertEquals(2, scheduled)
		assertEquals(pads(3, 3), queue.drain())
	}

	@Test
	fun partialDrain_keepsOrderAndSchedulesTheRest() {
		queue.offer(pads(0, 2500))

		val taken = mutableListOf<LedChange>()
		taken += queue.drain(1024)
		assertEquals(2, scheduled)
		taken += queue.drain(1024)
		assertEquals(3, scheduled)
		taken += queue.drain(1024)
		assertEquals(3, scheduled)

		assertEquals(pads(0, 2500), taken)
		assertTrue(queue.drain().isEmpty())
	}

	@Test
	fun offersDuringAPartialDrain_joinTheEndWithoutAnExtraDrain() {
		queue.offer(pads(0, 10))
		val first = queue.drain(4)
		queue.offer(pads(10, 5))

		assertEquals(2, scheduled)
		assertEquals(pads(0, 15), first + queue.drain())
	}
}
