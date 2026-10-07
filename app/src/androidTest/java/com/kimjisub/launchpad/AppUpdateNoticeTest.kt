package com.kimjisub.launchpad

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.google.android.play.core.appupdate.testing.FakeAppUpdateManager
import com.kimjisub.launchpad.activity.MainActivity
import com.kimjisub.launchpad.manager.PlayAppUpdateSource
import com.kimjisub.launchpad.manager.PreferenceAppUpdateStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The pack list's new-version notice against Play's own fake update manager, through the real
 * Play adapter. The fake never shows Play's screens or restarts the app, so this does not replace
 * a real Play download and install with two uploaded builds.
 */
@RunWith(AndroidJUnit4::class)
class AppUpdateNoticeTest : BaseUITest() {
	private lateinit var play: FakeAppUpdateManager

	@Before
	fun installFakePlay() {
		PreferenceAppUpdateStore(context).apply {
			lastAutoCheckAt = null
			declinedAt = null
			installPostponedAt = null
			downloadRequested = false
		}
		onMain { play = FakeAppUpdateManager(context) }
		MainActivity.appUpdateSourceForTest = { launcher -> PlayAppUpdateSource(play, launcher) }
	}

	@After
	fun removeFakePlay() {
		MainActivity.appUpdateSourceForTest = null
	}

	@Test
	fun downloadAndInstallAreSeparateChoices() {
		onMain { play.setUpdateAvailable(NEWER_BUILD) }
		launchToMainScreen()
		assertTrue("new-version card did not appear", waitForText(R.string.update_offer_title))
		takeScreenshot("update_offer")

		click(R.string.update_download)
		assertTrue("Play consent did not open", waitUntil { onMain { play.isConfirmationDialogVisible } })
		onMain {
			play.userAcceptsUpdate()
			// The fake takes sizes only once its download has started.
			play.downloadStarts()
			play.setTotalBytesToDownload(1000)
			play.setBytesDownloaded(400)
		}
		assertTrue("download progress not shown", waitForText(R.string.update_downloading_title))
		takeScreenshot("update_downloading")
		assertTrue("download percentage not shown", waitUntil(5000L) { findText("40%") != null })

		onMain { play.downloadCompletes() }
		assertTrue("ready card did not appear", waitForText(R.string.update_ready_title))
		takeScreenshot("update_ready")
		assertFalse("finished download installed on its own", onMain { play.isInstallSplashScreenVisible })

		click(R.string.update_install)
		assertTrue("install was not requested", waitUntil { onMain { play.isInstallSplashScreenVisible } })
	}

	@Test
	fun downloadFinishingDuringPlayWaitsForTheList() {
		onMain { play.setUpdateAvailable(NEWER_BUILD) }
		launchToMainScreen()
		acceptDownload()

		playTestPackWithOneTap()
		onMain {
			play.downloadStarts()
			play.setTotalBytesToDownload(1000)
			play.setBytesDownloaded(1000)
			play.downloadCompletes()
		}
		Thread.sleep(1500)
		assertTrue("play screen was left", isOnPlayScreen())
		assertFalse("ready notice shown over the play screen", device.hasObject(By.text(str(R.string.update_ready_title))))
		assertFalse(onMain { play.isInstallSplashScreenVisible })

		quitPlayToMain()
		device.findObject(By.res("main_detail_${TestUniPack.FOLDER_NAME}"))?.let { device.pressBack() }
		assertTrue("ready card did not appear back on the list", waitForText(R.string.update_ready_title))
		assertFalse(onMain { play.isInstallSplashScreenVisible })
	}

	@Test
	fun selectingAPackHidesTheCard() {
		onMain { play.setUpdateAvailable(NEWER_BUILD) }
		launchToMainScreen()
		assertTrue(waitForText(R.string.update_offer_title))

		selectTestPack()
		assertTrue(device.wait(Until.gone(By.text(str(R.string.update_offer_title))), 5000L))
		device.pressBack()
		assertTrue("card did not come back after deselecting", waitForText(R.string.update_offer_title))
	}

	@Test
	fun laterRestsAcrossRestartButTheLinkStillChecks() {
		onMain { play.setUpdateAvailable(NEWER_BUILD) }
		launchToMainScreen()
		assertTrue(waitForText(R.string.update_offer_title))
		click(R.string.update_later)
		assertTrue(device.wait(Until.gone(By.text(str(R.string.update_offer_title))), 5000L))
		assertTrue("check link missing", waitForText(R.string.update_check))
		takeScreenshot("update_link")

		launchToMainScreen()
		assertTrue(waitForText(R.string.update_check))
		Thread.sleep(1500)
		assertFalse("offered again within 7 days", device.hasObject(By.text(str(R.string.update_offer_title))))

		click(R.string.update_check)
		assertTrue("checking by hand did not show the card", waitForText(R.string.update_offer_title))
	}

	@Test
	fun checkingByHandWithNothingNewSaysSo() {
		onMain { play.setUpdateNotAvailable() }
		launchToMainScreen()
		click(R.string.update_check)
		assertTrue(waitForText(R.string.update_none))
		takeScreenshot("update_none")
		click(R.string.update_close)
		assertTrue(device.wait(Until.gone(By.text(str(R.string.update_none))), 5000L))
	}

	@Test
	fun failedInstallKeepsTheAppAndOffersRetryAndStore() {
		onMain { play.setUpdateAvailable(NEWER_BUILD) }
		launchToMainScreen()
		acceptDownload()
		onMain {
			play.downloadStarts()
			play.downloadCompletes()
		}
		assertTrue(waitForText(R.string.update_ready_title))
		click(R.string.update_install)
		assertTrue(waitUntil { onMain { play.isInstallSplashScreenVisible } })
		onMain { play.installFails() }
		assertTrue("failure card did not appear", waitForText(R.string.update_failed))
		assertNotNull(findText(R.string.update_retry))
		assertNotNull(findText(R.string.update_check_in_store))
		takeScreenshot("update_failed")
		assertTrue("the pack is still listed", device.hasObject(By.res("main_pack_${TestUniPack.FOLDER_NAME}")))
	}

	@Test
	fun restsAreKeptOnDisk() {
		PreferenceAppUpdateStore(context).apply {
			declinedAt = 123L
			downloadRequested = true
		}
		PreferenceAppUpdateStore(context).apply {
			assertEquals(123L, declinedAt)
			assertTrue(downloadRequested)
			assertEquals(null, installPostponedAt)
		}
	}

	/**
	 * [openTestPackInPlay] retaps the row until the pack panel shows; after the consent step the
	 * list is slow to report idle and a retap deselects the pack again, so tap once and wait.
	 */
	private fun playTestPackWithOneTap() {
		val row = findTestPackRow()
		device.click(row.visibleBounds.centerX(), row.visibleBounds.centerY())
		assertTrue(
			"pack panel did not open",
			device.wait(Until.hasObject(By.res("main_detail_${TestUniPack.FOLDER_NAME}")), 10000L),
		)
		Thread.sleep(700)
		val bounds = findTestPackRow().visibleBounds
		device.click(bounds.left + (50 * context.resources.displayMetrics.density).toInt(), bounds.centerY())
		assertTrue("Play screen did not open", waitForPlayScreen())
	}

	private fun acceptDownload() {
		assertTrue(waitForText(R.string.update_offer_title))
		click(R.string.update_download)
		assertTrue("Play consent did not open", waitUntil { onMain { play.isConfirmationDialogVisible } })
		onMain { play.userAcceptsUpdate() }
	}

	private fun waitForText(res: Int, timeoutMs: Long = 10000L): Boolean =
		waitUntil(timeoutMs) { findText(res) != null }

	private fun click(res: Int) {
		assertTrue("'${str(res)}' not found", clickFresh(5000L) { findText(res) })
	}

	/** With large text the side panel scrolls; bring the text into view like a person would. */
	private fun findText(res: Int): UiObject2? = findText(str(res))

	private fun findText(value: String): UiObject2? {
		val text = By.text(value)
		device.findObject(text)?.let { return it }
		return device.findObject(By.scrollable(true).hasDescendant(By.res("app_update_card"), 4))
			?.scrollUntil(Direction.DOWN, Until.findObject(text))
	}

	private fun <T> onMain(block: () -> T): T {
		var result: Result<T>? = null
		InstrumentationRegistry.getInstrumentation().runOnMainSync { result = runCatching(block) }
		return result!!.getOrThrow()
	}

	private companion object {
		const val NEWER_BUILD = 10_000
	}
}
