package com.kimjisub.launchpad.basefeatures

import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.kimjisub.launchpad.R
import com.kimjisub.launchpad.activity.ImportPackByUrlActivity
import com.kimjisub.launchpad.api.unipad.UniPadApi
import com.kimjisub.launchpad.api.unipad.vo.UnishareVO
import android.os.SystemClock
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.loadKoinModules
import org.koin.core.context.unloadKoinModules
import org.koin.dsl.module
import retrofit2.Response

@RunWith(AndroidJUnit4::class)
class ShareImportTest {
    @Test
    fun sharedCodeShowsFixtureBeforeDownloading() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        val fake = module {
            single<UniPadApi.UniPadApiService> {
                object : UniPadApi.UniPadApiService {
                    override suspend fun getUnishare(code: String) = Response.success(
                        UnishareVO(_id = "basefeatures", title = "Offline Share Fixture", producer = "Synthetic")
                    )
                }
            }
        }
        loadKoinModules(fake)
        try {
            ActivityScenario.launch<ImportPackByUrlActivity>(
                Intent(context, ImportPackByUrlActivity::class.java).setData(Uri.parse("unipad://import?code=BASEFEATURES"))
            ).use {
                assertNotNull("Injected share metadata never reached the screen",
                    device.wait(Until.findObject(By.text("Offline Share Fixture")), 10000))
                assertNotNull("Share import must ask before downloading",
                    device.wait(Until.findObject(By.text(context.getString(R.string.accept))), 3000))
            }
        } finally {
            unloadKoinModules(fake)
        }
    }
    @Test
    fun acceptedShareDownloadsFixtureShowsSuccessAndImportedPackOpens() {
        val screen = FeatureScreen()
        screen.assertDedicatedWorkspace()
        screen.cleanPacks()
        screen.install()
        val network = FakeNetwork(screen.zip())
        screen.cleanPacks()
        loadKoinModules(network.module)
        try {
            screen.launch(ImportPackByUrlActivity::class.java) {
                setData(Uri.parse("unipad://import?code=BASEFEATURES"))
            }
            screen.node(By.text(FeatureScreen.TITLE))
            assertEquals(listOf("https://api.unipad.io/unishare/BASEFEATURES"), network.requests.toList())
            screen.clickText(R.string.accept)
            screen.node(By.text(screen.text(R.string.success)))
            screen.capture("share-import-success")
            val folder = java.io.File(screen.workspace, FeatureScreen.SHARE_ID)
            assertTrue("Share ZIP was not installed", java.io.File(folder, "info").isFile)
            // The result activity closes automatically; start the library and open the installed row.
            screen.device.wait(Until.gone(By.text(screen.text(R.string.success))), 10000)
            screen.launch(com.kimjisub.launchpad.activity.MainActivity::class.java)
            screen.node(By.text(FeatureScreen.TITLE))
            screen.openLibraryPack()
            assertEquals(FeatureScreen.SHARE_ID, screen.onMain { screen.vm().unipack.id })
            assertEquals(listOf("https://api.unipad.io/unishare/BASEFEATURES", "https://api.unipad.io/unishare/basefeatures-share/download"), network.requests.toList())
            screen.capture("share-import-play")
        } finally {
            try { screen.close() } finally { unloadKoinModules(network.module) }
        }
    }

}
