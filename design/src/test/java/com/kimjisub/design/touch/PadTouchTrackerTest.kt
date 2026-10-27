package com.kimjisub.design.touch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PadTouchTrackerTest {

	@Test
	fun leavingAndReenteringTheGridKeepsIndependentFingersInBothModes() {
		for (sliding in listOf(false, true)) {
			events.clear()
			val t = tracker(slide = sliding)
			t.down(0, 1, 2)
			t.down(1, 3, 4)
			t.move(0, -1, 0)
			t.move(0, 1, 3)
			t.up(1)
			t.up(0)
			val expected = mutableListOf(Event(1, 2, true), Event(3, 4, true))
			if (sliding) expected += listOf(Event(1, 2, false), Event(1, 3, true))
			expected += listOf(Event(3, 4, false), Event(1, if (sliding) 3 else 2, false))
			assertEquals(expected, events)
		}
	}

	@Test
	fun layoutChangeSuppressesHeldFingersUntilLiftInSlideMode() {
		val t = tracker(slide = true)
		t.down(0, 1, 2)
		t.setGrid(8, 8)
		events.clear()
		t.move(0, 1, 3)
		assertEquals(emptyList<Event>(), events)
		t.up(0)
		t.down(0, 1, 3)
		t.up(0)
		assertEquals(listOf(Event(1, 3, true), Event(1, 3, false)), events)
	}

	@Test
	fun changingSlideModeReleasesAndSuppressesEveryCurrentFinger() {
		for (initial in listOf(false, true)) {
			events.clear()
			val t = tracker(slide = initial)
			t.down(0, 1, 2)
			t.down(1, 3, 4)
			events.clear()
			t.slide = !initial
			assertEquals(setOf(Event(1, 2, false), Event(3, 4, false)), events.toSet())
			events.clear()
			t.move(0, 1, 3)
			t.move(1, 3, 5)
			t.up(0)
			t.up(1)
			assertEquals(emptyList<Event>(), events)
		}
	}

	@Test
	fun aMoveWithoutADownCannotStartAPress() {
		val t = tracker(slide = true)
		t.move(9, 1, 2)
		t.up(9)
		assertEquals(emptyList<Event>(), events)
	}

	@Test
	fun twoPointersOnOneCellKeepTheirIdsWhenReleasedInEitherOrder() {
		for (first in listOf(7L, 11L)) {
			val delivered = mutableListOf<Pair<Long, Boolean>>()
			val tracker = PadTouchTracker { id, _, _, down -> delivered += id to down }.apply { setGrid(8, 8) }
			tracker.down(7, 50f, 50f, 800, 800)
			tracker.down(11, 50f, 50f, 800, 800)
			tracker.up(first)
			tracker.up(if (first == 7L) 11 else 7)
			assertEquals(listOf(7L to true, 11L to true, first to false, (if (first == 7L) 11L else 7L) to false), delivered)
		}
	}

	@Test
	fun slidingKeepsThePointerIdOnBothSidesOfTheMove() {
		val delivered = mutableListOf<Triple<Long, Int, Boolean>>()
		val tracker = PadTouchTracker { id, _, col, down -> delivered += Triple(id, col, down) }
			.apply { setGrid(8, 8); slide = true }
		tracker.down(17, 50f, 50f, 800, 800)
		tracker.move(17, 150f, 50f, 800, 800)
		tracker.up(17)
		assertEquals(listOf(Triple(17L, 0, true), Triple(17L, 0, false), Triple(17L, 1, true), Triple(17L, 1, false)), delivered)
	}

	@Test
	fun cancellingDeliversEveryHeldIdOnceAndAllowsItsReuseAfterLift() {
		val delivered = mutableListOf<Pair<Long, Boolean>>()
		val tracker = PadTouchTracker { id, _, _, down -> delivered += id to down }.apply { setGrid(8, 8) }
		tracker.down(3, 50f, 50f, 800, 800)
		tracker.down(9, 50f, 50f, 800, 800)
		delivered.clear()
		tracker.cancel()
		assertEquals(setOf(3L to false, 9L to false), delivered.toSet())
		assertEquals(2, delivered.size)
		delivered.clear()
		tracker.up(3)
		tracker.up(9)
		tracker.down(3, 50f, 50f, 800, 800)
		tracker.up(3)
		assertEquals(listOf(3L to true, 3L to false), delivered)
	}

	private data class Event(val x: Int, val y: Int, val down: Boolean)

	private val events = mutableListOf<Event>()

	// 8x8 grid of 100px pads: the centre of pad (x, y) is (y * 100 + 50, x * 100 + 50).
	private fun tracker(slide: Boolean) = PadTouchTracker { _, x, y, down -> events += Event(x, y, down) }
		.apply { setGrid(8, 8); this.slide = slide }

	private fun PadTouchTracker.down(id: Long, x: Int, y: Int) = down(id, y * 100 + 50f, x * 100 + 50f, 800, 800)

	private fun PadTouchTracker.move(id: Long, x: Int, y: Int) = move(id, y * 100 + 50f, x * 100 + 50f, 800, 800)

	@Test
	fun cellAt_mapsCornersOfAnEightByEightGrid() {
		assertEquals(0, PadTouchTracker.cellAt(0f, 0f, 800, 800, 8, 8))
		assertEquals(7, PadTouchTracker.cellAt(799f, 0f, 800, 800, 8, 8))
		assertEquals(56, PadTouchTracker.cellAt(0f, 799f, 800, 800, 8, 8))
		assertEquals(63, PadTouchTracker.cellAt(799f, 799f, 800, 800, 8, 8))
	}

	@Test
	fun cellAt_usesRowTimesColsPlusCol_forNonSquareGrids() {
		// 4 rows x 6 columns over 600x400: cell 100x100; point (250, 150) -> row 1, col 2
		assertEquals(1 * 6 + 2, PadTouchTracker.cellAt(250f, 150f, 600, 400, 4, 6))
	}

	@Test
	fun cellAt_isNullOutsideTheGridOrWithoutAGrid() {
		assertNull(PadTouchTracker.cellAt(-1f, 10f, 800, 800, 8, 8))
		assertNull(PadTouchTracker.cellAt(800f, 10f, 800, 800, 8, 8))
		assertNull(PadTouchTracker.cellAt(10f, 10f, 800, 800, 0, 8))
		assertNull(PadTouchTracker.cellAt(10f, 10f, 0, 800, 8, 8))
	}

	@Test
	fun eachPointerPressesAndReleasesItsOwnPad() {
		val t = tracker(slide = false)
		t.down(0, 1, 2)
		t.down(1, 3, 4)
		t.up(0)
		t.up(1)
		assertEquals(listOf(Event(1, 2, true), Event(3, 4, true), Event(1, 2, false), Event(3, 4, false)), events)
	}

	@Test
	fun pointerOutsideTheGridPressesNothingAndBlocksNoOtherPointer() {
		val t = tracker(slide = false)
		t.down(0, -900f, 400f, 800, 800)
		t.down(1, 4, 4)
		t.up(1)
		t.up(0)
		assertEquals(listOf(Event(4, 4, true), Event(4, 4, false)), events)
	}

	@Test
	fun withoutSlide_draggingKeepsTheFirstPad() {
		val t = tracker(slide = false)
		t.down(0, 3, 3)
		t.move(0, 3, 4)
		t.up(0)
		assertEquals(listOf(Event(3, 3, true), Event(3, 3, false)), events)
	}

	@Test
	fun withSlide_draggingMovesThePressToTheNewPad() {
		val t = tracker(slide = true)
		t.down(0, 3, 3)
		t.move(0, 3, 4)
		t.up(0)
		assertEquals(listOf(Event(3, 3, true), Event(3, 3, false), Event(3, 4, true), Event(3, 4, false)), events)
	}

	@Test
	fun setGridReleasesHeldPadsInTheOldGrid() {
		val t = tracker(slide = false)
		t.down(0, 2, 5)
		t.setGrid(4, 4)
		t.up(0)
		assertEquals(listOf(Event(2, 5, true), Event(2, 5, false)), events)
	}

	@Test
	fun cancelReleasesHeldPadsAndTheirLiftReleasesNothingMore() {
		val t = tracker(slide = false)
		t.down(0, 1, 2)
		t.down(1, 3, 4)
		events.clear()
		t.cancel()
		assertEquals(setOf(Event(1, 2, false), Event(3, 4, false)), events.toSet())

		events.clear()
		t.up(0)
		t.up(1)
		assertEquals(emptyList<Event>(), events)
	}

	@Test
	fun withSlide_aFingerDownAtCancelPressesNothingUntilItLifts() {
		val t = tracker(slide = true)
		t.down(0, 1, 2)
		t.down(1, -1, 0)
		t.cancel()
		events.clear()
		t.move(0, 1, 3)
		t.move(1, 2, 2)
		assertEquals(emptyList<Event>(), events)

		t.up(0)
		t.down(2, 1, 3)
		t.move(2, 1, 4)
		assertEquals(listOf(Event(1, 3, true), Event(1, 3, false), Event(1, 4, true)), events)
	}
}
