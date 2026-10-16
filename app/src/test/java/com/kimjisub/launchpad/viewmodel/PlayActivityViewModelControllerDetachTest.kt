package com.kimjisub.launchpad.viewmodel

import com.kimjisub.design.touch.PadTouchTracker
import com.kimjisub.launchpad.analytics.UsageAnalytics
import com.kimjisub.launchpad.db.repository.UnipackRepository
import com.kimjisub.launchpad.manager.ChannelManager
import com.kimjisub.launchpad.manager.ChannelManager.Channel
import com.kimjisub.launchpad.unipack.UniPack
import com.kimjisub.launchpad.unipack.runner.SoundRunner
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * A Launchpad unplugged, or handed off when the play screen pauses, while a pad on it is held sends
 * no release for that pad. The play screen then lets go of it, as PlayActivity does with its own
 * [PadTouchTracker], so a looping sound stops and the pressed light goes out; a pad a finger holds on
 * the screen keeps playing.
 */
class PlayActivityViewModelControllerDetachTest {

	private class GridPack : UniPack() {
		override val id = "controller-detach"
		override val keyLedExist = false

		init {
			buttonX = 8
			buttonY = 8
			chain = 1
		}

		override fun lastModified() = 0L
		override fun loadInfo() = this
		override fun loadDetail() = this
		override fun checkFile() {}
		override fun delete() = false
		override fun getPathString() = ""
		override fun getByteSize() = 0L
	}

	private lateinit var vm: PlayActivityViewModel
	private val sound = mockk<SoundRunner>(relaxed = true)
	private lateinit var screen: PadTouchTracker

	@Before
	fun setUp() {
		vm = PlayActivityViewModel(mockk<UnipackRepository>(), UsageAnalytics { _, _ -> })
		vm.unipack = GridPack()
		vm.channelManager = ChannelManager(8, 8)
		vm.soundRunner = sound
		vm.scbFeedbackLight.setChecked(true)
		screen = PadTouchTracker { x, y, down -> vm.padTouch(x, y, down) }.apply { setGrid(8, 8) }
	}

	private fun unplug() = vm.releaseControllerPads(screen::holds)

	private fun pressedLight(x: Int, y: Int) = vm.channelManager.get(x, y)?.channel

	// 8x8 grid of 100px pads: the centre of pad (x, y) is (y * 100 + 50, x * 100 + 50).
	private fun fingerDown(id: Long, x: Int, y: Int) = screen.down(id, y * 100 + 50f, x * 100 + 50f, 800, 800)

	@Test
	fun unplugWhileHoldingAPad_stopsItsSoundAndTurnsOffItsPressedLight() {
		vm.controllerPadTouch(2, 3, true)
		assertEquals(Channel.PRESSED, pressedLight(2, 3))

		unplug()

		verify(exactly = 1) { sound.soundOff(2, 3, 0) }
		assertNull(pressedLight(2, 3))
	}

	@Test
	fun unplug_leavesAPadAFingerHoldsOnTheScreen() {
		vm.controllerPadTouch(2, 3, true)
		vm.controllerPadTouch(5, 5, true)
		fingerDown(0, 2, 3)

		unplug()

		verify(exactly = 0) { sound.soundOff(2, 3, 0) }
		verify(exactly = 1) { sound.soundOff(5, 5, 0) }
		assertEquals(Channel.PRESSED, pressedLight(2, 3))
		assertNull(pressedLight(5, 5))

		screen.up(0)
		verify(exactly = 1) { sound.soundOff(2, 3, 0) }
		assertNull(pressedLight(2, 3))
	}

	@Test
	fun padPressedAgainAfterReplugging_playsAndReleasesNormally() {
		vm.controllerPadTouch(2, 3, true)
		unplug()

		vm.controllerPadTouch(2, 3, true)
		verify(exactly = 2) { sound.soundOn(2, 3) }
		assertEquals(Channel.PRESSED, pressedLight(2, 3))

		vm.controllerPadTouch(2, 3, false)
		verify(exactly = 2) { sound.soundOff(2, 3, 0) }
		assertNull(pressedLight(2, 3))
	}

	@Test
	fun padReleasedBeforeUnplugging_isNotReleasedAgain() {
		vm.controllerPadTouch(2, 3, true)
		vm.controllerPadTouch(2, 3, false)

		unplug()

		verify(exactly = 1) { sound.soundOff(2, 3, 0) }
	}

	@Test
	fun padReleasedWhileTheOptionWindowIsOpen_isLetGo() {
		vm.controllerPadTouch(2, 3, true)
		vm.toggleOptionWindow(true)

		vm.controllerPadTouch(2, 3, false)

		verify(exactly = 1) { sound.soundOff(2, 3, 0) }
		assertNull(pressedLight(2, 3))
	}

	@Test
	fun padPressedWhileTheOptionWindowIsOpen_isIgnored() {
		vm.toggleOptionWindow(true)

		vm.controllerPadTouch(2, 3, true)
		vm.controllerPadTouch(2, 3, false)
		unplug()

		verify(exactly = 0) { sound.soundOn(2, 3) }
		verify(exactly = 0) { sound.soundOff(2, 3, 0) }
	}
}
