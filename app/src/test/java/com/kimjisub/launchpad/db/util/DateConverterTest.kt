package com.kimjisub.launchpad.db.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Date

class DateConverterTest {

	private val converter = DateConverter()

	@Test
	fun fromTimestamp_returnsDateForValidValue() {
		val timestamp = 1709251200000L // 2024-03-01 00:00:00 UTC
		val result = converter.fromTimestamp(timestamp)

		assertEquals(Date(timestamp), result)
	}

	@Test
	fun fromTimestamp_returnsNullForNullValue() {
		val result = converter.fromTimestamp(null)

		assertNull(result)
	}

	@Test
	fun dateToTimestamp_returnsTimestampForValidDate() {
		val timestamp = 1709251200000L
		val date = Date(timestamp)

		val result = converter.dateToTimestamp(date)

		assertEquals(timestamp, result)
	}

	@Test
	fun dateToTimestamp_returnsNullForNullDate() {
		val result = converter.dateToTimestamp(null)

		assertNull(result)
	}

	@Test
	fun roundTrip_preservesDate() {
		val originalDate = Date()
		val timestamp = converter.dateToTimestamp(originalDate)
		val restoredDate = converter.fromTimestamp(timestamp)

		assertEquals(originalDate, restoredDate)
	}
}
