package com.kimjisub.launchpad.midi

import org.junit.Assert.*
import org.junit.Test

class MidiStreamCodecTest {
	@Test fun fragmentedAndRunningStatusKeepOrderAndNormalizeReleases() {
		val events = mutableListOf<List<Int>>()
		val decoder = MidiStreamDecoder { status, note, value -> events.add(listOf(status, note, value)) }
		decoder.accept(byteArrayOf(0x91.toByte(), 36), 0, 2)
		decoder.accept(byteArrayOf(127, 37, 0, 0x81.toByte(), 36, 64, 0xb0.toByte(), 28, 5), 0, 9)
		assertEquals(listOf(listOf(0x91,36,127), listOf(0x91,37,0), listOf(0x91,36,0), listOf(0xb0,28,5)), events)
	}

	@Test fun malformedAndHugeSystemMessagesHaveBoundedStateAndRecover() {
		val events = mutableListOf<List<Int>>()
		val decoder = MidiStreamDecoder { a,b,c -> events.add(listOf(a,b,c)) }
		decoder.accept(byteArrayOf(0x90.toByte(), 36, 0xf0.toByte()), 0, 3)
		repeat(1000) { decoder.accept(ByteArray(1024) { 1 }, 0, 1024) }
		decoder.accept(byteArrayOf(0xf7.toByte(), 1, 2, 0x90.toByte(), 36, 0xf8.toByte(), 100), 0, 7)
		decoder.accept(byteArrayOf(0x90.toByte()), -1, 100)
		assertEquals(listOf(listOf(0x90,36,100)), events)
	}

	@Test fun usbHeadersAreRemovedAndSysexPaddingDoesNotBecomeMidiData() {
		assertArrayEquals(byteArrayOf(0x91.toByte(),36,127), MidiStreamEncoder.packet(9, 0x91.toByte(), 36, 127))
		assertArrayEquals(byteArrayOf(0xf0.toByte(),1,2,0xf7.toByte()), MidiStreamEncoder.packets(byteArrayOf(4,0xf0.toByte(),1,2,5,0xf7.toByte(),0,0)))
		assertNull(MidiStreamEncoder.packets(byteArrayOf(9, 0x90.toByte(), 36)))
		assertNull(MidiStreamEncoder.packet(9, 0x90.toByte(), 0x80.toByte(), 0))
	}
}
