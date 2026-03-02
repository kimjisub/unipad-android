package com.kimjisub.launchpad.midi.driver

import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test

class MatrixTest {

	private lateinit var driver: Matrix
	private lateinit var receiveListener: DriverRef.OnReceiveSignalListener
	private lateinit var sendListener: DriverRef.OnSendSignalListener

	@Before
	fun setUp() {
		driver = Matrix()
		receiveListener = mockk(relaxed = true)
		sendListener = mockk(relaxed = true)
		driver.setOnGetSignalListener(receiveListener)
		driver.setOnSendSignalListener(sendListener)
	}

	// --- getSignal: note-on (cmd=9) pad touch, lower range 36-67 ---

	@Test
	fun getSignal_noteOn_lowerRange_firstNote() {
		// note=36: x=(67-36)/4+1=8, y=4-(67-36)%4=1 → pad(7, 0)
		driver.getSignal(cmd = 9, sig = 0, note = 36, velocity = 100)
		verify { receiveListener.onPadTouch(7, 0, true, 100) }
	}

	@Test
	fun getSignal_noteOn_lowerRange_lastNote() {
		// note=67: x=(67-67)/4+1=1, y=4-0=4 → pad(0, 3)
		driver.getSignal(cmd = 9, sig = 0, note = 67, velocity = 80)
		verify { receiveListener.onPadTouch(0, 3, true, 80) }
	}

	// --- getSignal: note-on (cmd=9) pad touch, upper range 68-99 ---

	@Test
	fun getSignal_noteOn_upperRange_firstNote() {
		// note=68: x=(99-68)/4+1=8, y=8-(99-68)%4=5 → pad(7, 4)
		driver.getSignal(cmd = 9, sig = 0, note = 68, velocity = 100)
		verify { receiveListener.onPadTouch(7, 4, true, 100) }
	}

	@Test
	fun getSignal_noteOn_upperRange_lastNote() {
		// note=99: x=1, y=8 → pad(0, 7)
		driver.getSignal(cmd = 9, sig = 0, note = 99, velocity = 80)
		verify { receiveListener.onPadTouch(0, 7, true, 80) }
	}

	// --- getSignal: note-on (cmd=9) chain buttons (100-107) ---

	@Test
	fun getSignal_chainButton_first() {
		// note=100: c=0 → chain(0) + functionKey(8)
		driver.getSignal(cmd = 9, sig = 0, note = 100, velocity = 127)
		verify { receiveListener.onChainTouch(0, true) }
		verify { receiveListener.onFunctionKeyTouch(8, true) }
	}

	@Test
	fun getSignal_chainButton_last() {
		// note=107: c=7 → chain(7) + functionKey(15)
		driver.getSignal(cmd = 9, sig = 0, note = 107, velocity = 127)
		verify { receiveListener.onChainTouch(7, true) }
		verify { receiveListener.onFunctionKeyTouch(15, true) }
	}

	@Test
	fun getSignal_chainButton_release() {
		driver.getSignal(cmd = 9, sig = 0, note = 100, velocity = 0)
		verify { receiveListener.onChainTouch(0, false) }
		verify { receiveListener.onFunctionKeyTouch(8, false) }
	}

	// --- getSignal: note-on (cmd=9) extra function keys (108-115) ---

	@Test
	fun getSignal_extraFunctionKey_first() {
		// note=108: c=8-(108-108)+8=16 → functionKey(16+8=24)
		driver.getSignal(cmd = 9, sig = 0, note = 108, velocity = 127)
		verify { receiveListener.onFunctionKeyTouch(24, true) }
	}

	@Test
	fun getSignal_extraFunctionKey_last() {
		// note=115: c=8-(115-108)+8=9 → functionKey(9+8=17)
		driver.getSignal(cmd = 9, sig = 0, note = 115, velocity = 127)
		verify { receiveListener.onFunctionKeyTouch(17, true) }
	}

	// --- getSignal: note-off (cmd=8) ---

	@Test
	fun getSignal_noteOff_lowerRange() {
		driver.getSignal(cmd = 8, sig = 0, note = 36, velocity = 0)
		verify { receiveListener.onPadTouch(7, 0, false, 0) }
	}

	@Test
	fun getSignal_noteOff_upperRange() {
		driver.getSignal(cmd = 8, sig = 0, note = 99, velocity = 0)
		verify { receiveListener.onPadTouch(0, 7, false, 0) }
	}

	// --- sendPadLed ---

	@Test
	fun sendPadLed_lowerRow() {
		// (0, 0): padX=1, padY=1 (in 1..4) → note = -4*1+1+67 = 64, sig=-111
		driver.sendPadLed(0, 0, 60)
		verify { sendListener.onSend(9.toByte(), (-111).toByte(), 64.toByte(), 60.toByte()) }
	}

	@Test
	fun sendPadLed_upperRow() {
		// (0, 4): padX=1, padY=5 (in 5..8) → note = -4*1+5+95 = 96, sig=-111
		driver.sendPadLed(0, 4, 60)
		verify { sendListener.onSend(9.toByte(), (-111).toByte(), 96.toByte(), 60.toByte()) }
	}

	// --- sendChainLed ---

	@Test
	fun sendChainLed_routesToFunctionKey() {
		// chain 0 → sendFunctionKeyLed(8, velocity) → circleCode[8] = {9, -111, 100}
		driver.sendChainLed(0, 60)
		verify { sendListener.onSend(9.toByte(), (-111).toByte(), 100.toByte(), 60.toByte()) }
	}

	@Test
	fun sendChainLed_outOfRange_doesNotSend() {
		driver.sendChainLed(8, 60)
		verify(exactly = 0) { sendListener.onSend(any(), any(), any(), any()) }
	}

	// --- sendFunctionKeyLed ---

	@Test
	fun sendFunctionKeyLed_first() {
		// f=0 → circleCode[0] = {9, -111, 28}
		driver.sendFunctionKeyLed(0, 45)
		verify { sendListener.onSend(9.toByte(), (-111).toByte(), 28.toByte(), 45.toByte()) }
	}

	@Test
	fun sendFunctionKeyLed_outOfRange_doesNotSend() {
		driver.sendFunctionKeyLed(32, 60)
		verify(exactly = 0) { sendListener.onSend(any(), any(), any(), any()) }
	}

	// --- boundary ---

	@Test
	fun getSignal_unknownCommand_noAction() {
		driver.getSignal(cmd = 11, sig = 0, note = 50, velocity = 100)
		verify(exactly = 0) { receiveListener.onPadTouch(any(), any(), any(), any()) }
	}

	// --- circleCode ---

	@Test
	fun circleCode_has32Entries() {
		assert(Matrix.circleCode.size == 32)
	}
}
