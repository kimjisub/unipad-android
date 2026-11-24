package com.kimjisub.launchpad.basefeatures

import android.Manifest
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import com.kimjisub.launchpad.R
import com.kimjisub.launchpad.activity.MainActivity
import com.kimjisub.launchpad.activity.FBStoreActivity
import com.kimjisub.launchpad.manager.PreferenceManager
import com.kimjisub.launchpad.unipack.UniPackFolder
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.loadKoinModules
import org.koin.core.context.unloadKoinModules
import java.io.File

@RunWith(AndroidJUnit4::class)
class StoreDownloadTest {
    @Test fun offlineCatalogDownloadUpdatesResultAndLibrary() {
        val screen = FeatureScreen()
        screen.assertDedicatedWorkspace()
        screen.cleanPacks()
        screen.install()
        val network = FakeNetwork(screen.zip())
        screen.cleanPacks()
        val prefs = PreferenceManager(screen.context)
        val oldCount = prefs.prevStoreCount
        loadKoinModules(network.module)
        try {
            screen.launch(MainActivity::class.java)
            screen.clickText(R.string.guide_download_new)
            screen.node(By.text("Offline Store Fixture")).click()
            screen.clickText(R.string.download)
            if (android.os.Build.VERSION.SDK_INT >= 33 && !notificationsAllowed(screen)) allowNotifications(screen)
            screen.node(By.text(screen.text(R.string.downloaded)))
            val folder = File(screen.workspace, FeatureScreen.STORE_ID)
            assertTrue(File(folder, "info").isFile)
            assertEquals(FeatureScreen.TITLE, UniPackFolder(folder).load().title)
            assertEquals(listOf("https://fixture.invalid/store.zip"), network.requests.toList())
            assertTrue(network.catalog.attached)
            assertEquals(1L, prefs.prevStoreCount)
            screen.capture("store-downloaded")
            screen.device.pressBack()
            screen.node(By.text(screen.text(R.string.STP_downloadedCount)))
            screen.device.pressBack()
            screen.node(By.text(FeatureScreen.TITLE))
            screen.capture("store-pack-in-library")
            assertFalse("Store listener was retained after leaving", network.catalog.attached)
        } finally {
            screen.close()
            unloadKoinModules(network.module)
            prefs.prevStoreCount = oldCount
        }
    }

    private fun notificationsAllowed(screen: FeatureScreen) =
        screen.context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /**
     * The download asks for notification access. The system dialog can drop a tap that arrives while it is
     * still appearing, which leaves it over the store screen, so Allow is tapped again until the grant is
     * recorded. A dialog that closes without granting fails the wait.
     */
    private fun allowNotifications(screen: FeatureScreen) {
        val allow = By.res("com.android.permissioncontroller", "permission_allow_button")
        // The system dialog is asynchronous; do not race a nullable immediate lookup.
        screen.node(allow)
        screen.await("Notification access was not granted from its dialog") {
            notificationsAllowed(screen) || run {
                runCatching { screen.device.findObject(allow)?.click() }
                screen.device.wait(Until.gone(allow), 1000)
                notificationsAllowed(screen)
            }
        }
    }
}
