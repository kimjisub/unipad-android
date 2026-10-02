package com.kimjisub.launchpad.basefeatures

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
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
            if (android.os.Build.VERSION.SDK_INT >= 33 &&
                screen.context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                // The system dialog is asynchronous; do not race a nullable immediate lookup.
                screen.node(By.res("com.android.permissioncontroller", "permission_allow_button")).click()
            }
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
            assertFalse(network.count.attached)
        } finally {
            screen.close()
            unloadKoinModules(network.module)
            prefs.prevStoreCount = oldCount
        }
    }
}
