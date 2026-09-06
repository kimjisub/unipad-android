package com.kimjisub.launchpad.viewmodel

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
}
