package com.kimjisub.launchpad.manager

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ChannelManagerTest {

	private lateinit var manager: ChannelManager

	@Before
	fun setUp() {
		manager = ChannelManager(10, 10)
	}

	// === get() on empty manager ===

	@Test
	fun get_returnsNull_whenBtnEmpty() {
		assertNull(manager.get(0, 0))
	}

	@Test
	fun get_returnsNull_whenCirEmpty() {
		assertNull(manager.get(-1, 0))
	}

	// === add() and get() for btn (pad) items ===

	@Test
	fun add_and_get_btnItem() {
		manager.add(0, 0, ChannelManager.Channel.LED, 0xFF0000, 5)
		val item = requireNotNull(manager.get(0, 0)) { "Expected non-null item at (0,0)" }
		assertEquals(ChannelManager.Channel.LED, item.channel)
		assertEquals(0xFF0000, item.color)
		assertEquals(5, item.code)
	}

	@Test
	fun add_and_get_btnItem_differentPosition() {
		manager.add(5, 7, ChannelManager.Channel.GUIDE, 0x00FF00, 10)
		val item = requireNotNull(manager.get(5, 7)) { "Expected non-null item at (5,7)" }
		assertEquals(ChannelManager.Channel.GUIDE, item.channel)
		// Other positions remain null
		assertNull(manager.get(0, 0))
	}

	// === add() and get() for cir (circular/function key) items ===

	@Test
	fun add_and_get_cirItem() {
		manager.add(-1, 5, ChannelManager.Channel.GUIDE, 0x00FF00, 10)
		val item = requireNotNull(manager.get(-1, 5)) { "Expected non-null item at (-1,5)" }
		assertEquals(ChannelManager.Channel.GUIDE, item.channel)
		assertEquals(0x00FF00, item.color)
		assertEquals(10, item.code)
	}

	// === Priority-based retrieval ===

	@Test
	fun get_returnsHighestPriority_uiOverLed() {
		// UI has priority 0 (highest), LED has priority 4 (lowest)
		manager.add(0, 0, ChannelManager.Channel.LED, 0xFF0000, 5)
		manager.add(0, 0, ChannelManager.Channel.UI, 0x00FF00, 10)

		val item = requireNotNull(manager.get(0, 0)) { "Expected non-null item at (0,0)" }
		assertEquals(ChannelManager.Channel.UI, item.channel)
	}

	@Test
	fun get_returnsHighestPriority_guideOverPressed() {
		// GUIDE has priority 2, PRESSED has priority 3
		manager.add(0, 0, ChannelManager.Channel.PRESSED, 0xFF0000, 5)
		manager.add(0, 0, ChannelManager.Channel.GUIDE, 0x00FF00, 10)

		val item = requireNotNull(manager.get(0, 0)) { "Expected non-null item at (0,0)" }
		assertEquals(ChannelManager.Channel.GUIDE, item.channel)
	}

	// === remove() ===

	@Test
	fun remove_clearsItem() {
		manager.add(0, 0, ChannelManager.Channel.LED, 0xFF0000, 5)
		manager.remove(0, 0, ChannelManager.Channel.LED)
		assertNull(manager.get(0, 0))
	}

	@Test
	fun remove_cirItem() {
		manager.add(-1, 3, ChannelManager.Channel.UI, 0xFF0000, 5)
		manager.remove(-1, 3, ChannelManager.Channel.UI)
		assertNull(manager.get(-1, 3))
	}

	@Test
	fun remove_exposesLowerPriority() {
		manager.add(0, 0, ChannelManager.Channel.UI, 0x00FF00, 10)
		manager.add(0, 0, ChannelManager.Channel.LED, 0xFF0000, 5)

		// Remove UI (highest priority), LED should now be returned
		manager.remove(0, 0, ChannelManager.Channel.UI)
		val item = requireNotNull(manager.get(0, 0)) { "Expected non-null item after removing UI" }
		assertEquals(ChannelManager.Channel.LED, item.channel)
	}

	// === setBtnIgnore / setCirIgnore ===

	@Test
	fun setBtnIgnore_skipsIgnoredChannel() {
		manager.add(0, 0, ChannelManager.Channel.UI, 0x00FF00, 10)
		manager.setBtnIgnore(ChannelManager.Channel.UI, true)
		assertNull(manager.get(0, 0))
	}

	@Test
	fun setBtnIgnore_fallsToNextChannel() {
		manager.add(0, 0, ChannelManager.Channel.UI, 0x00FF00, 10)
		manager.add(0, 0, ChannelManager.Channel.LED, 0xFF0000, 5)

		manager.setBtnIgnore(ChannelManager.Channel.UI, true)
		val item = requireNotNull(manager.get(0, 0)) { "Expected LED item after ignoring UI" }
		assertEquals(ChannelManager.Channel.LED, item.channel)
	}

	@Test
	fun setBtnIgnore_canBeReEnabled() {
		manager.add(0, 0, ChannelManager.Channel.UI, 0x00FF00, 10)
		manager.setBtnIgnore(ChannelManager.Channel.UI, true)
		assertNull(manager.get(0, 0))

		// Re-enable
		manager.setBtnIgnore(ChannelManager.Channel.UI, false)
		val item = requireNotNull(manager.get(0, 0)) { "Expected UI item after re-enabling" }
		assertEquals(ChannelManager.Channel.UI, item.channel)
	}

	@Test
	fun setCirIgnore_skipsIgnoredChannel() {
		manager.add(-1, 5, ChannelManager.Channel.GUIDE, 0x00FF00, 10)
		manager.setCirIgnore(ChannelManager.Channel.GUIDE, true)
		assertNull(manager.get(-1, 5))
	}

	// === PRESSED and CHAIN share priority slot ===

	@Test
	fun pressed_and_chain_sharePrioritySlot() {
		// PRESSED and CHAIN both have priority 3
		manager.add(0, 0, ChannelManager.Channel.PRESSED, 0xFF0000, 5)
		manager.add(0, 0, ChannelManager.Channel.CHAIN, 0x00FF00, 10)

		// CHAIN overwrites PRESSED at the same slot
		val item = requireNotNull(manager.get(0, 0)) { "Expected CHAIN item at (0,0)" }
		assertEquals(ChannelManager.Channel.CHAIN, item.channel)
	}

	// === Color -1 conversion from LaunchpadColor.ARGB ===

	@Test
	fun add_withNegativeOneColor_usesLaunchpadColorARGB() {
		val code = 5 // LaunchpadColor.ARGB[5] = 0xFFef5350
		manager.add(0, 0, ChannelManager.Channel.LED, -1, code)
		val item = requireNotNull(manager.get(0, 0)) { "Expected item with ARGB color" }
		assertEquals(LaunchpadColor.ARGB[code].toInt(), item.color)
		assertEquals(code, item.code)
	}

	// === Multi-channel conflict scenarios ===

	@Test
	fun allChannels_returnHighestPriority_ui() {
		// Add all non-conflicting channels (UI, UI_UNIPAD, GUIDE, LED)
		manager.add(0, 0, ChannelManager.Channel.LED, 0x000004, 4)
		manager.add(0, 0, ChannelManager.Channel.GUIDE, 0x000002, 2)
		manager.add(0, 0, ChannelManager.Channel.UI_UNIPAD, 0x000001, 1)
		manager.add(0, 0, ChannelManager.Channel.UI, 0x000000, 0)

		val item = requireNotNull(manager.get(0, 0))
		assertEquals(ChannelManager.Channel.UI, item.channel)
	}

	@Test
	fun removeAllChannels_returnsNull() {
		manager.add(0, 0, ChannelManager.Channel.UI, 0x000000, 0)
		manager.add(0, 0, ChannelManager.Channel.GUIDE, 0x000002, 2)
		manager.add(0, 0, ChannelManager.Channel.LED, 0x000004, 4)

		manager.remove(0, 0, ChannelManager.Channel.UI)
		manager.remove(0, 0, ChannelManager.Channel.GUIDE)
		manager.remove(0, 0, ChannelManager.Channel.LED)

		assertNull(manager.get(0, 0))
	}

	@Test
	fun multipleIgnores_fallsThroughCorrectly() {
		manager.add(0, 0, ChannelManager.Channel.UI, 0x000000, 0)
		manager.add(0, 0, ChannelManager.Channel.UI_UNIPAD, 0x000001, 1)
		manager.add(0, 0, ChannelManager.Channel.GUIDE, 0x000002, 2)
		manager.add(0, 0, ChannelManager.Channel.LED, 0x000004, 4)

		// Ignore UI and UI_UNIPAD, should fall through to GUIDE
		manager.setBtnIgnore(ChannelManager.Channel.UI, true)
		manager.setBtnIgnore(ChannelManager.Channel.UI_UNIPAD, true)

		val item = requireNotNull(manager.get(0, 0))
		assertEquals(ChannelManager.Channel.GUIDE, item.channel)
	}

	@Test
	fun cirItem_priorityAndIgnore() {
		manager.add(-1, 0, ChannelManager.Channel.UI, 0x000000, 0)
		manager.add(-1, 0, ChannelManager.Channel.LED, 0x000004, 4)

		// Highest priority is UI
		assertEquals(ChannelManager.Channel.UI, manager.get(-1, 0)?.channel)

		// Ignore UI on circular buttons
		manager.setCirIgnore(ChannelManager.Channel.UI, true)
		assertEquals(ChannelManager.Channel.LED, manager.get(-1, 0)?.channel)

		// Remove LED, nothing left visible
		manager.remove(-1, 0, ChannelManager.Channel.LED)
		assertNull(manager.get(-1, 0))
	}

	@Test
	fun btnAndCirIndependent() {
		// Adding to btn should not affect cir and vice versa
		manager.add(0, 0, ChannelManager.Channel.UI, 0xFF0000, 1)
		manager.add(-1, 0, ChannelManager.Channel.LED, 0x00FF00, 2)

		val btnItem = requireNotNull(manager.get(0, 0))
		assertEquals(ChannelManager.Channel.UI, btnItem.channel)

		val cirItem = requireNotNull(manager.get(-1, 0))
		assertEquals(ChannelManager.Channel.LED, cirItem.channel)

		// Ignoring on btn should not affect cir
		manager.setBtnIgnore(ChannelManager.Channel.UI, true)
		assertNull(manager.get(0, 0))
		assertNotNull(manager.get(-1, 0))
	}

	// === Item toString ===

	@Test
	fun item_toString_containsAllFields() {
		val item = ChannelManager.Item(ChannelManager.Channel.LED, 0xFF0000, 5)
		val str = item.toString()
		assertTrue(str.contains("LED"))
		assertTrue(str.contains("16711680")) // 0xFF0000
		assertTrue(str.contains("5"))
	}
}
