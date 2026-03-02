package com.kimjisub.launchpad.midi.driver

import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test

class LaunchpadMK3Test {

	private lateinit var driver: LaunchpadMK3
	private lateinit var receiveListener: DriverRef.OnReceiveSignalListener
	private lateinit var sendListener: DriverRef.OnSendSignalListener

	@Before
	fun setUp() {
		driver = LaunchpadMK3()
		receiveListener = mockk(relaxed = true)
		sendListener = mockk(relaxed = true)
		driver.setOnGetSignalListener(receiveListener)
		driver.setOnSendSignalListener(sendListener)
	}

	// --- getSignal: pad touch (cmd=9) ---

	@Test
	fun getSignal_padTouch_topLeftCorner() {
		// note=81: x=9-81/10=9-8=1, y=81%10=1 → pad(0, 0)
		driver.getSignal(cmd = 9, sig = 0, note = 81, velocity = 100)
		verify { receiveListener.onPadTouch(0, 0, true, 100) }
	}

	@Test
	fun getSignal_padTouch_bottomRightCorner() {
		// note=18: x=9-18/10=9-1=8, y=18%10=8 → pad(7, 7)
		driver.getSignal(cmd = 9, sig = 0, note = 18, velocity = 80)
		verify { receiveListener.onPadTouch(7, 7, true, 80) }
	}

	@Test
	fun getSignal_padTouch_releaseWithZeroVelocity() {
		driver.getSignal(cmd = 9, sig = 0, note = 81, velocity = 0)
		verify { receiveListener.onPadTouch(0, 0, false, 0) }
	}

	@Test
	fun getSignal_padTouch_invalidY_noAction() {
		// note=80: y=80%10=0, not in 1..8 → no pad touch
		driver.getSignal(cmd = 9, sig = 0, note = 80, velocity = 100)
		verify(exactly = 0) { receiveListener.onPadTouch(any(), any(), any(), any()) }
	}

	// --- getSignal: top function keys (cmd=11, sig=-80, note 91-98) ---

	@Test
	fun getSignal_topFunctionKey_first() {
		// note=91 → functionKey(0)
		driver.getSignal(cmd = 11, sig = -80, note = 91, velocity = 127)
		verify { receiveListener.onFunctionKeyTouch(0, true) }
	}

	@Test
	fun getSignal_topFunctionKey_last() {
		// note=98 → functionKey(7)
		driver.getSignal(cmd = 11, sig = -80, note = 98, velocity = 127)
		verify { receiveListener.onFunctionKeyTouch(7, true) }
	}

	@Test
	fun getSignal_topFunctionKey_release() {
		driver.getSignal(cmd = 11, sig = -80, note = 91, velocity = 0)
		verify { receiveListener.onFunctionKeyTouch(0, false) }
	}

	// --- getSignal: right side chain (note in 19..89, note%10==9) ---

	@Test
	fun getSignal_rightSideChain_first() {
		// note=89: c=9-89/10-1=9-8-1=0 → chain(0) + functionKey(8)
		driver.getSignal(cmd = 11, sig = -80, note = 89, velocity = 127)
		verify { receiveListener.onChainTouch(0, true) }
		verify { receiveListener.onFunctionKeyTouch(8, true) }
	}

	@Test
	fun getSignal_rightSideChain_last() {
		// note=19: c=9-19/10-1=9-1-1=7 → chain(7) + functionKey(15)
		driver.getSignal(cmd = 11, sig = -80, note = 19, velocity = 127)
		verify { receiveListener.onChainTouch(7, true) }
		verify { receiveListener.onFunctionKeyTouch(15, true) }
	}

	// --- getSignal: bottom row chain (note in 1..8) ---

	@Test
	fun getSignal_bottomChain_note8() {
		// note=8: chain = 16-8 = 8, fkey = 24-8 = 16
		driver.getSignal(cmd = 11, sig = -80, note = 8, velocity = 127)
		verify { receiveListener.onChainTouch(8, true) }
		verify { receiveListener.onFunctionKeyTouch(16, true) }
	}

	@Test
	fun getSignal_bottomChain_note1() {
		// note=1: chain = 16-1 = 15, fkey = 24-1 = 23
		driver.getSignal(cmd = 11, sig = -80, note = 1, velocity = 127)
		verify { receiveListener.onChainTouch(15, true) }
		verify { receiveListener.onFunctionKeyTouch(23, true) }
	}

	// --- getSignal: left side chain (note in 10..80, note%10==0) ---

	@Test
	fun getSignal_leftSideChain_note10() {
		// note=10: chain = 10/10+15 = 16, fkey = 10/10+23 = 24
		driver.getSignal(cmd = 11, sig = -80, note = 10, velocity = 127)
		verify { receiveListener.onChainTouch(16, true) }
		verify { receiveListener.onFunctionKeyTouch(24, true) }
	}

	@Test
	fun getSignal_leftSideChain_note80() {
		// note=80: chain = 80/10+15 = 23, fkey = 80/10+23 = 31
		driver.getSignal(cmd = 11, sig = -80, note = 80, velocity = 127)
		verify { receiveListener.onChainTouch(23, true) }
		verify { receiveListener.onFunctionKeyTouch(31, true) }
	}

	// --- getSignal: unknown / mode detection ---

	@Test
	fun getSignal_unknownSignal_callsUnknownReceived() {
		driver.getSignal(cmd = 99, sig = 0, note = 0, velocity = 0)
		verify { receiveListener.onUnknownReceived(99, 0, 0, 0) }
	}

	// --- sendPadLed ---

	@Test
	fun sendPadLed_topLeftCorner() {
		// (0,0) → note = 10*(8-0)+0+1 = 81
		driver.sendPadLed(0, 0, 60)
		verify { sendListener.onSend(9.toByte(), (-112).toByte(), 81.toByte(), 60.toByte()) }
	}

	@Test
	fun sendPadLed_bottomRightCorner() {
		// (7,7) → note = 10*(8-7)+7+1 = 18
		driver.sendPadLed(7, 7, 127)
		verify { sendListener.onSend(9.toByte(), (-112).toByte(), 18.toByte(), 127.toByte()) }
	}

	// --- sendChainLed ---

	@Test
	fun sendChainLed_routesToFunctionKey() {
		// chain 0 → sendFunctionKeyLed(8, velocity) → circleCode[8] = {11, -80, 89}
		driver.sendChainLed(0, 60)
		verify { sendListener.onSend(11.toByte(), (-80).toByte(), 89.toByte(), 60.toByte()) }
	}

	@Test
	fun sendChainLed_outOfRange_doesNotSend() {
		driver.sendChainLed(8, 60)
		verify(exactly = 0) { sendListener.onSend(any(), any(), any(), any()) }
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

	@Test
	fun sendFunctionKeyLed_outOfRange_doesNotSend() {
		driver.sendFunctionKeyLed(32, 60)
		verify(exactly = 0) { sendListener.onSend(any(), any(), any(), any()) }
	}

	// --- circleCode ---

	@Test
	fun circleCode_has32Entries() {
		assert(LaunchpadMK3.circleCode.size == 32)
	}
}
