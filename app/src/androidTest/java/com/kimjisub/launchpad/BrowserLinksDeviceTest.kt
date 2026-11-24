package com.kimjisub.launchpad

import android.app.Activity
import android.app.Instrumentation
import android.content.ContextWrapper
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.kimjisub.launchpad.activity.ThemeActivity
import com.kimjisub.launchpad.tool.BrowserLinks
import com.kimjisub.launchpad.tool.splitties.browse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrowserLinksDeviceTest {
	@Test fun themeGuideOpensMarkedAddress() {
		val instrumentation = InstrumentationRegistry.getInstrumentation()
		val device = UiDevice.getInstance(instrumentation)
		var opened: String? = null
		val capture = InstrumentationRegistry.getArguments().getString("captureBrowserLinks") == "true"
		val monitor = object : Instrumentation.ActivityMonitor() {
			override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
				if (intent.action != Intent.ACTION_VIEW) return null
				opened = intent.dataString
				return if (capture) null else Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null)
			}
		}
		instrumentation.addMonitor(monitor)
		try {
			ActivityScenario.launch(ThemeActivity::class.java).use {
				val title = instrumentation.targetContext.getString(R.string.theme_add_create)
				requireNotNull(device.wait(Until.findObject(By.text(title)), 10000L)).click()
				instrumentation.waitForIdleSync()
				assertEquals("https://unipad.io/docs/theme?no_analytics=1", opened)
				if (capture) {
					assertTrue("Browser address bar did not appear", device.wait(Until.hasObject(By.res("com.android.chrome", "url_bar")), 20000L))
					assertTrue(TestCaptures.screenshot(device, "theme-guide-test-run.png"))
				}
			}
		} finally { instrumentation.removeMonitor(monitor) }
	}

	@Test fun instrumentedRunMarksOnlyOfficialPages() {
		assertTrue("The test runner must mark this process before a link opens", BrowserLinks.isTestRun)
		val opened = mutableListOf<Intent>()
		val context = object : ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
			override fun startActivity(intent: Intent) { opened += intent }
		}
		val links = listOf("https://unipad.io", "https://unipad.io/docs/get-started",
			"https://unipad.io/docs/theme", "https://unipad.io/docs/guide#launchpad",
			"https://unipad.io/docs/guide#custom-firmware", "https://example.com/artist")
		links.forEach { context.browse(it) }
		assertEquals(listOf("https://unipad.io?no_analytics=1",
			"https://unipad.io/docs/get-started?no_analytics=1",
			"https://unipad.io/docs/theme?no_analytics=1",
			"https://unipad.io/docs/guide?no_analytics=1#launchpad",
			"https://unipad.io/docs/guide?no_analytics=1#custom-firmware",
			"https://example.com/artist"), opened.map { it.data.toString() })
	}
}
