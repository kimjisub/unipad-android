package com.kimjisub.launchpad

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Process
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.kimjisub.launchpad.diagnostics.ForegroundScreen
import com.kimjisub.launchpad.diagnostics.ForegroundScreenRecorder
import com.kimjisub.launchpad.diagnostics.SystemExit
import com.kimjisub.launchpad.diagnostics.SystemExitPolicy
import com.kimjisub.launchpad.diagnostics.SystemExitReporter
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Checks real lifecycle delivery and disk persistence; it does not manufacture a low-memory kill. */
@SuppressLint("NewApi")
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.R)
class ForegroundScreenDeviceTest : BaseUITest() {
    @Test fun mainAndPlayTransitionsPersistSeparateForegroundContext() {
        launchToMainScreen()
        val main = awaitScreen("MainActivity")
        assertFalse(main.packOpen)
        assertEquals(Process.myPid(), main.pid)
        assertTrue(main.processStartedAt > 0)
        assertTrue(main.since >= main.processStartedAt)

        openTestPackInPlay()
        val play = awaitScreen("PlayActivity")
        assertTrue(play.packOpen)
        assertEquals(main.processStartedAt, play.processStartedAt)
        assertEquals(main.pid, play.pid)
        assertTrue(play.since >= main.since)
        val exit = SystemExit(SystemExitPolicy.LOW_MEMORY, timestamp = System.currentTimeMillis(), pid = play.pid)
        assertEquals(play, SystemExitPolicy.foregroundScreenAt(exit, play))
        assertNull(SystemExitPolicy.foregroundScreenAt(exit.copy(importance = 230), play))

        quitPlayToMain()
        val returned = awaitScreen("MainActivity", packOpen = false)
        assertFalse(returned.packOpen)
        assertEquals(main.processStartedAt, returned.processStartedAt)
        assertEquals(main.pid, returned.pid)
        assertTrue(returned.since >= play.since)
    }

    private fun awaitScreen(name: String, packOpen: Boolean? = null): ForegroundScreen {
        val preferences = context.getSharedPreferences(SystemExitReporter.PREFS, Context.MODE_PRIVATE)
        var record: ForegroundScreen? = null
        assertTrue("Foreground record did not reach $name", waitUntil(10000) {
            record = ForegroundScreenRecorder.read(preferences)
            record?.screen == name && (packOpen == null || record?.packOpen == packOpen)
        })
        // Reload from disk, independently of the SharedPreferences in-memory cache.
        val disk = java.io.File(context.applicationInfo.dataDir, "shared_prefs/${SystemExitReporter.PREFS}.xml")
        assertTrue("Foreground record was not committed to disk", waitUntil(10000) {
            val xml = runCatching { disk.readText() }.getOrDefault("")
            xml.contains("value=\"${record!!.since}\"") && xml.contains(">$name</string>") &&
                xml.contains("name=\"foreground_pack_open\" value=\"${record!!.packOpen}\"")
        })
        return record!!
    }
}
