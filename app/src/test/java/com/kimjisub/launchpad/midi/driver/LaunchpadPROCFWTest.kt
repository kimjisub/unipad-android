package com.kimjisub.launchpad.midi.driver

import org.junit.Assert.assertEquals
import org.junit.Test

/** The Launchpad Pro custom firmware ("Launchpad Open"): drum-rack notes on channel 16 of the first cable. */
class LaunchpadPROCFWTest {

	private class Capture : DriverRef.OnSendSignalListener, DriverRef.OnReceiveSignalListener {
		val sent = mutableListOf<String>()
		val events = mutableListOf<String>()

		override fun onSend(cmd: Byte, sig: Byte, note: Byte, velocity: Byte) {
			sent += byteArrayOf(cmd, sig, note, velocity).joinToString(" ") { "%02X".format(it) }
		}

		override fun onSendRaw(messages: List<ByteArray>, cableNumber: Int) {
			for (m in messages) sent += "raw$cableNumber " + m.joinToString(" ") { "%02X".format(it) }
		}

		override fun onReceived(cmd: Int, sig: Int, note: Int, velocity: Int) {}
		override fun onUnknownReceived(cmd: Int, sig: Int, note: Int, velocity: Int) {
			events += "unknown $note"
		}

		override fun onPadTouch(x: Int, y: Int, upDown: Boolean, velocity: Int) {
			events += "pad $x,$y,$upDown,$velocity"
		}

		override fun onChainTouch(c: Int, upDown: Boolean) {
			events += "chain $c,$upDown"
		}

		override fun onFunctionKeyTouch(f: Int, upDown: Boolean) {
			events += "key $f,$upDown"
		}
	}

	private fun capture(block: LaunchpadPROCFW.() -> Unit): Capture {
		val driver = LaunchpadPROCFW()
		val out = Capture()
		driver.setOnSendSignalListener(out)
		driver.setOnGetSignalListener(out)
		driver.block()
		return out
	}

	@Test
	fun padLeds_areDrumRackNotesOnChannel16OfTheFirstCable() {
		assertEquals(listOf("09 9F 40 05"), capture { sendPadLed(0, 0, 5) }.sent)
		assertEquals(listOf("09 9F 63 7F"), capture { sendPadLed(0, 7, 200) }.sent)
		assertEquals(listOf("09 9F 24 00"), capture { sendPadLed(7, 0, 0) }.sent)
		assertEquals(listOf("09 9F 44 01"), capture { sendPadLed(7, 4, 1) }.sent)
		assertEquals(emptyList<String>(), capture { sendPadLed(8, 0, 1) }.sent)
	}

	@Test
	fun ringLeds_followTheDrumRackRing() {
		val notes = (0..32).map { f -> capture { sendFunctionKeyLed(f, 3) }.sent.single().split(" ")[2].toInt(16) }
		assertEquals((28..35) + (100..107) + (123 downTo 116) + (115 downTo 108) + 27, notes)
		assertEquals(listOf("09 9F 64 03"), capture { sendChainLed(0, 3) }.sent)
		assertEquals(emptyList<String>(), capture { sendChainLed(24, 3) }.sent)
	}

	@Test
	fun clear_sendsEveryPadAndRingButton() {
		assertEquals(64 + 33, capture { sendClearLed() }.sent.size)
	}

	@Test
	fun init_entersPerformanceModeOnTheFirstCable() {
		assertEquals(
			listOf("raw0 F0 00 20 29 02 10 21 01 F7", "raw0 F0 00 20 29 02 10 0E 00 F7"),
			capture { initialize() }.sent,
		)
	}

	@Test
	fun input_acceptsNoteOnNoteOffAndCcOnAnyCable() {
		val events = capture {
			getSignal(0x09, 0x9F.toByte().toInt(), 64, 100)
			getSignal(0x08, 0x8F.toByte().toInt(), 64, 100)
			getSignal(0x1B, 0xBF.toByte().toInt(), 99, 1)
			getSignal(0x09, 0x9F.toByte().toInt(), 100, 1)
			getSignal(0x09, 0x9F.toByte().toInt(), 116, 0)
			getSignal(0x09, 0x9F.toByte().toInt(), 108, 1)
			getSignal(0x09, 0x9F.toByte().toInt(), 27, 1)
			getSignal(0x09, 0x9F.toByte().toInt(), 20, 1)
			getSignal(0x0A, 0xAF.toByte().toInt(), 64, 1)
		}.events
		assertEquals(
			listOf(
				"pad 0,0,true,100", "pad 0,0,false,100", "pad 0,7,true,1",
				"chain 0,true", "key 8,true",
				"chain 15,false", "key 23,false",
				"chain 23,true", "key 31,true",
				"key 32,true",
				"unknown 20", "unknown 64",
			),
			events,
		)
	}
}
