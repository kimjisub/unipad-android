package com.kimjisub.design.touch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PadTouchTrackerTest {

	private data class Event(val x: Int, val y: Int, val down: Boolean)

	private val events = mutableListOf<Event>()

	// 8x8 grid of 100px pads: the centre of pad (x, y) is (y * 100 + 50, x * 100 + 50).
	private fun tracker(slide: Boolean) = PadTouchTracker { x, y, down -> events += Event(x, y, down) }
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
}
