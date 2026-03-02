package com.kimjisub.launchpad.midi.driver

import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test

class LaunchpadPROTest {

	private lateinit var driver: LaunchpadPRO
	private lateinit var receiveListener: DriverRef.OnReceiveSignalListener
	private lateinit var sendListener: DriverRef.OnSendSignalListener

	@Before
	fun setUp() {
		driver = LaunchpadPRO()
		receiveListener = mockk(relaxed = true)
		sendListener = mockk(relaxed = true)
		driver.setOnGetSignalListener(receiveListener)
		driver.setOnSendSignalListener(sendListener)
	}

	// --- getSignal: pad touch ---

	@Test
	fun getSignal_padTouch_topLeftCorner() {
		// cmd=9, note=81: x=9-8=1, y=1 → pad(0, 0)
		driver.getSignal(cmd = 9, sig = 0, note = 81, velocity = 100)
		verify { receiveListener.onPadTouch(0, 0, true, 100) }
	}

	@Test
	fun getSignal_padTouch_bottomRightCorner() {
		// cmd=9, note=18: x=9-1=8, y=8 → pad(7, 7)
		driver.getSignal(cmd = 9, sig = 0, note = 18, velocity = 80)
		verify { receiveListener.onPadTouch(7, 7, true, 80) }
	}

	// --- getSignal: function keys (top row) ---

	@Test
	fun getSignal_topFunctionKey_first() {
		// cmd=11, sig=-80, note=91 → functionKey(0)
		driver.getSignal(cmd = 11, sig = -80, note = 91, velocity = 127)
		verify { receiveListener.onFunctionKeyTouch(0, true) }
	}

	@Test
	fun getSignal_topFunctionKey_last() {
		// cmd=11, sig=-80, note=98 → functionKey(7)
		driver.getSignal(cmd = 11, sig = -80, note = 98, velocity = 127)
		verify { receiveListener.onFunctionKeyTouch(7, true) }
	}

	// --- getSignal: right side chain ---

	@Test
	fun getSignal_rightSideChain_first() {
		// note=89: c = 9 - 8 - 1 = 0 → chain(0) + functionKey(8)
		driver.getSignal(cmd = 11, sig = -80, note = 89, velocity = 127)
		verify { receiveListener.onChainTouch(0, true) }
		verify { receiveListener.onFunctionKeyTouch(8, true) }
	}

	@Test
	fun getSignal_rightSideChain_last() {
		// note=19: c = 9 - 1 - 1 = 7 → chain(7) + functionKey(15)
		driver.getSignal(cmd = 11, sig = -80, note = 19, velocity = 127)
		verify { receiveListener.onChainTouch(7, true) }
		verify { receiveListener.onFunctionKeyTouch(15, true) }
	}

	// --- getSignal: bottom chain ---

	@Test
	fun getSignal_bottomChain() {
		// note=8: chain = 8 - 8 + 16 - 8 = 8, fkey = 8 - 8 + 16 = 16
		driver.getSignal(cmd = 11, sig = -80, note = 8, velocity = 127)
		verify { receiveListener.onChainTouch(8, true) }
		verify { receiveListener.onFunctionKeyTouch(16, true) }
	}

	// --- getSignal: left side chain ---

	@Test
	fun getSignal_leftSideChain() {
		// note=10: chain = 10/10 - 1 + 24 - 8 = 16, fkey = 10/10 - 1 + 24 = 24
		driver.getSignal(cmd = 11, sig = -80, note = 10, velocity = 127)
		verify { receiveListener.onChainTouch(16, true) }
		verify { receiveListener.onFunctionKeyTouch(24, true) }
	}

	// --- sendPadLed ---

	@Test
	fun sendPadLed_topLeftCorner() {
		// (0,0) → note = 10*(8-0) + 0 + 1 = 81
		driver.sendPadLed(0, 0, 60)
		verify { sendListener.onSend(9.toByte(), (-112).toByte(), 81.toByte(), 60.toByte()) }
	}

	// --- sendFunctionKeyLed ---

	@Test
	fun sendFunctionKeyLed_topRow() {
		// f=0 → circleCode[0] = {11, -80, 91}
		driver.sendFunctionKeyLed(0, 45)
		verify { sendListener.onSend(11.toByte(), (-80).toByte(), 91.toByte(), 45.toByte()) }
	}

	@Test
	fun sendFunctionKeyLed_rightSide() {
		// f=8 → circleCode[8] = {11, -80, 89}
		driver.sendFunctionKeyLed(8, 60)
		verify { sendListener.onSend(11.toByte(), (-80).toByte(), 89.toByte(), 60.toByte()) }
	}

	// --- boundary ---

	@Test
	fun sendFunctionKeyLed_outOfRange_doesNotSend() {
		driver.sendFunctionKeyLed(32, 60)
		verify(exactly = 0) { sendListener.onSend(any(), any(), any(), any()) }
	}

	@Test
	fun getSignal_unknownSignal_callsUnknownReceived() {
		driver.getSignal(cmd = 99, sig = 0, note = 0, velocity = 0)
		verify { receiveListener.onUnknownReceived(99, 0, 0, 0) }
	}
}
