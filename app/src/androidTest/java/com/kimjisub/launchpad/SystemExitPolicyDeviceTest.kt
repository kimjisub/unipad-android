package com.kimjisub.launchpad

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kimjisub.launchpad.diagnostics.SystemExit
import com.kimjisub.launchpad.diagnostics.SystemExitPolicy
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Android compiles regular expressions with its own engine, which rejects some patterns the JVM accepts
 * (a bare "}"), so the exit text redaction is checked here as well as in the JVM unit tests.
 */
@RunWith(AndroidJUnit4::class)
class SystemExitPolicyDeviceTest {
    @Test fun anrReasonIsRedactedOnTheDevice() {
        val reason = "bg anr: Broadcast of Intent { act=android.intent.action.VIEW dat=content://x/my pack v=2.zip " +
            "flg=0x10 cmp=com.kimjisub.launchpad/.Receiver (has extras) }"
        assertEquals("bg anr: Broadcast of Intent { act=android.intent.action.VIEW cmp=com.kimjisub.launchpad/.Receiver <redacted> }",
            SystemExitPolicy.description(SystemExit(SystemExitPolicy.ANR, description = reason)))
        assertEquals("ends dat=<redacted>",
            SystemExitPolicy.description(SystemExit(SystemExitPolicy.ANR, description = "ends dat=file:///a b")))
        assertEquals("bg anr", SystemExitPolicy.description(SystemExit(SystemExitPolicy.ANR, description = "bg anr")))
    }
}
