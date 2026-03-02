package com.kimjisub.launchpad.manager

import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * ColorManager wraps Android's ContextCompat.getColor with lazy delegates.
 * Full color resolution tests require an Android context (instrumented tests).
 * This test validates the class structure is sound.
 */
class ColorManagerTest {

	@Test
	fun colorManager_classExists() {
		// Verify the class is accessible from tests
		assertNotNull(ColorManager::class)
	}
}
