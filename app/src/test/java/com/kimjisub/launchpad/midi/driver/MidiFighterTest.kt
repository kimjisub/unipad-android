package com.kimjisub.launchpad.midi.driver

import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test

class MidiFighterTest {

	private lateinit var driver: MidiFighter
	private lateinit var receiveListener: DriverRef.OnReceiveSignalListener
	private lateinit var sendListener: DriverRef.OnSendSignalListener

	@Before
	fun setUp() {
		driver = MidiFighter()
		receiveListener = mockk(relaxed = true)
		sendListener = mockk(relaxed = true)
		driver.setOnGetSignalListener(receiveListener)
		driver.setOnSendSignalListener(sendListener)
	}

	// --- getSignal: note-on (cmd=9) lower range 36-67 ---

	@Test
	fun getSignal_noteOn_lowerRange_firstNote() {
		// note=36: x=(67-36)/4+1=8, y=4-(67-36)%4=4-3=1 → pad(7, 0)
		driver.getSignal(cmd = 9, sig = 0, note = 36, velocity = 100)
		verify { receiveListener.onPadTouch(7, 0, true, 100) }
	}

	@Test
	fun getSignal_noteOn_lowerRange_lastNote() {
		// note=67: x=(67-67)/4+1=1, y=4-(67-67)%4=4-0=4 → pad(0, 3)
		driver.getSignal(cmd = 9, sig = 0, note = 67, velocity = 80)
		verify { receiveListener.onPadTouch(0, 3, true, 80) }
	}

	// --- getSignal: note-on (cmd=9) upper range 68-99 ---

	@Test
	fun getSignal_noteOn_upperRange_firstNote() {
		// note=68: x=(99-68)/4+1=8, y=8-(99-68)%4=8-3=5 → pad(7, 4)
		driver.getSignal(cmd = 9, sig = 0, note = 68, velocity = 100)
		verify { receiveListener.onPadTouch(7, 4, true, 100) }
	}

	@Test
	fun getSignal_noteOn_upperRange_lastNote() {
		// note=99: x=(99-99)/4+1=1, y=8-(99-99)%4=8 → pad(0, 7)
		driver.getSignal(cmd = 9, sig = 0, note = 99, velocity = 80)
		verify { receiveListener.onPadTouch(0, 7, true, 80) }
	}

	// --- getSignal: note-off (cmd=8) ---

	@Test
	fun getSignal_noteOff_lowerRange() {
		// cmd=8 → upDown=false
		driver.getSignal(cmd = 8, sig = 0, note = 36, velocity = 0)
		verify { receiveListener.onPadTouch(7, 0, false, 0) }
	}

	@Test
	fun getSignal_noteOff_upperRange() {
		driver.getSignal(cmd = 8, sig = 0, note = 99, velocity = 0)
		verify { receiveListener.onPadTouch(0, 7, false, 0) }
	}

	// --- getSignal: out of range ---

	@Test
	fun getSignal_noteOutOfRange_noAction() {
		driver.getSignal(cmd = 9, sig = 0, note = 35, velocity = 100)
		verify(exactly = 0) { receiveListener.onPadTouch(any(), any(), any(), any()) }
	}

	@Test
	fun getSignal_noteAboveRange_noAction() {
		driver.getSignal(cmd = 9, sig = 0, note = 100, velocity = 100)
		verify(exactly = 0) { receiveListener.onPadTouch(any(), any(), any(), any()) }
	}

	@Test
	fun getSignal_unknownCommand_noAction() {
		driver.getSignal(cmd = 11, sig = 0, note = 50, velocity = 100)
		verify(exactly = 0) { receiveListener.onPadTouch(any(), any(), any(), any()) }
	}

	// --- sendPadLed ---

	@Test
	fun sendPadLed_lowerRow() {
		// (0, 0): padX=1, padY=1 (in 1..4) → note = -4*1+1+67 = 64
		driver.sendPadLed(0, 0, 60)
		verify { sendListener.onSend(9.toByte(), (-110).toByte(), 64.toByte(), 60.toByte()) }
	}

	@Test
	fun sendPadLed_upperRow() {
		// (0, 4): padX=1, padY=5 (in 5..8) → note = -4*1+5+95 = 96
		driver.sendPadLed(0, 4, 60)
		verify { sendListener.onSend(9.toByte(), (-110).toByte(), 96.toByte(), 60.toByte()) }
	}

	@Test
	fun sendPadLed_bottomRightLower() {
		// (7, 3): padX=8, padY=4 (in 1..4) → note = -4*8+4+67 = 39
		driver.sendPadLed(7, 3, 127)
		verify { sendListener.onSend(9.toByte(), (-110).toByte(), 39.toByte(), 127.toByte()) }
	}

	@Test
	fun sendPadLed_bottomRightUpper() {
		// (7, 7): padX=8, padY=8 (in 5..8) → note = -4*8+8+95 = 71
		driver.sendPadLed(7, 7, 127)
		verify { sendListener.onSend(9.toByte(), (-110).toByte(), 71.toByte(), 127.toByte()) }
	}

	// --- no chain/function key support ---

	@Test
	fun sendChainLed_noOp() {
		driver.sendChainLed(0, 60)
		verify(exactly = 0) { sendListener.onSend(any(), any(), any(), any()) }
	}

	@Test
	fun sendFunctionKeyLed_noOp() {
		driver.sendFunctionKeyLed(0, 60)
		verify(exactly = 0) { sendListener.onSend(any(), any(), any(), any()) }
	}
}
