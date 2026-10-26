package com.kimjisub.launchpad

import android.graphics.Rect
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import com.kimjisub.launchpad.activity.MainActivity
import com.kimjisub.launchpad.manager.PreferenceAppUpdateStore
import com.kimjisub.launchpad.manager.UpdateStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The same pack and geometry, including the accessibility name of every compact state. */
class AppUpdateLayoutTest : BaseUITest() {
    @Test fun englishDefault() = checkLayout("en", 1f)
    @Test fun koreanDefault() = checkLayout("ko", 1f)
    @Test fun englishLarge() = checkLayout("en", 2f)
    @Test fun koreanLarge() = checkLayout("ko", 2f)

    @Test fun offlineLookupCanRetryWithoutClaimingUpToDate() {
        val source = ControlledSource().apply { error = java.io.IOException("offline") }
        try {
            MainActivity.appUpdateSourceForTest = { source }
            launchToMainScreen()
            val check = device.wait(Until.findObject(By.text(str(R.string.update_check))), 5000L)
            assertNotNull(check)
            check.click()
            assertTrue(device.wait(Until.hasObject(By.text(str(R.string.update_check_failed))), 10000L))
            assertFalse(device.hasObject(By.text(str(R.string.update_none))))
            takeScreenshot("update_lookup_failed")
            source.error = null
            device.findObject(By.text(str(R.string.update_check_again))).click()
            assertTrue(device.wait(Until.hasObject(By.text(str(R.string.update_none))), 10000L))
            device.pressBack()
            findTestPackRow()
        } finally { MainActivity.appUpdateSourceForTest = null }
    }

    @Test fun unsupportedPlayKeepsTheStoreFallbackBehindATap() {
        val source = ControlledSource().apply { answer = UpdateStatus.Unsupported }
        try {
            MainActivity.appUpdateSourceForTest = { source }
            launchToMainScreen()
            assertFalse(device.hasObject(By.text(str(R.string.update_unsupported))))
            val check = device.wait(Until.findObject(By.text(str(R.string.update_check))), 5000L)
            assertNotNull(check)
            check.click()
            assertTrue(device.wait(Until.hasObject(By.text(str(R.string.update_unsupported))), 10000L))
            assertNotNull(device.findObject(By.text(str(R.string.update_check_in_store))))
            takeScreenshot("update_play_unsupported")
            device.pressBack()
            findTestPackRow()
        } finally { MainActivity.appUpdateSourceForTest = null }
    }

    @Test fun backgroundStatusesKeepSearchSelectionSettingsAndImportInFront() {
        val source = ControlledSource()
        try {
            MainActivity.appUpdateSourceForTest = { source }
            launchToMainScreen()
            device.findObject(By.desc(str(R.string.transfer_search))).click()
            val field = device.wait(Until.findObject(By.clazz("android.widget.EditText")), 5000L)
            assertNotNull("search field missing", field)
            field.text = TestUniPack.TITLE
            findTestPackRow()
            source.emit(UpdateStatus.Downloaded)
            assertTrue("search lost focus", device.findObject(By.clazz("android.widget.EditText")).isFocused)
            assertEquals(TestUniPack.TITLE, device.findObject(By.clazz("android.widget.EditText")).text)
            assertFalse(device.hasObject(By.res("app_update_dialog")))
            takeScreenshot("update_ready_during_search")
            device.findObject(By.desc(str(R.string.transfer_search))).click()
            selectTestPack()
            source.emit(UpdateStatus.Failed)
            val selectionKept = device.hasObject(By.res("main_detail_${TestUniPack.FOLDER_NAME}"))
            if (!selectionKept) {
                takeScreenshot("update-selection-failure")
                ActivityReadiness.captureState(context, device, "update-selection-failure", "state")
                TestCaptures.save(device, "update-selection-failure/input.txt") { it.writeText(device.executeShellCommand("dumpsys input")); true }
                TestCaptures.save(device, "update-selection-failure/windows.txt") { it.writeText(device.executeShellCommand("dumpsys window windows")); true }
            }
            assertTrue("selection was lost", selectionKept)
            assertFalse(device.hasObject(By.res("app_update_dialog")))
            device.pressBack()
            openSettings()
            source.emit(UpdateStatus.Downloaded)
            assertTrue("settings were left", device.hasObject(By.text(str(R.string.settings_storage))))
            assertFalse(device.hasObject(By.res("app_update_dialog")))
            device.pressBack()
            assertTrue(waitForMainScreen())
            device.findObject(By.desc(str(R.string.import_unipack))).click()
            assertTrue("file picker did not open", waitUntil(10000L) { device.currentPackageName != PACKAGE_NAME })
            val picker = device.currentPackageName
            source.emit(UpdateStatus.Downloaded)
            assertEquals("file picker was left", picker, device.currentPackageName)
            device.pressBack()
            assertTrue(waitForMainScreen())
            assertFalse(device.hasObject(By.res("app_update_dialog")))
            assertTrue(device.wait(Until.hasObject(noticeSaying(R.string.update_ready_title)), 5000L))
        } finally { MainActivity.appUpdateSourceForTest = null }
    }

    @Test fun automaticStatusKeepsKeyboardFocusOnTheUpdateControl() {
        val source = ControlledSource()
        fun control() = device.findObject(By.res("app_update_notice"))
            ?: device.findObject(By.res("app_update_check"))
        try {
            MainActivity.appUpdateSourceForTest = { source }
            launchToMainScreen()
            repeat(20) {
                if (control()?.isFocused != true) device.pressKeyCode(android.view.KeyEvent.KEYCODE_TAB)
            }
            assertTrue("could not focus the update control", control()?.isFocused == true)
            source.emit(UpdateStatus.Downloaded)
            assertTrue(device.wait(Until.hasObject(noticeSaying(R.string.update_ready_title)), 5000L))
            assertTrue("an automatic result moved keyboard focus", control()?.isFocused == true)
            assertFalse(device.hasObject(By.res("app_update_dialog")))
        } finally { MainActivity.appUpdateSourceForTest = null }
    }

    @Test fun largeTextInformationStaysAboveTheUpdateControl() {
        val originalLanguage = AppLanguage.get()
        val originalScale = device.executeShellCommand("settings get system font_scale").trim()
        try {
            device.executeShellCommand("settings put system font_scale 2.0")
            AppLanguage.set("en")
            MainActivity.appUpdateSourceForTest = { ControlledSource() }
            launchToMainScreen()
            val notice = device.wait(Until.findObject(By.res("app_update_notice")), 5000L)
            assertNotNull("compact update control missing", notice)
            val information = device.findObjects(By.scrollable(true))
                .filter { it.visibleBounds.left < notice.visibleBounds.centerX() }
                .minByOrNull { it.visibleBounds.left }
            assertNotNull("large text information is not scrollable", information)
            takeScreenshot("update_information_above_notice")
            assertTrue("scrolling information extends behind the update control: ${information!!.visibleBounds}, ${notice.visibleBounds}",
                information.visibleBounds.bottom <= notice.visibleBounds.top)
        } finally {
            MainActivity.appUpdateSourceForTest = null
            device.executeShellCommand(if (originalScale == "null") "settings delete system font_scale" else "settings put system font_scale $originalScale")
            AppLanguage.set(originalLanguage)
        }
    }

    private fun checkLayout(language: String, scale: Float) {
        val originalLanguage = AppLanguage.get()
        val originalScale = device.executeShellCommand("settings get system font_scale").trim()
        val source = ControlledSource()
        try {
            device.executeShellCommand("settings put system font_scale $scale")
            AppLanguage.set(language)
            PreferenceAppUpdateStore(context).apply {
                lastAutoCheckAt = null; declinedAt = null; installPostponedAt = null; downloadRequested = false
            }
            MainActivity.appUpdateSourceForTest = { source }
            launchToMainScreen()
            val original = positions()
            takeScreenshot("update_${language}_${scale}_none")
            source.answer = UpdateStatus.Available
            PreferenceAppUpdateStore(context).lastAutoCheckAt = null
            launchToMainScreen()
            assertTrue("offer missing", waitForState(R.string.update_offer_title))
            val automaticChoices = device.hasObject(By.res("app_update_card"))
            val statePositions = mutableListOf(positions())
            takeScreenshot("update_${language}_${scale}_offer")
            val notice = device.findObject(noticeSaying(R.string.update_offer_title))
            if (notice != null) {
                assertTrue("notice must become enabled", waitUntil(5000L) {
                    device.findObject(By.res("app_update_notice"))?.isEnabled == true
                })
                assertTrue("notice target is too small", notice.visibleBounds.height() >= 48 * context.resources.displayMetrics.density - 2)
                TestCaptures.save(device, "notice-${language}-${scale}.xml") { file -> device.dumpWindowHierarchy(file); true }
                assertTrue("notice could not be tapped", clickFresh(5000L) {
                    device.findObject(By.res("app_update_notice"))?.takeIf { it.isEnabled }
                })
            }
            assertTrue("update choices did not appear", device.wait(Until.hasObject(By.res("app_update_card")), 5000L))
            val download = By.text(str(R.string.update_download))
            if (!device.hasObject(download)) {
                device.findObject(By.scrollable(true))?.scrollUntil(androidx.test.uiautomator.Direction.DOWN, Until.findObject(download))
            }
            assertTrue("tap did not open download choices", device.wait(Until.hasObject(download), 5000L))
            takeScreenshot("update_${language}_${scale}_choices")
            if (notice != null) device.pressBack()
            for ((status, title, suffix) in listOf(
                Triple(UpdateStatus.Downloading(0.4f), R.string.update_downloading_title, "downloading"),
                Triple(UpdateStatus.Downloaded, R.string.update_ready_title, "ready"),
                Triple(UpdateStatus.Failed, R.string.update_failed, "failed"),
            )) {
                source.push(status)
                assertTrue("state missing: $suffix", waitForState(title))
                statePositions += positions()
                takeScreenshot("update_${language}_${scale}_$suffix")
            }
            assertFalse("automatic lookup opened the choices", automaticChoices)
            assertFalse("a background event opened choices", device.hasObject(By.res("app_update_card")))
            statePositions.forEach { assertEquals("update state moved the main contents", original, it) }
            openSettings()
        } finally {
            MainActivity.appUpdateSourceForTest = null
            device.executeShellCommand(if (originalScale == "null") "settings delete system font_scale" else "settings put system font_scale $originalScale")
            AppLanguage.set(originalLanguage)
        }
    }

    private fun waitForState(title: Int): Boolean = waitUntil(10000L) { device.hasObject(By.text(str(title))) }

    /** The small update notice itself, not the update choices' title that shares its text. */
    private fun noticeSaying(title: Int) = By.res("app_update_notice").hasDescendant(By.text(str(title)))

    private fun positions(): List<Rect> = listOf(
        By.desc(str(R.string.cd_logo)), By.desc(str(R.string.setting)),
        By.text(str(R.string.MPT_playCount)), By.res("main_pack_${TestUniPack.FOLDER_NAME}"),
    ).map { selector ->
        val node = device.wait(Until.findObject(selector), 5000L)
        assertNotNull("main content missing: $selector", node)
        Rect(node.visibleBounds)
    }
}
