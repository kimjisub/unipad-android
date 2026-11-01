package com.kimjisub.launchpad.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Test

class InstallSourceTest {
	@Test
	fun knownStoresHaveFixedCategories() {
		val stores = mapOf(
			"com.android.vending" to "play",
			"com.sec.android.app.samsungapps" to "samsung",
			"com.uptodown" to "uptodown",
			"com.apkpure.aegon" to "apkpure",
		)
		stores.forEach { (installer, category) ->
			assertEquals(category, InstallSource.classify(installer))
		}
	}

	@Test
	fun absentInstallerIsUnknown() {
		assertEquals("unknown", InstallSource.classify(null))
		assertEquals("unknown", InstallSource.classify(""))
	}

	@Test
	fun everyOtherInstallerIsOtherWithoutReturningItsName() {
		listOf(
			"org.example.store",
			"com.android.packageinstaller",
			"com.google.android.packageinstaller",
			"com.android.shell",
			"com.android.vending.impostor",
			"COM.ANDROID.VENDING",
		).forEach { assertEquals("other", InstallSource.classify(it)) }
	}

	@Test
	fun successfulLookupIsReadOnceAndClassified() {
		var reads = 0
		assertEquals("play", InstallSource.read { reads++; "com.android.vending" })
		assertEquals(1, reads)
	}

	@Test
	fun missingLookupValueIsUnknown() {
		assertEquals("unknown", InstallSource.read { null })
	}

	@Test
	fun lookupFailuresAreUnknownAndDoNotEscape() {
		listOf(
			SecurityException("org.example.private.store"),
			IllegalArgumentException("org.example.private.store"),
			Exception("org.example.private.store"),
		).forEach { failure ->
			assertEquals("unknown", InstallSource.read { throw failure })
		}
	}
}
