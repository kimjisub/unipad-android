package com.kimjisub.launchpad.network.accounting

import org.junit.Assert.*
import org.junit.Test

class DownloadAccountingRouteTest {
    @Test fun debugAndEmulatorNeverUseProductionEvenWithPlayInstaller() {
        assertNull(DownloadAccountingRoute.select(true, false, false) { "com.android.vending" })
        assertNull(DownloadAccountingRoute.select(false, false, true) { "com.android.vending" })
        assertEquals(DownloadAccountingRoute.EMULATOR,
            DownloadAccountingRoute.select(true, true, true) { error("must not inspect installer") })
    }

    @Test fun releaseRequiresPlayAndIgnoresLocalBuildFlag() {
        assertEquals(DownloadAccountingRoute.PRODUCTION,
            DownloadAccountingRoute.select(false, true, false) { "com.android.vending" })
        for (installer in listOf(null, "", "com.android.shell", "other.store")) {
            assertNull(DownloadAccountingRoute.select(false, false, false) { installer })
        }
        assertNull(DownloadAccountingRoute.select(false, false, false) { throw SecurityException() })
    }
}
