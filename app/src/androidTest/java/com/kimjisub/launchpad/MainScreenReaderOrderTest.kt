package com.kimjisub.launchpad

import android.os.Build
import android.os.SystemClock
import com.kimjisub.launchpad.activity.MainActivity
import com.kimjisub.launchpad.manager.PreferenceAppUpdateStore
import com.kimjisub.launchpad.manager.UpdateStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * What a screen reader says, item by item, on the pack list: the information panel, the small update
 * notice in each of its states, the list controls and the pack rows, in English and Korean.
 */
class MainScreenReaderOrderTest : BaseUITest() {
    @Test fun english() = checkLanguage("en")
    @Test fun korean() = checkLanguage("ko")

    private class Expected(val label: String, val matches: (String) -> Boolean)

    private fun exactly(text: String) = Expected("\"$text\"") { it == text }
    private fun oneOf(texts: List<String>) = Expected(texts.joinToString(" | ") { "\"$it\"" }) { it in texts }
    private fun pattern(regex: Regex) = Expected("/${regex.pattern}/") { regex.matches(it) }

    private fun checkLanguage(language: String) {
        val originalLanguage = AppLanguage.get()
        val source = ControlledSource()
        try {
            AppLanguage.set(language)
            resetUpdatePrompts()
            MainActivity.appUpdateSourceForTest = { source }
            launchToMainScreen()
            findTestPackRow()
            val controls = panelAndControls(str(R.string.update_check))
            checkPackRows(awaitOrder("idle", controls).drop(controls.size))

            source.answer = UpdateStatus.Available
            resetUpdatePrompts()
            launchToMainScreen()
            awaitOrder("offer", panelAndControls(str(R.string.update_offer_title)))
            for ((status, name) in listOf(
                UpdateStatus.Downloading(0.4f) to "${str(R.string.update_downloading_title)}, 40%",
                UpdateStatus.Downloaded to str(R.string.update_ready_title),
                UpdateStatus.Failed to str(R.string.update_failed),
            )) {
                source.push(status)
                awaitOrder(status.toString(), panelAndControls(name))
            }

            selectTestPack()
            val selected = ScreenReaderOrder.read()
            val row = selected.indexOfFirst { it.tag == "main_pack_${TestUniPack.FOLDER_NAME}" }
            assertTrue("selected row missing:\n${selected.joinToString("\n")}", row >= 0)
            val flag = selected.getOrNull(row + 1)
            assertEquals("the selected row's play flag is not the next item:\n${selected.joinToString("\n")}",
                str(R.string.unipack_play), flag?.name)
            assertTrue("the play flag cannot be activated", flag!!.actionable)
        } finally {
            MainActivity.appUpdateSourceForTest = null
            AppLanguage.set(originalLanguage)
        }
    }

    private fun resetUpdatePrompts() {
        PreferenceAppUpdateStore(context).apply {
            lastAutoCheckAt = null; declinedAt = null; installPostponedAt = null; downloadRequested = false
        }
    }

    /** The information panel top to bottom, then the list controls left to right. */
    private fun panelAndControls(updateName: String): List<Expected> = listOf(
        exactly(str(R.string.cd_logo)),
        exactly(BuildConfig.VERSION_NAME),
        exactly(str(R.string.MPT_playCount)), pattern(Regex("\\d+")),
        exactly(str(R.string.MTP_count)), pattern(Regex("\\d+")),
        exactly(str(R.string.MTP_size)), pattern(Regex("\\d+(\\.\\d+)? ${Regex.escape(str(R.string.mb))}")),
        exactly(updateName),
        exactly(str(R.string.setting)),
        oneOf(listOf(R.string.sort_title, R.string.sort_producer, R.string.sort_download_date).map(::str)),
        oneOf(listOf(R.string.sort_ascending, R.string.sort_descending).map { "${str(R.string.sort_order)}, ${str(it)}" }),
        exactly(str(R.string.transfer_search)),
        exactly(str(R.string.store)),
        exactly(str(R.string.import_unipack)),
    )

    /** Reads until the first items say [expected], then checks every item has a name. */
    private fun awaitOrder(state: String, expected: List<Expected>): List<ScreenReaderOrder.Stop> {
        val deadline = SystemClock.uptimeMillis() + 10000L
        var stops: List<ScreenReaderOrder.Stop>
        var mismatch: String?
        do {
            stops = ScreenReaderOrder.read()
            mismatch = expected.indices.firstNotNullOfOrNull { i ->
                val actual = stops.getOrNull(i)?.name
                if (actual != null && expected[i].matches(actual)) null
                else "item ${i + 1} should be ${expected[i].label}, was ${actual?.let { "\"$it\"" } ?: "missing"}"
            }
            if (mismatch == null) break
            SystemClock.sleep(200)
        } while (SystemClock.uptimeMillis() < deadline)
        if (mismatch != null) fail("$state: $mismatch\n${stops.joinToString("\n")}")
        stops.filter { it.actionable && it.name.isBlank() }.forEach {
            fail("$state: a control has no name: $it\n${stops.joinToString("\n")}")
        }
        return stops
    }

    /** Rows say the pack before anything else, then the guide actions follow the last row. */
    private fun checkPackRows(afterControls: List<ScreenReaderOrder.Stop>) {
        val everything = afterControls.joinToString("\n")
        val rows = afterControls.takeWhile { it.tag?.startsWith("main_pack_") == true }
        val led = str(R.string.option_indicator_format).format(str(R.string.led).uppercase())
        val autoPlay = str(R.string.option_indicator_format).format(str(R.string.autoPlay).uppercase())
        assertEquals("test pack row:\n$everything",
            listOf("${TestUniPack.TITLE}, ${TestUniPack.PRODUCER}, $led, $autoPlay"),
            rows.filter { it.tag == "main_pack_${TestUniPack.FOLDER_NAME}" }.map { it.name })
        rows.forEach { assertTrue("a row is not actionable: $it", it.actionable) }
        val guides = listOfNotNull(
            str(R.string.guide_download_new), str(R.string.guide_import_external),
            str(R.string.guide_restore_packs).takeIf { Build.VERSION.SDK_INT >= Build.VERSION_CODES.R },
        )
        val rest = afterControls.drop(rows.size).map { it.name }
        assertTrue("after the rows only the guide actions should follow, in order:\n$everything",
            rest == guides.take(rest.size))
    }
}
