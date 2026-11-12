package com.kimjisub.launchpad.basefeatures

import android.net.Uri
import android.os.SystemClock
import androidx.test.uiautomator.By
import com.kimjisub.launchpad.R
import com.kimjisub.launchpad.TestCaptures
import com.kimjisub.launchpad.activity.MainActivity
import com.kimjisub.launchpad.viewmodel.PlayMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Screen observations of manual chain moves and the real import failure dialog. */
class PlaybackAndArchiveBoundaryTest : PlaybackScreenTest() {
    private fun manualMoveSurvives(name: String, control: () -> Unit) {
        val pack = screen.pack()
        File(pack, "keySound").writeText("1 1 1 silence.wav 1 2\n")
        File(pack, "autoPlay").writeText("d 60000\non 1 1\n")
        screen.openPlay()
        val vm = screen.vm()
        screen.onMain { vm.switchPlayMode(PlayMode.AutoPlay) }
        val elapsed = screen.onMain {
            val started = SystemClock.elapsedRealtime()
            vm.padTouch(0, 0, true)
            // Main also delivers the delayed move. Keeping these calls in one Main turn
            // proves the control precedes delivery even when rendering takes over 100 ms.
            assertEquals("The manual move must still be pending before the control", 0, vm.chain.value)
            control()
            SystemClock.elapsedRealtime() - started
        }
        try {
            try {
                screen.await({ "The manual chain move did not arrive after the control: ${screen.onMain { vm.chain.value }}" }) {
                    screen.onMain { vm.chain.value == 1 }
                }
            } finally {
                val chain = screen.onMain { vm.chain.value }
                screen.capture(name)
                assertTrue(TestCaptures.save(screen.device, "basefeatures-captures/$name.txt") {
                    it.writeText("control_elapsed_ms=$elapsed\nfinal_chain=$chain\nsound_requests=${audio.plays.size}\n")
                    true
                })
            }
            assertEquals("A playback control must preserve the manual pad's chain move", 1, screen.onMain { vm.chain.value })
        } finally {
            screen.onMain {
                vm.padTouch(0, 0, false)
                vm.switchPlayMode(PlayMode.None)
            }
        }
    }

    @Test fun pausingAutoplayPreservesTheManualPadsPendingChainMove() =
        manualMoveSurvives("manual-chain-after-pause") { screen.vm().autoPlayPause() }

    @Test fun disablingAutoplayPreservesTheManualPadsPendingChainMove() =
        manualMoveSurvives("manual-chain-after-disable") { screen.vm().switchPlayMode(PlayMode.None) }

    @Test fun importingTwoPacksExplainsTheRejectionOnScreen() {
        val archive = File(screen.context.cacheDir, "two-packs.zip")
        ZipOutputStream(archive.outputStream()).use { zip ->
            for (folder in listOf("first", "second")) {
                zip.putNextEntry(ZipEntry("$folder/info"))
                zip.write("title=$folder\nproducerName=Test\nbuttonX=8\nbuttonY=8\nchain=1\n".toByteArray())
                zip.closeEntry()
            }
        }
        try {
            screen.launch(MainActivity::class.java)
            // Use the same callback as the file picker, keeping the importer and dialog real.
            screen.onMain {
                MainActivity::class.java.getDeclaredMethod("importUniPack", Uri::class.java)
                    .apply { isAccessible = true }.invoke(screen.resumed(), Uri.fromFile(archive))
            }
            screen.node(By.text(screen.text(R.string.importFailed)))
            screen.capture("multiple-packs-rejected")
            screen.node(By.textContains("Archive contains multiple packs"))
            assertTrue("The source archive must remain after rejection", archive.isFile)
            assertTrue("The existing pack must remain after rejection", File(screen.pack(), "info").isFile)
        } finally {
            archive.delete()
        }
    }
}
