package com.kimjisub.launchpad.tool

import org.junit.Assert.assertEquals
import org.junit.Test

class TraceLogTextTest {

	@Test
	fun emptySequence_givesEmptyTextOnEveryPad() {
		val texts = TraceLogText.perPad(emptyList(), 2, 3)

		assertEquals(2, texts.size)
		assertEquals(3, texts[0].size)
		for (row in texts) for (text in row) assertEquals("", text)
	}

	@Test
	fun tapsAreNumberedFromOne_andRepeatedPadsAccumulate() {
		val seq = listOf(Pair(0, 0), Pair(1, 2), Pair(0, 0), Pair(1, 1))

		val texts = TraceLogText.perPad(seq, 2, 3)

		assertEquals("1 3 ", texts[0][0])
		assertEquals("2 ", texts[1][2])
		assertEquals("4 ", texts[1][1])
		assertEquals("", texts[0][1])
	}

	@Test
	fun tapsOutsideTheGrid_areIgnoredButStillCounted() {
		val seq = listOf(Pair(5, 5), Pair(0, 0), Pair(-1, 0))

		val texts = TraceLogText.perPad(seq, 2, 2)

		assertEquals("2 ", texts[0][0])
	}
}
