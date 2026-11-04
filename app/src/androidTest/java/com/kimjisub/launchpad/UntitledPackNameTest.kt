package com.kimjisub.launchpad

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** A pack whose info file has no title is named by its folder in the list row and the detail panel. */
@RunWith(AndroidJUnit4::class)
class UntitledPackNameTest : BaseUITest() {

    @Test
    fun listRowAndDetailShowTheFolderName() {
        TestUniPack.install(context, title = "")
        launchToMainScreen()

        val row = findTestPackRow()
        assertTrue(
            "List row of a pack without a title does not show '${TestUniPack.FOLDER_NAME}'",
            row.wait(Until.hasObject(By.text(TestUniPack.FOLDER_NAME)), NAME_TIMEOUT_MS),
        )

        selectTestPack()
        val detail = device.findObject(By.res("main_detail_${TestUniPack.FOLDER_NAME}"))
        takeScreenshot("untitled_pack_detail")
        assertTrue(
            "Detail panel of a pack without a title does not show '${TestUniPack.FOLDER_NAME}'",
            detail.wait(Until.hasObject(By.text(TestUniPack.FOLDER_NAME)), NAME_TIMEOUT_MS),
        )
    }

    private companion object {
        const val NAME_TIMEOUT_MS = 5000L
    }
}
