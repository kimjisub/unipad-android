package com.kimjisub.launchpad.basefeatures

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/** The retired share feature must have neither a link handler nor a registered screen. */
@RunWith(AndroidJUnit4::class)
class RetiredShareLinkTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun sharedCodeLinkHasNoHandlingActivity() {
        val link = Intent(Intent.ACTION_VIEW, Uri.parse("unipad://unipack?code=BASEFEATURES"))
            .setPackage(context.packageName)
        assertTrue("Retired share link still opens an activity",
            context.packageManager.queryIntentActivities(link, PackageManager.MATCH_DEFAULT_ONLY).isEmpty())
    }

    @Test
    fun retiredScreenHasNoActivityRegistration() {
        val component = ComponentName(context.packageName,
            "com.kimjisub.launchpad.activity.ImportPackByUrlActivity")
        try {
            context.packageManager.getActivityInfo(component, 0)
            fail("Retired share screen is still registered")
        } catch (_: PackageManager.NameNotFoundException) {
            // Absence also prevents an explicit external intent from opening the old screen.
        }
    }
}
