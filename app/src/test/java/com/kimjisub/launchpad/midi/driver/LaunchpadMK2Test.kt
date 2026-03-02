package com.kimjisub.launchpad.midi.driver

import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test

class LaunchpadMK2Test {

	private lateinit var driver: LaunchpadMK2
	private lateinit var receiveListener: DriverRef.OnReceiveSignalListener
	private lateinit var sendListener: DriverRef.OnSendSignalListener

	@Before
	fun setUp() {
		driver = LaunchpadMK2()
		receiveListener = mockk(relaxed = true)
		sendListener = mockk(relaxed = true)
		driver.setOnGetSignalListener(receiveListener)
		driver.setOnSendSignalListener(sendListener)
	}

	// --- getSignal: pad touch ---

	@Test
	fun getSignal_padTouch_topLeftCorner() {
		// note=81: x=9-8=1, y=1 → pad(0, 0)
		driver.getSignal(cmd = 9, sig = 0, note = 81, velocity = 100)
		verify { receiveListener.onPadTouch(0, 0, true, 100) }
	}

	@Test
	fun getSignal_padTouch_bottomRightCorner() {
		// note=18: x=9-1=8, y=8 → pad(7, 7)
		driver.getSignal(cmd = 9, sig = 0, note = 18, velocity = 80)
		verify { receiveListener.onPadTouch(7, 7, true, 80) }
	}

	@Test
	fun getSignal_padTouch_releaseWithZeroVelocity() {
		driver.getSignal(cmd = 9, sig = 0, note = 81, velocity = 0)
		verify { receiveListener.onPadTouch(0, 0, false, 0) }
	}

	// --- getSignal: chain and function keys ---

	@Test
	fun getSignal_rightSideButton_triggersChainAndFunctionKey() {
		// note=89: x=9-8=1, y=9 → chain(0) + functionKey(8)
		driver.getSignal(cmd = 9, sig = 0, note = 89, velocity = 127)
		verify { receiveListener.onChainTouch(0, true) }
		verify { receiveListener.onFunctionKeyTouch(8, true) }
	}

	@Test
	fun getSignal_topRowFunctionKey() {
		// cmd=11, note=104 → functionKey(0)
		driver.getSignal(cmd = 11, sig = 0, note = 104, velocity = 127)
		verify { receiveListener.onFunctionKeyTouch(0, true) }
	}

	@Test
	fun getSignal_topRowFunctionKey_lastKey() {
		// cmd=11, note=111 → functionKey(7)
		driver.getSignal(cmd = 11, sig = 0, note = 111, velocity = 127)
		verify { receiveListener.onFunctionKeyTouch(7, true) }
	}

	// --- sendPadLed ---

	@Test
	fun sendPadLed_topLeftCorner() {
		// (0,0) → note = 10*(8-0) + 0 + 1 = 81
		driver.sendPadLed(0, 0, 60)
		verify { sendListener.onSend(9.toByte(), (-112).toByte(), 81.toByte(), 60.toByte()) }
	}

	@Test
	fun sendPadLed_bottomRightCorner() {
		// (7,7) → note = 10*(8-7) + 7 + 1 = 18
		driver.sendPadLed(7, 7, 127)
		verify { sendListener.onSend(9.toByte(), (-112).toByte(), 18.toByte(), 127.toByte()) }
	}

	// --- sendChainLed ---

	@Test
	fun sendChainLed_routesToFunctionKey() {
		// chain 0 → functionKey(8) → circleCode[8] = {9, -112, 89}
		driver.sendChainLed(0, 60)
		verify { sendListener.onSend(9.toByte(), (-112).toByte(), 89.toByte(), 60.toByte()) }
	}

	// --- sendFunctionKeyLed ---

	@Test
	fun sendFunctionKeyLed_topRow() {
		// f=0 → circleCode[0] = {11, -80, 104}
		driver.sendFunctionKeyLed(0, 45)
		verify { sendListener.onSend(11.toByte(), (-80).toByte(), 104.toByte(), 45.toByte()) }
	}

	// --- boundary / invalid ---

	@Test
	fun getSignal_unknownCommand_noPadTouch() {
		driver.getSignal(cmd = 99, sig = 0, note = 81, velocity = 100)
		verify(exactly = 0) { receiveListener.onPadTouch(any(), any(), any(), any()) }
	}

	@Test
	fun sendChainLed_outOfRange_doesNotSend() {
		driver.sendChainLed(8, 60)
		verify(exactly = 0) { sendListener.onSend(any(), any(), any(), any()) }
	}

	@Test
	fun sendFunctionKeyLed_outOfRange_doesNotSend() {
		driver.sendFunctionKeyLed(16, 60)
		verify(exactly = 0) { sendListener.onSend(any(), any(), any(), any()) }
	}
}
