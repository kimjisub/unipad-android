package com.kimjisub.launchpad.unipack.runner

import com.kimjisub.launchpad.unipack.runner.LedRunner.LedChange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
		assertEquals(pads(0, 1000), queue.drain().changes)
	}

	@Test
	fun offerAfterADrain_schedulesTheNextOne() {
		queue.offer(pads(0, 3))
		queue.drain()
		queue.offer(pads(3, 3))

		assertEquals(2, scheduled)
		assertEquals(pads(3, 3), queue.drain().changes)
	}

	@Test
	fun partialDrain_keepsOrderAndSchedulesTheRest() {
		queue.offer(pads(0, 2500))

		val taken = mutableListOf<LedChange>()
		taken += queue.drain(1024).changes
		assertEquals(2, scheduled)
		taken += queue.drain(1024).changes
		assertEquals(3, scheduled)
		taken += queue.drain(1024).changes
		assertEquals(3, scheduled)

		assertEquals(pads(0, 2500), taken)
		assertTrue(queue.drain().changes.isEmpty())
	}

	@Test
	fun offersDuringAPartialDrain_joinTheEndWithoutAnExtraDrain() {
		queue.offer(pads(0, 10))
		val first = queue.drain(4).changes
		queue.offer(pads(10, 5))

		assertEquals(2, scheduled)
		assertEquals(pads(0, 15), first + queue.drain().changes)
	}

	@Test
	fun resetWithNothingWaiting_runsRightAway() {
		assertTrue(queue.requestReset())
		assertEquals(0, scheduled)
	}

	@Test
	fun resetBehindABacklog_comesRightAfterItsLastChange_inBoundedDrains() {
		queue.offer(pads(0, 2500))
		assertFalse(queue.requestReset())
		queue.offer(pads(2500, 10))

		val batches = generateSequence { queue.drain(1024).takeIf { it.changes.isNotEmpty() || it.reset } }.toList()

		assertEquals(listOf(1024, 1024, 452, 10), batches.map { it.changes.size })
		assertEquals(listOf(false, false, true, false), batches.map { it.reset })
		assertEquals(pads(0, 2510), batches.flatMap { it.changes })
		assertEquals(4, scheduled)
	}

	@Test
	fun resetsInARow_areEachReportedOnce() {
		queue.offer(pads(0, 3))
		assertFalse(queue.requestReset())
		assertFalse(queue.requestReset())

		val first = queue.drain()
		val second = queue.drain()

		assertEquals(pads(0, 3), first.changes)
		assertTrue(first.reset)
		assertTrue(second.changes.isEmpty())
		assertTrue(second.reset)
		assertFalse(queue.drain().reset)
		assertTrue(queue.requestReset())
	}

	@Test
	fun resetWhileOnlyAResetWaits_queuesBehindIt() {
		queue.offer(pads(0, 1))
		queue.requestReset()
		queue.requestReset()
		queue.drain()

		assertFalse(queue.requestReset())
		assertTrue(queue.drain().reset)
		assertTrue(queue.drain().reset)
		assertTrue(queue.requestReset())
	}

	@Test
	fun discard_dropsWaitingChangesAndResets_soTheScheduledDrainFindsNothing() {
		queue.offer(pads(0, 2500))
		queue.requestReset()
		queue.drain(1024)

		queue.discard()
		val stale = queue.drain(1024)

		assertEquals(2, scheduled)
		assertTrue(stale.changes.isEmpty())
		assertFalse(stale.reset)
		assertTrue(queue.requestReset())
	}

	@Test
	fun offersAfterADiscard_keepTheirOrderAndLaterResetsTheirPlace() {
		queue.offer(pads(0, 10))
		queue.discard()
		queue.offer(pads(10, 3))
		assertFalse(queue.requestReset())
		queue.offer(pads(13, 2))

		val first = queue.drain()
		val second = queue.drain()

		assertEquals(2, scheduled)
		assertEquals(pads(10, 3), first.changes)
		assertTrue(first.reset)
		assertEquals(pads(13, 2), second.changes)
		assertFalse(second.reset)
	}

	@Test
	fun discard_returnsTheLastWaitingChainChange() {
		queue.offer(pads(0, 3) + LedChange.ChainChange(1) + pads(3, 3))
		queue.offer(listOf(LedChange.ChainChange(2)) + pads(6, 2000))

		assertEquals(LedChange.ChainChange(2), queue.discard())
		assertTrue(queue.drain().changes.isEmpty())
	}

	@Test
	fun discard_ignoresChainChangesAlreadyDrainedOrDiscarded() {
		queue.offer(listOf(LedChange.ChainChange(1)) + pads(0, 3))
		queue.drain()
		queue.offer(pads(3, 3))
		assertNull(queue.discard())

		queue.offer(listOf(LedChange.ChainChange(2)))
		queue.discard()
		assertNull(queue.discard())
	}
}
