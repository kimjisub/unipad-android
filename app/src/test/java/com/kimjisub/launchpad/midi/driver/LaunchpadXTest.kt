package com.kimjisub.launchpad.midi.driver

import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test

class LaunchpadXTest {

	private lateinit var driver: LaunchpadX
	private lateinit var receiveListener: DriverRef.OnReceiveSignalListener
	private lateinit var sendListener: DriverRef.OnSendSignalListener

	@Before
	fun setUp() {
		driver = LaunchpadX()
		receiveListener = mockk(relaxed = true)
		sendListener = mockk(relaxed = true)
		driver.setOnGetSignalListener(receiveListener)
		driver.setOnSendSignalListener(sendListener)
	}

	// --- getSignal: pad touch (uses cmd=25, not cmd=9) ---

	@Test
	fun getSignal_padTouch_topLeftCorner() {
		// cmd=25, note=81: x=9-8=1, y=1 → pad(0, 0)
		driver.getSignal(cmd = 25, sig = 0, note = 81, velocity = 100)
		verify { receiveListener.onPadTouch(0, 0, true, 100) }
	}

	@Test
	fun getSignal_padTouch_bottomRightCorner() {
		// cmd=25, note=18: x=9-1=8, y=8 → pad(7, 7)
		driver.getSignal(cmd = 25, sig = 0, note = 18, velocity = 80)
		verify { receiveListener.onPadTouch(7, 7, true, 80) }
	}

	@Test
	fun getSignal_padTouch_cmd9DoesNotWork() {
		// cmd=9 should NOT trigger pad touch on LaunchpadX (uses cmd=25)
		driver.getSignal(cmd = 9, sig = 0, note = 81, velocity = 100)
		verify(exactly = 0) { receiveListener.onPadTouch(any(), any(), any(), any()) }
	}

	// --- getSignal: function keys (uses cmd=27, not cmd=11) ---

	@Test
	fun getSignal_topFunctionKey() {
		// cmd=27, sig=-80, note=91 → functionKey(0)
		driver.getSignal(cmd = 27, sig = -80, note = 91, velocity = 127)
		verify { receiveListener.onFunctionKeyTouch(0, true) }
	}

	// --- getSignal: right side chain ---

	@Test
	fun getSignal_rightSideChain() {
		// cmd=27, sig=-80, note=89: c = 9 - 8 - 1 = 0 → chain(0) + functionKey(8)
		driver.getSignal(cmd = 27, sig = -80, note = 89, velocity = 127)
		verify { receiveListener.onChainTouch(0, true) }
		verify { receiveListener.onFunctionKeyTouch(8, true) }
	}

	// --- getSignal: bottom chain ---

	@Test
	fun getSignal_bottomChain() {
		// cmd=27, sig=-80, note=1: chain = 8 - 1 + 16 - 8 = 15, fkey = 8 - 1 + 16 = 23
		driver.getSignal(cmd = 27, sig = -80, note = 1, velocity = 127)
		verify { receiveListener.onChainTouch(15, true) }
		verify { receiveListener.onFunctionKeyTouch(23, true) }
	}

	// --- getSignal: left side chain ---

	@Test
	fun getSignal_leftSideChain() {
		// cmd=27, sig=-80, note=80: chain = 80/10 - 1 + 24 - 8 = 23, fkey = 80/10 - 1 + 24 = 31
		driver.getSignal(cmd = 27, sig = -80, note = 80, velocity = 127)
		verify { receiveListener.onChainTouch(23, true) }
		verify { receiveListener.onFunctionKeyTouch(31, true) }
	}

	// --- sendPadLed (uses cmd=25) ---

	@Test
	fun sendPadLed_topLeftCorner() {
		// (0,0) → cmd=25, note = 10*(8-0) + 0 + 1 = 81
		driver.sendPadLed(0, 0, 60)
		verify { sendListener.onSend(25.toByte(), (-112).toByte(), 81.toByte(), 60.toByte()) }
	}

	@Test
	fun sendPadLed_bottomRightCorner() {
		// (7,7) → cmd=25, note = 10*(8-7) + 7 + 1 = 18
		driver.sendPadLed(7, 7, 127)
		verify { sendListener.onSend(25.toByte(), (-112).toByte(), 18.toByte(), 127.toByte()) }
	}

	// --- sendFunctionKeyLed (uses cmd=27) ---

	@Test
	fun sendFunctionKeyLed_topRow() {
		// f=0 → circleCode[0] = {27, -80, 91}
		driver.sendFunctionKeyLed(0, 45)
		verify { sendListener.onSend(27.toByte(), (-80).toByte(), 91.toByte(), 45.toByte()) }
	}

	// --- boundary ---

	@Test
	fun getSignal_unknownCmd_callsUnknownReceived() {
		driver.getSignal(cmd = 99, sig = 0, note = 0, velocity = 0)
		verify { receiveListener.onUnknownReceived(99, 0, 0, 0) }
	}

	@Test
	fun sendFunctionKeyLed_outOfRange_doesNotSend() {
		driver.sendFunctionKeyLed(32, 60)
		verify(exactly = 0) { sendListener.onSend(any(), any(), any(), any()) }
	}
}
