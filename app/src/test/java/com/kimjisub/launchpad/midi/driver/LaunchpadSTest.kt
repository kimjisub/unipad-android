package com.kimjisub.launchpad.midi.driver

import com.kimjisub.launchpad.manager.LaunchpadColor
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test

class LaunchpadSTest {

	private lateinit var driver: LaunchpadS
	private lateinit var receiveListener: DriverRef.OnReceiveSignalListener
	private lateinit var sendListener: DriverRef.OnSendSignalListener

	@Before
	fun setUp() {
		driver = LaunchpadS()
		receiveListener = mockk(relaxed = true)
		sendListener = mockk(relaxed = true)
		driver.setOnGetSignalListener(receiveListener)
		driver.setOnSendSignalListener(sendListener)
	}

	// --- getSignal: pad touch (cmd=9) ---

	@Test
	fun getSignal_padTouch_topLeftCorner() {
		// note=0: x=0/16+1=1, y=0%16+1=1 → pad(0, 0)
		driver.getSignal(cmd = 9, sig = 0, note = 0, velocity = 100)
		verify { receiveListener.onPadTouch(0, 0, true, 100) }
	}

	@Test
	fun getSignal_padTouch_bottomRightCorner() {
		// note=7*16+7=119: x=119/16+1=8, y=119%16+1=8 → pad(7, 7)
		driver.getSignal(cmd = 9, sig = 0, note = 119, velocity = 80)
		verify { receiveListener.onPadTouch(7, 7, true, 80) }
	}

	@Test
	fun getSignal_padTouch_releaseWithZeroVelocity() {
		driver.getSignal(cmd = 9, sig = 0, note = 0, velocity = 0)
		verify { receiveListener.onPadTouch(0, 0, false, 0) }
	}

	@Test
	fun getSignal_padTouch_middlePad() {
		// note=3*16+4=52: x=52/16+1=4, y=52%16+1=5 → pad(3, 4)
		driver.getSignal(cmd = 9, sig = 0, note = 52, velocity = 64)
		verify { receiveListener.onPadTouch(3, 4, true, 64) }
	}

	// --- getSignal: chain + function key (cmd=9, y==9) ---

	@Test
	fun getSignal_rightSideButton_triggersChainAndFunctionKey() {
		// note with y=9: note=0*16+8=8 → x=8/16+1=1, y=8%16+1=9
		// chain(0) + functionKey(8)
		driver.getSignal(cmd = 9, sig = 0, note = 8, velocity = 127)
		verify { receiveListener.onChainTouch(0, true) }
		verify { receiveListener.onFunctionKeyTouch(8, true) }
	}

	@Test
	fun getSignal_rightSideButton_lastChain() {
		// note=7*16+8=120: x=120/16+1=8, y=120%16+1=9
		// chain(7) + functionKey(15)
		driver.getSignal(cmd = 9, sig = 0, note = 120, velocity = 127)
		verify { receiveListener.onChainTouch(7, true) }
		verify { receiveListener.onFunctionKeyTouch(15, true) }
	}

	@Test
	fun getSignal_rightSideButton_release() {
		driver.getSignal(cmd = 9, sig = 0, note = 8, velocity = 0)
		verify { receiveListener.onChainTouch(0, false) }
		verify { receiveListener.onFunctionKeyTouch(8, false) }
	}

	// --- getSignal: top row function keys (cmd=11) ---

	@Test
	fun getSignal_topFunctionKey_first() {
		// cmd=11, note=104 → functionKey(0)
		driver.getSignal(cmd = 11, sig = 0, note = 104, velocity = 127)
		verify { receiveListener.onFunctionKeyTouch(0, true) }
	}

	@Test
	fun getSignal_topFunctionKey_last() {
		// cmd=11, note=111 → functionKey(7)
		driver.getSignal(cmd = 11, sig = 0, note = 111, velocity = 127)
		verify { receiveListener.onFunctionKeyTouch(7, true) }
	}

	@Test
	fun getSignal_topFunctionKey_release() {
		driver.getSignal(cmd = 11, sig = 0, note = 104, velocity = 0)
		verify { receiveListener.onFunctionKeyTouch(0, false) }
	}

	// --- sendPadLed ---

	@Test
	fun sendPadLed_topLeftCorner() {
		// (0,0) → note = 0*16+0 = 0, velocity = SCode[60]
		driver.sendPadLed(0, 0, 60)
		verify { sendListener.onSend(9.toByte(), (-112).toByte(), 0.toByte(), LaunchpadColor.SCode[60].toByte()) }
	}

	@Test
	fun sendPadLed_bottomRightCorner() {
		// (7,7) → note = 7*16+7 = 119, velocity = SCode[127]
		driver.sendPadLed(7, 7, 127)
		verify { sendListener.onSend(9.toByte(), (-112).toByte(), 119.toByte(), LaunchpadColor.SCode[127].toByte()) }
	}

	// --- sendChainLed ---

	@Test
	fun sendChainLed_routesToFunctionKey() {
		// chain 0 → sendFunctionKeyLed(8, velocity)
		// circleCode[8] = {9, -112, 8}, velocity = SCode[60]
		driver.sendChainLed(0, 60)
		verify {
			sendListener.onSend(
				LaunchpadS.circleCode[8][0].toByte(),
				LaunchpadS.circleCode[8][1].toByte(),
				LaunchpadS.circleCode[8][2].toByte(),
				LaunchpadColor.SCode[60].toByte()
			)
		}
	}

	@Test
	fun sendChainLed_outOfRange_doesNotSend() {
		driver.sendChainLed(8, 60)
		verify(exactly = 0) { sendListener.onSend(any(), any(), any(), any()) }
	}

	// --- sendFunctionKeyLed ---

	@Test
	fun sendFunctionKeyLed_topRow() {
		// f=0 → circleCode[0] = {11, -80, 104}
		driver.sendFunctionKeyLed(0, 45)
		verify {
			sendListener.onSend(
				11.toByte(),
				(-80).toByte(),
				104.toByte(),
				LaunchpadColor.SCode[45].toByte()
			)
		}
	}

	@Test
	fun sendFunctionKeyLed_outOfRange_doesNotSend() {
		driver.sendFunctionKeyLed(16, 60)
		verify(exactly = 0) { sendListener.onSend(any(), any(), any(), any()) }
	}

	// --- boundary ---

	@Test
	fun getSignal_unknownCommand_noPadTouch() {
		driver.getSignal(cmd = 99, sig = 0, note = 0, velocity = 100)
		verify(exactly = 0) { receiveListener.onPadTouch(any(), any(), any(), any()) }
	}

	@Test
	fun getSignal_cmd11_noteOutOfRange_noFunctionKey() {
		driver.getSignal(cmd = 11, sig = 0, note = 103, velocity = 127)
		verify(exactly = 0) { receiveListener.onFunctionKeyTouch(any(), any()) }
	}
}
