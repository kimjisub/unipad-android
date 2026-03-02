package com.kimjisub.launchpad.db.ent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class UnipackTest {

	@Test
	fun create_setsDefaultValues() {
		val unipack = Unipack.create("test-id")

		assertEquals("test-id", unipack.id)
		assertFalse(unipack.bookmark)
		assertEquals(0L, unipack.openCount)
		assertNull(unipack.lastOpenedAt)
		// createdAt should be approximately now
		assertTrue(System.currentTimeMillis() - unipack.createdAt.time < 1000)
	}

	@Test
	fun equals_sameValues_returnsTrue() {
		val date = Date(1709251200000L)
		val a = Unipack("id", true, 5, date, date)
		val b = Unipack("id", true, 5, date, date)

		assertEquals(a, b)
	}

	@Test
	fun equals_differentId_returnsFalse() {
		val date = Date()
		val a = Unipack("id1", false, 0, null, date)
		val b = Unipack("id2", false, 0, null, date)

		assertNotEquals(a, b)
	}

	@Test
	fun equals_differentBookmark_returnsFalse() {
		val date = Date()
		val a = Unipack("id", true, 0, null, date)
		val b = Unipack("id", false, 0, null, date)

		assertNotEquals(a, b)
	}

	@Test
	fun equals_sameInstance_returnsTrue() {
		val unipack = Unipack.create("id")

		assertTrue(unipack == unipack)
	}

	@Test
	fun equals_null_returnsFalse() {
		val unipack = Unipack.create("id")

		assertFalse(unipack.equals(null))
	}

	@Test
	fun hashCode_sameValues_sameHash() {
		val date = Date(1709251200000L)
		val a = Unipack("id", true, 5, date, date)
		val b = Unipack("id", true, 5, date, date)

		assertEquals(a.hashCode(), b.hashCode())
	}

	@Test
	fun hashCode_differentValues_differentHash() {
		val date = Date()
		val a = Unipack("id1", false, 0, null, date)
		val b = Unipack("id2", false, 0, null, date)

		assertNotEquals(a.hashCode(), b.hashCode())
	}

	@Test
	fun toString_containsAllFields() {
		val date = Date(1709251200000L)
		val unipack = Unipack("my-pack", true, 10, date, date)

		val str = unipack.toString()

		assertTrue(str.contains("my-pack"))
		assertTrue(str.contains("bookmark=true"))
		assertTrue(str.contains("openCount=10"))
	}
}
