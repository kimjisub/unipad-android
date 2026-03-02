package com.kimjisub.launchpad.unipack

import com.kimjisub.launchpad.unipack.struct.LedAnimation
import com.kimjisub.launchpad.unipack.struct.Sound
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

class UniPackCircularQueueTest {

	private lateinit var unipack: TestUniPack

	/**
	 * Concrete subclass of UniPack for testing circular queue logic.
	 */
	private class TestUniPack : UniPack() {
		override val id: String = "test-unipack"
		override fun lastModified(): Long = 0
		override fun loadInfo(): UniPack = this
		override fun loadDetail(): UniPack = this
		override fun checkFile() {}
		override fun delete() {}
		override fun getPathString(): String = "/test"
		override fun getByteSize(): Long = 0
	}

	@Before
	fun setUp() {
		unipack = TestUniPack()
		unipack.chain = 2
		unipack.buttonX = 2
		unipack.buttonY = 2
	}

	private fun buildSoundTable(chains: Int, x: Int, y: Int): Array<Array<Array<ArrayDeque<Sound>?>>> {
		return Array(chains) {
			Array(x) {
				Array(y) { null as ArrayDeque<Sound>? }
			}
		}
	}

	private fun buildLedTable(chains: Int, x: Int, y: Int): Array<Array<Array<ArrayDeque<LedAnimation>?>>> {
		return Array(chains) {
			Array(x) {
				Array(y) { null as ArrayDeque<LedAnimation>? }
			}
		}
	}

	private fun makeSound(num: Int) = Sound(file = File("sound$num.wav"), loop = 1, num = num)

	private fun makeLedAnimation(num: Int) = LedAnimation(
		ledEvents = arrayListOf(),
		loop = 1,
		num = num
	)

	// ===== soundGet tests =====

	@Test
	fun soundGet_returnsFirstSoundInQueue() {
		val table = buildSoundTable(2, 2, 2)
		val s0 = makeSound(0)
		val s1 = makeSound(1)
		table[0][0][0] = ArrayDeque(listOf(s0, s1))
		unipack.soundTable = table

		val result = unipack.soundGet(0, 0, 0)
		assertEquals(s0, result)
	}

	@Test
	fun soundGet_returnsNullForEmptySlot() {
		val table = buildSoundTable(2, 2, 2)
		unipack.soundTable = table

		assertNull(unipack.soundGet(0, 0, 0))
	}

	@Test
	fun soundGet_returnsNullForOutOfBounds() {
		val table = buildSoundTable(2, 2, 2)
		unipack.soundTable = table

		assertNull(unipack.soundGet(5, 0, 0))
	}

	@Test
	fun soundGet_withNum_returnsModuloIndex() {
		val table = buildSoundTable(2, 2, 2)
		val s0 = makeSound(0)
		val s1 = makeSound(1)
		val s2 = makeSound(2)
		table[0][0][0] = ArrayDeque(listOf(s0, s1, s2))
		unipack.soundTable = table

		assertEquals(s0, unipack.soundGet(0, 0, 0, 0))
		assertEquals(s1, unipack.soundGet(0, 0, 0, 1))
		assertEquals(s2, unipack.soundGet(0, 0, 0, 2))
		// Wraps around
		assertEquals(s0, unipack.soundGet(0, 0, 0, 3))
		assertEquals(s1, unipack.soundGet(0, 0, 0, 4))
	}

	@Test
	fun soundGet_withNum_returnsNullForEmptySlot() {
		val table = buildSoundTable(2, 2, 2)
		unipack.soundTable = table

		assertNull(unipack.soundGet(0, 0, 0, 0))
	}

	// ===== soundPush tests =====

	@Test
	fun soundPush_rotatesQueueByOne() {
		val table = buildSoundTable(2, 2, 2)
		val s0 = makeSound(0)
		val s1 = makeSound(1)
		val s2 = makeSound(2)
		table[0][0][0] = ArrayDeque(listOf(s0, s1, s2))
		unipack.soundTable = table

		// Before push: [s0, s1, s2]
		assertEquals(s0, unipack.soundGet(0, 0, 0))

		unipack.soundPush(0, 0, 0)
		// After push: [s1, s2, s0]
		assertEquals(s1, unipack.soundGet(0, 0, 0))

		unipack.soundPush(0, 0, 0)
		// After second push: [s2, s0, s1]
		assertEquals(s2, unipack.soundGet(0, 0, 0))

		unipack.soundPush(0, 0, 0)
		// Full cycle: [s0, s1, s2]
		assertEquals(s0, unipack.soundGet(0, 0, 0))
	}

	@Test
	fun soundPush_doesNothingForNullSlot() {
		val table = buildSoundTable(2, 2, 2)
		unipack.soundTable = table

		// Should not throw
		unipack.soundPush(0, 0, 0)
	}

	@Test
	fun soundPush_doesNothingForOutOfBounds() {
		val table = buildSoundTable(2, 2, 2)
		unipack.soundTable = table

		// Should not throw
		unipack.soundPush(5, 0, 0)
	}

	@Test
	fun soundPush_singleElement_staysSame() {
		val table = buildSoundTable(2, 2, 2)
		val s0 = makeSound(0)
		table[0][1][0] = ArrayDeque(listOf(s0))
		unipack.soundTable = table

		unipack.soundPush(0, 1, 0)
		assertEquals(s0, unipack.soundGet(0, 1, 0))
	}

	// ===== ledGet tests =====

	@Test
	fun ledGet_returnsFirstAnimation() {
		val table = buildLedTable(2, 2, 2)
		val led0 = makeLedAnimation(0)
		val led1 = makeLedAnimation(1)
		table[0][0][0] = ArrayDeque(listOf(led0, led1))
		unipack.ledAnimationTable = table

		assertEquals(led0, unipack.ledGet(0, 0, 0))
	}

	@Test
	fun ledGet_returnsNullForEmptySlot() {
		val table = buildLedTable(2, 2, 2)
		unipack.ledAnimationTable = table

		assertNull(unipack.ledGet(0, 0, 0))
	}

	@Test
	fun ledGet_returnsNullForOutOfBounds() {
		val table = buildLedTable(2, 2, 2)
		unipack.ledAnimationTable = table

		assertNull(unipack.ledGet(5, 0, 0))
	}

	// ===== ledPush tests =====

	@Test
	fun ledPush_rotatesQueueByOne() {
		val table = buildLedTable(2, 2, 2)
		val led0 = makeLedAnimation(0)
		val led1 = makeLedAnimation(1)
		val led2 = makeLedAnimation(2)
		table[0][0][0] = ArrayDeque(listOf(led0, led1, led2))
		unipack.ledAnimationTable = table

		assertEquals(led0, unipack.ledGet(0, 0, 0))

		unipack.ledPush(0, 0, 0)
		assertEquals(led1, unipack.ledGet(0, 0, 0))

		unipack.ledPush(0, 0, 0)
		assertEquals(led2, unipack.ledGet(0, 0, 0))

		unipack.ledPush(0, 0, 0)
		assertEquals(led0, unipack.ledGet(0, 0, 0))
	}

	@Test
	fun ledPush_doesNothingForNullSlot() {
		val table = buildLedTable(2, 2, 2)
		unipack.ledAnimationTable = table

		// Should not throw
		unipack.ledPush(0, 0, 0)
	}

	@Test
	fun ledPush_doesNothingForOutOfBounds() {
		val table = buildLedTable(2, 2, 2)
		unipack.ledAnimationTable = table

		// Should not throw
		unipack.ledPush(5, 0, 0)
	}

	@Test
	fun ledPush_singleElement_staysSame() {
		val table = buildLedTable(2, 2, 2)
		val led0 = makeLedAnimation(0)
		table[0][1][0] = ArrayDeque(listOf(led0))
		unipack.ledAnimationTable = table

		unipack.ledPush(0, 1, 0)
		assertEquals(led0, unipack.ledGet(0, 1, 0))
	}

	// ===== Multiple slots independence =====

	@Test
	fun soundPush_differentSlots_areIndependent() {
		val table = buildSoundTable(2, 2, 2)
		val s0 = makeSound(0)
		val s1 = makeSound(1)
		val s2 = makeSound(2)
		val s3 = makeSound(3)
		table[0][0][0] = ArrayDeque(listOf(s0, s1))
		table[0][0][1] = ArrayDeque(listOf(s2, s3))
		unipack.soundTable = table

		unipack.soundPush(0, 0, 0)
		// Slot [0][0][0] rotated: s1 is now first
		assertEquals(s1, unipack.soundGet(0, 0, 0))
		// Slot [0][0][1] unchanged: s2 is still first
		assertEquals(s2, unipack.soundGet(0, 0, 1))
	}

	// ===== addErr tests =====

	@Test
	fun addErr_setsInitialError() {
		assertNull(unipack.errorDetail)
		unipack.addErr("first error")
		assertEquals("first error", unipack.errorDetail)
	}

	@Test
	fun addErr_appendsSubsequentErrors() {
		unipack.addErr("error 1")
		unipack.addErr("error 2")
		assertEquals("error 1\nerror 2", unipack.errorDetail)
	}

	// ===== equals tests =====

	@Test
	fun equals_sameId_returnsTrue() {
		val other = TestUniPack()
		assertTrue(unipack == other)
	}

	@Test
	fun equals_differentType_returnsFalse() {
		assertFalse(unipack.equals("not a unipack"))
	}

	@Test
	fun toString_containsId() {
		assertEquals("UniPack(id=test-unipack)", unipack.toString())
	}
}
