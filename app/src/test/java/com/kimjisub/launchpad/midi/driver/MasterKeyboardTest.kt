package com.kimjisub.launchpad.midi.driver

import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test

class MasterKeyboardTest {

	private lateinit var driver: MasterKeyboard
	private lateinit var receiveListener: DriverRef.OnReceiveSignalListener
	private lateinit var sendListener: DriverRef.OnSendSignalListener

	@Before
	fun setUp() {
		driver = MasterKeyboard()
		receiveListener = mockk(relaxed = true)
		sendListener = mockk(relaxed = true)
		driver.setOnGetSignalListener(receiveListener)
		driver.setOnSendSignalListener(sendListener)
	}

	// --- getSignal: note-on (cmd=9, velocity!=0) ---

	@Test
	fun getSignal_noteOn_lowerRange_firstNote() {
		// note=36: x=(67-36)/4+1=8, y=4-(67-36)%4=1 → pad(7, 0, true)
		driver.getSignal(cmd = 9, sig = 0, note = 36, velocity = 100)
		verify { receiveListener.onPadTouch(7, 0, true, 100) }
	}

	@Test
	fun getSignal_noteOn_lowerRange_lastNote() {
		// note=67: x=1, y=4 → pad(0, 3, true)
		driver.getSignal(cmd = 9, sig = 0, note = 67, velocity = 80)
		verify { receiveListener.onPadTouch(0, 3, true, 80) }
	}

	@Test
	fun getSignal_noteOn_upperRange_firstNote() {
		// note=68: x=8, y=5 → pad(7, 4, true)
		driver.getSignal(cmd = 9, sig = 0, note = 68, velocity = 100)
		verify { receiveListener.onPadTouch(7, 4, true, 100) }
	}

	@Test
	fun getSignal_noteOn_upperRange_lastNote() {
		// note=99: x=1, y=8 → pad(0, 7, true)
		driver.getSignal(cmd = 9, sig = 0, note = 99, velocity = 80)
		verify { receiveListener.onPadTouch(0, 7, true, 80) }
	}

	// --- getSignal: note-on with velocity==0 means release ---

	@Test
	fun getSignal_cmd9_velocityZero_treatedAsRelease() {
		// cmd=9 with velocity=0 → upDown = (velocity != 0) = false
		driver.getSignal(cmd = 9, sig = 0, note = 36, velocity = 0)
		verify { receiveListener.onPadTouch(7, 0, false, 0) }
	}

	// --- getSignal: note-off via velocity==0 with non-cmd-9 ---

	@Test
	fun getSignal_nonCmd9_velocityZero_lowerRange() {
		// cmd=10, velocity=0 → note-off path
		driver.getSignal(cmd = 10, sig = 0, note = 36, velocity = 0)
		verify { receiveListener.onPadTouch(7, 0, false, 0) }
	}

	@Test
	fun getSignal_nonCmd9_velocityZero_upperRange() {
		driver.getSignal(cmd = 10, sig = 0, note = 99, velocity = 0)
		verify { receiveListener.onPadTouch(0, 7, false, 0) }
	}

	// --- getSignal: non-cmd-9 with non-zero velocity → no action ---

	@Test
	fun getSignal_nonCmd9_nonZeroVelocity_noAction() {
		driver.getSignal(cmd = 10, sig = 0, note = 50, velocity = 100)
		verify(exactly = 0) { receiveListener.onPadTouch(any(), any(), any(), any()) }
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

	// --- send methods are all no-op ---

	@Test
	fun sendPadLed_noOp() {
		driver.sendPadLed(0, 0, 60)
		verify(exactly = 0) { sendListener.onSend(any(), any(), any(), any()) }
	}

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

	@Test
	fun sendClearLed_noOp() {
		driver.sendClearLed()
		verify(exactly = 0) { sendListener.onSend(any(), any(), any(), any()) }
	}
}
