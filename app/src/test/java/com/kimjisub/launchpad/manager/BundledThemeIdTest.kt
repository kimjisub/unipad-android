package com.kimjisub.launchpad.manager

import org.junit.Assert.*
import org.junit.Test

class BundledThemeIdTest {
    @Test fun legacyBundledNameUsesAssets() {
        assertEquals("asset://sskin", resolveBundledThemeId("zip://sskin") { it == "sskin" })
    }

    @Test fun externalThemeKeepsItsFolder() {
        assertEquals("zip://my-skin", resolveBundledThemeId("zip://my-skin") { it == "sskin" })
    }

    @Test fun assetAndDefaultSelectionsNeedNoAssetLookup() {
        for (id in listOf("asset://sskin", "com.kimjisub.launchpad", "com.kimjisub.launchpad.dev")) {
            assertEquals(id, resolveBundledThemeId(id) { error("Unexpected asset lookup") })
        }
    }

    @Test fun onlyImmediateBundledNamesCanBeAliases() {
        for (id in listOf("zip://", "zip://other/sskin", "zip://../sskin", "zip://other\\sskin")) {
            assertEquals(id, resolveBundledThemeId(id) { error("Unexpected asset lookup") })
        }
    }
}
