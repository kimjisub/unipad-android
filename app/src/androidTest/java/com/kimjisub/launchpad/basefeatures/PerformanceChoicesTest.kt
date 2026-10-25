package com.kimjisub.launchpad.basefeatures

import android.content.Context
import android.graphics.Point
import android.media.AudioManager
import android.widget.TextView
import android.os.Process
import androidx.test.platform.app.InstrumentationRegistry
import com.kimjisub.launchpad.TestCaptures
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import com.kimjisub.launchpad.R
import com.kimjisub.launchpad.viewmodel.PlayMode
import java.util.regex.Pattern
import com.kimjisub.launchpad.manager.PreferenceManager
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Real touch delivery and retained performance choices, using silent sound requests. */
class PerformanceChoicesTest : PlaybackScreenTest() {
    @Test fun chainChangesOnDownBeforeAnotherFingerPlays() {
        screen.openPlay()
        val vm = screen.vm()
        val changes = mutableListOf<Int>()
        screen.onMain { vm.chain.addObserver { c, _ -> changes += c } }
        val fingers = Fingers(screen)
        try {
            val chain = fingers.down(chainButton(1))
            screen.capture("chain-held-before-pad")
            assertEquals("Chain 2 must already be selected while the first finger is down", 1, screen.onMain { vm.chain.value })
            val bounds = screen.padBounds().first()
            val pad = fingers.down(Point(bounds.centerX(), bounds.centerY()))
            screen.await("The second finger must play the new chain") { audio.plays.isNotEmpty() }
            assertNotNull(audio.loaded["silence2.wav"])
            assertNotEquals(audio.loaded["silence.wav"], audio.loaded["silence2.wav"])
            assertEquals(audio.loaded["silence2.wav"], audio.plays.last())
            fingers.up(pad)
            fingers.move(chain to chainButton(0))
            assertEquals(1, screen.onMain { vm.chain.value })
            fingers.up(chain)
            assertEquals(listOf(1), screen.onMain { changes.toList() })
        } finally { fingers.cancelRemaining() }
    }

    @Test fun traceOrderSurvivesProLightRelayoutWithRecordingOff() {
        screen.openPlay()
        val vm = screen.vm()
        screen.onMain { vm.scbTraceLog.setChecked(true) }
        pressPad(0); pressPad(1); pressPad(0)
        screen.onMain { vm.scbTraceLog.setChecked(false) }
        pressPad(2)
        screen.capture("trace-before-relayout")
        screen.onMain { vm.scbProLightMode.toggleChecked() }
        screen.await("Layout did not finish") { screen.onMain { vm.uiLoaded } }
        screen.instrumentation.waitForIdleSync()
        screen.device.waitForIdle()
        screen.capture("trace-after-relayout")
        assertEquals(listOf(0 to 0, 0 to 1, 0 to 0), screen.onMain { vm.traceLogSequence[0].toList() })
        val texts = screen.onMain { screen.views(screen.resumed().window.decorView).filterIsInstance<TextView>()
            .filter { it.id == com.kimjisub.design.R.id.traceLog }.map { it.text.toString() } }
        assertEquals("1 3 ", texts[0])
        assertEquals("2 ", texts[1])
        assertEquals("", texts[2])
        assertFalse(screen.onMain { vm.scbTraceLog.checked })
    }

    @Test fun watermarkExplicitChoiceSurvivesNewPerformanceAndRecreation() {
        InstrumentationRegistry.getArguments().getString("performanceChoicePhase")?.let {
            checkChoiceAcrossProcess(it)
            return
        }
        val prefs = screen.context.getSharedPreferences("data", Context.MODE_PRIVATE)
        val old = prefs.all["Watermark"] as? Boolean
        try {
            prefs.edit().remove("Watermark").commit()
            var scenario = screen.openPlay()
            assertTrue(screen.onMain { screen.vm().scbWatermark.checked })
            screen.onMain { screen.vm().scbWatermark.setChecked(false) }
            screen.capture("watermark-explicitly-off")
            scenario.close()
            scenario = screen.openPlay()
            screen.capture("watermark-reopened")
            assertFalse("An explicit off choice must survive reopening", screen.onMain { screen.vm().scbWatermark.checked })
            scenario.recreate()
            screen.await("Recreated screen not ready") { screen.onMain { screen.vm().uiLoaded } }
            assertFalse(screen.onMain { screen.vm().scbWatermark.checked })
            screen.onMain { screen.vm().scbWatermark.setChecked(true) }
            scenario.close()
            screen.openPlay()
            assertTrue(screen.onMain { screen.vm().scbWatermark.checked })
        } finally {
            prefs.edit().apply { if (old == null) remove("Watermark") else putBoolean("Watermark", old) }.commit()
        }
    }

    /** The external two-phase check force-stops the app between these instrumentations. */
    private fun checkChoiceAcrossProcess(phase: String) {
        val prefs = screen.context.getSharedPreferences("data", Context.MODE_PRIVATE)
        val saved = screen.context.getSharedPreferences("performance_choice_test", Context.MODE_PRIVATE)
        val manager = screen.context.getSystemService(AudioManager::class.java)
        if (phase == "prepare") {
            assertTrue(saved.edit().putInt("pid", Process.myPid())
                .putInt("volume", manager.getStreamVolume(AudioManager.STREAM_MUSIC))
                .putBoolean("hadWatermark", prefs.contains("Watermark"))
                .putBoolean("watermark", prefs.getBoolean("Watermark", true)).commit())
            screen.openPlay()
            screen.onMain { screen.vm().scbWatermark.setChecked(false) }
            manager.setStreamVolume(AudioManager.STREAM_MUSIC, 1, 0)
            assertFalse(prefs.getBoolean("Watermark", true))
            screen.capture("watermark-before-process-stop")
        } else {
            assertEquals("verify", phase)
            try {
                assertNotEquals("The verification must run in a new app process", saved.getInt("pid", -1), Process.myPid())
                screen.openPlay()
                assertFalse(screen.onMain { screen.vm().scbWatermark.checked })
                assertEquals(1, manager.getStreamVolume(AudioManager.STREAM_MUSIC))
                screen.capture("watermark-after-process-restart")
                assertTrue(TestCaptures.save(screen.device, "basefeatures-captures/choice-process-restart.txt") {
                    it.writeText("before_pid=${saved.getInt("pid", -1)}\nafter_pid=${Process.myPid()}\nwatermark=false\nvolume=1\n")
                    true
                })
            } finally {
                prefs.edit().apply {
                    if (saved.getBoolean("hadWatermark", false)) putBoolean("Watermark", saved.getBoolean("watermark", true))
                    else remove("Watermark")
                }.commit()
                manager.setStreamVolume(AudioManager.STREAM_MUSIC, saved.getInt("volume", 1), 0)
                saved.edit().clear().commit()
            }
        }
    }

    @Test fun traceMenuExposesStyleAndClearWithoutChangingRecording() {
        val prefs = PreferenceManager(screen.context)
        val old = prefs.traceLogClassic
        try {
            prefs.traceLogClassic = true
            screen.openPlay()
            val vm = screen.vm()
            screen.onMain { vm.scbTraceLog.setChecked(true) }
            pressPad(0); pressPad(1); pressPad(0)
            screen.onMain { vm.selectChain(1) }
            pressPad(2)
            val padBounds = screen.padBounds()
            screen.clickDescription(R.string.menu)
            screen.scrollTo(By.text(screen.text(R.string.trace_clear_all)), Direction.DOWN)
            assertEquals("Opening trace controls must not shrink the pads", padBounds, screen.padBounds())
            screen.capture("trace-menu-before-clear")
            assertTrue("A separate clear action must be discoverable", screen.device.hasObject(By.text(screen.text(R.string.trace_clear_all))))
            screen.click(By.text(screen.text(R.string.trace_lines)))
            assertFalse(prefs.traceLogClassic)
            screen.click(By.text(screen.text(R.string.trace_numbers)))
            assertTrue(prefs.traceLogClassic)
            assertEquals(listOf(0 to 0, 0 to 1, 0 to 0), screen.onMain { vm.traceLogSequence[0].toList() })
            assertEquals(listOf(0 to 2), screen.onMain { vm.traceLogSequence[1].toList() })
            screen.click(By.text(screen.text(R.string.trace_clear_all)))
            assertTrue(screen.onMain { vm.traceLogSequence.all { it.isEmpty() } })
            assertTrue("Clear must preserve recording on", screen.onMain { vm.scbTraceLog.checked })
            screen.device.pressBack()
            screen.await("Options did not close") { !screen.has(By.res("play_options")) }
            pressPad(0)
            screen.onMain { vm.scbTraceLog.setChecked(false) }
            assertTrue(screen.onMain { vm.traceLogSequence[1].isNotEmpty() })
            screen.clickDescription(R.string.menu)
            screen.scrollTo(By.text(screen.text(R.string.trace_clear_all)), Direction.DOWN)
            screen.await("Clear notice remained over the menu") { !screen.has(By.res(Pattern.compile(".*:id/snackbar_text"))) }
            screen.click(By.text(screen.text(R.string.trace_clear_all)))
            screen.await("Clear did not remove traces while recording was off") { screen.onMain { vm.traceLogSequence.all { it.isEmpty() } } }
            assertFalse("Clear must preserve recording off", screen.onMain { vm.scbTraceLog.checked })
            screen.capture("trace-menu-after-clear")
        } finally { prefs.traceLogClassic = old }
    }

    @Test fun openingPacksKeepsLoweredSystemVolumeAndPackLightDefaults() {
        val manager = screen.context.getSystemService(AudioManager::class.java)
        val old = manager.getStreamVolume(AudioManager.STREAM_MUSIC)
        try {
            manager.setStreamVolume(AudioManager.STREAM_MUSIC, 1, 0)
            File(screen.pack(), "autoPlay").writeText("delay 200\n")
            fun checkEnd(default: Boolean) {
                val vm = screen.vm()
                screen.onMain {
                    vm.switchPlayMode(PlayMode.AutoPlay)
                    vm.scbFeedbackLight.setChecked(!default)
                }
                screen.await("Auto play did not end") { screen.onMain { !vm.scbAutoPlay.checked } }
                assertEquals("Auto play end must restore the pack default", default, screen.onMain { vm.scbFeedbackLight.checked })
            }
            val withLights = screen.openPlay()
            assertFalse(screen.onMain { screen.vm().scbFeedbackLight.checked })
            assertEquals(1, manager.getStreamVolume(AudioManager.STREAM_MUSIC))
            checkEnd(false)
            withLights.close()
            File(screen.pack(), "keyLED").deleteRecursively()
            screen.openPlay()
            assertTrue(screen.onMain { screen.vm().scbFeedbackLight.checked })
            checkEnd(true)
            assertEquals(1, manager.getStreamVolume(AudioManager.STREAM_MUSIC))
            screen.capture("unlit-pack-lowered-volume")
        } finally { manager.setStreamVolume(AudioManager.STREAM_MUSIC, old, 0) }
    }
}
