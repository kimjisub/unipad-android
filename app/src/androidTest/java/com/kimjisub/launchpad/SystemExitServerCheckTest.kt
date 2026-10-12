package com.kimjisub.launchpad

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.tasks.Tasks
import com.google.firebase.crashlytics.FirebaseCrashlytics
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/** A one-off upload check after a real system exit; ordinary UI runs keep collection disabled. */
@RunWith(AndroidJUnit4::class)
class SystemExitServerCheckTest {
    @Test fun debugCollectionRequiresAnExplicitServerCheck() {
        val crashlytics = FirebaseCrashlytics.getInstance()
        assertFalse("Debug installs must not enable automatic crash collection", crashlytics.isCrashlyticsCollectionEnabled)
        if (InstrumentationRegistry.getArguments().getString("systemExitServerCheck") == "true") {
            assertTrue("No completed report session is waiting to be sent",
                Tasks.await(crashlytics.checkForUnsentReports(), 60, TimeUnit.SECONDS))
            crashlytics.sendUnsentReports()
            // The SDK has no upload completion task. Keep this explicit check alive for dispatch;
            // verify the actual receipt separately in the server's report.
            Thread.sleep(15_000)
        }
    }
}
