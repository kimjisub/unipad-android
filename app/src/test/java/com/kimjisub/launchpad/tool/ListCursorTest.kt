package com.kimjisub.launchpad.tool

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ListCursorTest {

	private val all = listOf("a", "b", "c", "d", "e")

	// Search results for a query that keeps only b and d.
	private val results = listOf("b", "d")

	@Test
	fun nextAndPrevStayInsideResults() {
		val atB = ListCursor(results, "b")
		assertEquals("d", atB.next())
		assertFalse(atB.hasPrev)

		val atD = ListCursor(results, "d")
		assertEquals("b", atD.prev())
		assertFalse(atD.hasNext)
		assertNull(atD.next())
	}

	@Test
	fun sameAnchorMovesToHiddenNeighbourWithoutFilter() {
		assertEquals("c", ListCursor(all, "b").next())
	}

	@Test
	fun anchorFilteredOutStartsFromFirstResult() {
		val cursor = ListCursor(results, "c")
		assertEquals(-1, cursor.index)
		assertFalse(cursor.hasCurrent)
		assertFalse(cursor.hasPrev)
		assertEquals("b", cursor.next())
	}

	@Test
	fun noAnchorStartsFromFirstItem() {
		val cursor = ListCursor(all, null)
		assertNull(cursor.current())
		assertEquals("a", cursor.next())
	}

	@Test
	fun currentIsAnchorWhenVisible() {
		val cursor = ListCursor(results, "d")
		assertTrue(cursor.hasCurrent)
		assertEquals("d", cursor.current())
	}

	@Test
	fun emptyResultsHaveNothingToPick() {
		val cursor = ListCursor(emptyList<String>(), "b")
		assertFalse(cursor.hasCurrent)
		assertFalse(cursor.hasPrev)
		assertFalse(cursor.hasNext)
	}
}
