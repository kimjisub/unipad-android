package com.kimjisub.launchpad.viewmodel

import android.media.AudioManager
import com.kimjisub.launchpad.db.repository.UnipackRepository
import com.kimjisub.launchpad.manager.ChannelManager
import com.kimjisub.launchpad.manager.ChannelManager.Channel
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PlayActivityViewModelTest {

	private lateinit var vm: PlayActivityViewModel

	@Before
	fun setUp() {
		vm = PlayActivityViewModel(mockk<UnipackRepository>())
	}

	@Test
	fun toggleOptionWindow_doesNotThrow_beforeChannelManagerIsInitialized() {
		assertFalse(vm.isChannelManagerInitialized)

		vm.toggleOptionWindow(true)

		assertTrue(vm.isOptionWindowVisible)
		assertFalse(vm.isChannelManagerInitialized)
	}

	@Test
	fun refreshWatermark_doesNotThrow_beforeChannelManagerIsInitialized() {
		assertFalse(vm.isChannelManagerInitialized)

		vm.refreshWatermark()

		assertFalse(vm.isChannelManagerInitialized)
	}

	@Test
	fun refreshWatermark_paintsOptionWindowState_onceChannelManagerExists() {
		vm.toggleOptionWindow(true)
		vm.channelManager = ChannelManager(8, 8)
		assertNull(vm.channelManager.get(-1, 7))

		vm.refreshWatermark()

		val item = requireNotNull(vm.channelManager.get(-1, 7)) { "Expected UI top bar LED at function key 7" }
		assertEquals(Channel.UI, item.channel)
		assertEquals(PlayActivityViewModel.LED_RED_BRIGHT, item.code)
	}

	@Test
	fun repeatedBackPressesDuringLoading_paintOnlyTheLastOptionWindowState() {
		repeat(4) { vm.toggleOptionWindow() }
		assertFalse(vm.isOptionWindowVisible)
		vm.channelManager = ChannelManager(8, 8)

		vm.refreshWatermark()

		val item = requireNotNull(vm.channelManager.get(-1, 7)) { "Expected watermark LED at function key 7" }
		assertEquals(Channel.UI_UNIPAD, item.channel)
		assertEquals(PlayActivityViewModel.LED_BLUE, item.code)
	}

	@Test
	fun audioFocusChanges_doNotThrow_beforeRunnersExist() {
		vm.audioFocusPolicy.onFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
		vm.audioFocusPolicy.onFocusChange(AudioManager.AUDIOFOCUS_GAIN)
		vm.audioFocusPolicy.onFocusChange(AudioManager.AUDIOFOCUS_LOSS)

		assertFalse(vm.isAutoPlayPlaying)
		assertEquals(PlayMode.None, vm.playMode)
	}
}
