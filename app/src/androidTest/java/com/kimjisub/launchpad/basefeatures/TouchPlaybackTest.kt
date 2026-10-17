package com.kimjisub.launchpad.basefeatures

import android.graphics.Point
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import com.kimjisub.launchpad.manager.PreferenceManager
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kimjisub.launchpad.chainrelease.ChainReleaseCorpus
import com.kimjisub.launchpad.chainrelease.ChainReleaseCorpus.Pad
import com.kimjisub.launchpad.chainrelease.ChainReleaseReplay
import com.kimjisub.design.view.ChainView
import com.kimjisub.design.view.PadView
import com.kimjisub.launchpad.R
import com.kimjisub.launchpad.manager.ChannelManager.Channel
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TouchPlaybackTest : PlaybackScreenTest() {
    @Test fun padTouchRequestsSoundFromLoadedPack() {
        screen.openPlay()
        screen.tapPad(0)
        screen.await("Pad input did not request playback from the injected audio engine") { audio.plays.size == 1 }
        assertEquals(audio.loaded.getValue("silence.wav"), audio.plays.single())
        screen.await("keyLED did not reach the screen channel") { screen.onMain {
            screen.vm().channelManager.get(0, 0)?.let { it.channel == Channel.LED && it.code == 5 } == true
        } }
        val color = screen.onMain {
            val pad = screen.views(screen.resumed().window.decorView).filterIsInstance<PadView>().first()
            val led = pad.findViewById<android.view.View>(com.kimjisub.design.R.id.led)
            (led.background as android.graphics.drawable.ColorDrawable).color
        }
        assertEquals(com.kimjisub.launchpad.manager.LaunchpadColor.ARGB[5].toInt(), color)
        screen.capture("pad-keyled")
        screen.await("keyLED did not turn off") { screen.onMain { screen.vm().channelManager.get(0, 0)?.channel != Channel.LED } }
        val chains = screen.onMain {
            screen.views(screen.resumed().window.decorView).filterIsInstance<ChainView>().filter { it.width > 0 }.map(screen::bounds)
        }
        assertTrue("Both chains must be on screen", chains.size >= 2)
        val second = chains[1]
        screen.device.click(second.centerX(), second.centerY())
        screen.await("Chain button did not switch chain") { screen.onMain { screen.vm().chain.value == 1 } }
        screen.tapPad(0)
        assertEquals(audio.loaded.getValue("silence2.wav"), audio.plays.last())
    }

    /**
     * The options window's dimmed backdrop fades out for 0.3 s after the window closes. A pad pressed
     * then, such as the first step right after choosing step practice, must play rather than land on
     * the backdrop. Animations are slowed tenfold so the press always falls inside the fade.
     */
    @Test fun padPressedWhileTheClosedOptionsWindowFadesOutPlays() {
        val scale = "animator_duration_scale"
        val previous = screen.device.executeShellCommand("settings get global $scale").trim()
        screen.device.executeShellCommand("settings put global $scale 10")
        try {
            screen.openPlay()
            screen.options()
            val closed = SystemClock.elapsedRealtime()
            assertTrue("Back key injection failed", screen.device.pressKeyCode(android.view.KeyEvent.KEYCODE_BACK))
            screen.await("Back did not close the options window") { screen.onMain { !screen.vm().isOptionWindowVisible } }
            screen.tapPad(0)
            assertTrue("The press came after the fade had ended", SystemClock.elapsedRealtime() - closed < 3000)
            screen.await("A pad pressed while the options window faded out did not play") { audio.plays.size == 1 }
        } finally {
            screen.device.executeShellCommand(
                if (previous == "null") "settings delete global $scale" else "settings put global $scale $previous"
            )
        }
    }

    @Test fun twoSimultaneousFingersPlayBothPadsAndReleaseBoth() {
        screen.openPlay()
        screen.options()
        screen.clickText(R.string.feedbackLight)
        screen.device.pressBack()
        screen.await("Options remained open") { screen.onMain { !screen.vm().isOptionWindowVisible } }
        val pads = screen.padBounds()
        val downAt = SystemClock.uptimeMillis()
        val properties = Array(2) { i -> MotionEvent.PointerProperties().apply { id = i; toolType = MotionEvent.TOOL_TYPE_FINGER } }
        val coordinates = Array(2) { i -> MotionEvent.PointerCoords().apply {
            x = pads[i + 1].centerX().toFloat(); y = pads[i + 1].centerY().toFloat(); pressure = 1f; size = 1f
        } }
        fun inject(action: Int, count: Int) {
            val event = MotionEvent.obtain(downAt, SystemClock.uptimeMillis(), action, count, properties, coordinates,
                0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
            try { assertTrue("Touch injection failed", screen.instrumentation.uiAutomation.injectInputEvent(event, true)) }
            finally { event.recycle() }
        }
        inject(MotionEvent.ACTION_DOWN, 1)
        var activePointers = 1
        try {
            inject(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 2)
            activePointers = 2
            screen.await("Both fingers did not play") { audio.plays.size == 2 }
            assertEquals(listOf(audio.loaded.getValue("silence.wav"), audio.loaded.getValue("silence.wav")), audio.plays.toList())
            screen.await("Both pressed lights were not shown at once") { screen.onMain {
                (1..2).all { screen.vm().channelManager.get(0, it)?.channel == Channel.PRESSED }
            } }
            screen.capture("two-fingers")
            inject(MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 2)
            activePointers = 1
            inject(MotionEvent.ACTION_UP, 1)
            activePointers = 0
            screen.await("Released fingers still light pads") { screen.onMain {
                (1..2).all { screen.vm().channelManager.get(0, it)?.channel != Channel.PRESSED }
            } }
        } finally {
            if (activePointers > 0) inject(MotionEvent.ACTION_CANCEL, activePointers)
        }
        cancelledTouchesReleaseOnlyTheirPadsAndCanBePressedAgain()
        wholeTouchStreamCancellationReleasesBothPads()
        slideTouchCancellationStillReleasesBothPads()
    }

    /**
     * Every case of the chain-release corpus with fingers injected on the play screen, Slide Mode
     * off: lifting or cancelling a finger after the chain changed stops only the infinite loop that
     * finger started, and keeps the chain, the other fingers' loops and their press lights.
     */
    @Test fun fingerLiftedAfterChainChangeStopsOnlyTheLoopItStarted() {
        val corpus = ChainReleaseCorpus.load { InstrumentationRegistry.getInstrumentation().context.assets.open(it) }
        android.util.Log.i(CHAIN_RELEASE, "suite=${ChainReleaseCorpus.SUITE} platform=android input=programmed-screen " +
            "evidence=engine requests only, nothing heard ${corpus.sha256}")
        val prefs = PreferenceManager(screen.context)
        val original = prefs.slideMode
        try {
            for (case in corpus.cases) {
                resetTouchFixture()
                val pack = screen.pack()
                pack.deleteRecursively()
                corpus.unpack(case, pack)
                openPlayWithPressLight(prefs, slide = false)
                val driver = ScreenDriver(capture = case.id in setOf("CR-001", "CR-003"))
                try {
                    ChainReleaseReplay(driver).run(case).forEach { android.util.Log.i(CHAIN_RELEASE, it) }
                } finally {
                    driver.fingers.cancelRemaining()
                }
            }
        } finally {
            prefs.slideMode = original
        }
    }

    /** The play screen as a [ChainReleaseReplay.Driver]: injected fingers, real time, recorded engine requests. */
    private inner class ScreenDriver(private val capture: Boolean) : ChainReleaseReplay.Driver {
        val fingers = Fingers(screen)
        private val pointers = HashMap<String, Int>()
        private var startedAt = -1L

        override val virtualTime = false

        override fun advanceTo(atMs: Long) {
            if (startedAt < 0) startedAt = SystemClock.uptimeMillis()
            val wait = startedAt + atMs - SystemClock.uptimeMillis()
            if (wait > 0) SystemClock.sleep(wait)
        }

        override fun press(inputId: String, pad: Pad) {
            pointers[inputId] = fingers.down(center(pad.x * GRID + pad.y))
        }

        // The system refuses events for a finger it already ended, so a stale release cannot reach the screen.
        override fun release(inputId: String, cancelled: Boolean, stale: Boolean): Boolean {
            if (stale) return false
            val id = pointers.remove(inputId)!!
            if (cancelled && pointers.isEmpty()) fingers.cancelRemaining() else fingers.up(id, cancelled)
            return true
        }

        override fun tapChain(chain: Int) {
            fingers.up(fingers.down(chainButton(chain)))
        }

        override fun await(problem: () -> String?) {
            var last: String? = null
            screen.await({ last ?: "" }) { problem().also { last = it } == null }
        }

        override fun settle() {
            screen.instrumentation.waitForIdleSync()
            SystemClock.sleep(SETTLE_MS)
            screen.instrumentation.waitForIdleSync()
        }

        override fun chain() = screen.onMain { screen.vm().chain.value }

        override fun pressedPads() = screen.onMain {
            val channels = screen.vm().channelManager
            (0 until GRID).flatMap { x -> (0 until GRID).map { y -> Pad(x, y) } }
                .filter { channels.get(it.x, it.y)?.channel == Channel.PRESSED }
                .toSet()
        }

        override fun plays(): List<Pair<String, Int>> {
            val files = audio.loaded.entries.associate { (file, id) -> id to file }
            return audio.plays.indices.map { files.getValue(audio.plays[it]) to audio.loops[it] }
        }

        override fun stops(): List<Int> = audio.stoppedVoices.toList()

        override fun stepChecked(name: String) {
            if (capture) captureAfterDraw("chain-release-$name")
        }
    }

    private fun cancelledTouchesReleaseOnlyTheirPadsAndCanBePressedAgain() {
        resetTouchFixture()
        android.util.Log.i("TouchCancellation", "cancelledTouchesReleaseOnlyTheirPadsAndCanBePressedAgain")
        withTouchFixture(slide = false, finitePad = true) { pads, fingers ->
            fun start(index: Int): Int {
                val id = fingers.down(center(index))
                screen.await("Pad $index was not pressed with a running LED") { pressed(index) }
                assertEquals(-1, audio.loops.last())
                return id
            }
            start(1)
            val voice = audio.plays.size
            val before = audio.stoppedVoices.size
            fingers.cancelRemaining()
            checkThenCapture("single-pad-cancel") {
                screen.await("Cancelled pad 1 still has a pressed light or running LED") { released(pads, 1) }
                assertEquals(listOf(voice), audio.stoppedVoices.drop(before))
            }
            val after = audio.stoppedVoices.size
            repeatCancelAndUp(fingers)
            assertEquals("Repeated cancel/up stopped another voice", after, audio.stoppedVoices.size)
            val repressed = start(1)
            fingers.up(repressed)
            screen.await("Repressed pad did not release") { released(pads, 1) }
            assertEquals(audio.plays.size, audio.stoppedVoices.last())

            val other = start(2)
            val otherVoice = audio.plays.size
            val cancelled = start(1)
            val cancelledVoice = audio.plays.size
            val stops = audio.stoppedVoices.size
            fingers.up(cancelled, cancelled = true)
            screen.await("Individually cancelled pad did not release") { released(pads, 1) }
            assertEquals(listOf(cancelledVoice), audio.stoppedVoices.drop(stops))
            assertTrue("The other pad must stay held", pressed(2))
            captureAfterDraw("one-of-two-pads-cancel")
            val afterIndividualCancel = audio.stoppedVoices.size
            fingers.redeliverEnd()
            fingers.redeliverEnd(flags = 0)
            assertEquals("Repeated individual cancellation requested another stop", afterIndividualCancel, audio.stoppedVoices.size)
            assertTrue("Repeated individual cancellation released the other pad", pressed(2))
            val again = start(1)
            fingers.up(again)
            assertFalse("Repress/release stopped the other voice", audio.stoppedVoices.drop(stops).contains(otherVoice))
            assertTrue("The other pad must stay held", pressed(2))
            fingers.up(other)
            screen.await("Other pad did not release") { released(pads, 2) }
            assertEquals(otherVoice, audio.stoppedVoices.last())

            // Finite samples follow the existing UP rule: releasing does not stop their voice.
            for (cancel in listOf(false, true)) {
                val requested = audio.plays.size
                val id = fingers.down(center(3))
                // soundOn stops the pad's previous voice before it plays, so wait for this press's own request.
                screen.await("Finite sample did not play") { audio.plays.size == requested + 1 && audio.loops.last() == 0 }
                val count = audio.stoppedVoices.size
                if (cancel) fingers.cancelRemaining() else fingers.up(id)
                screen.await("Finite sample pad stayed pressed") { released(pads, 3) }
                assertEquals("Finite sound was cut off", count, audio.stoppedVoices.size)
            }
            android.util.Log.i("TouchCancellation", "per-pad plays=${audio.plays} stops=${audio.stoppedVoices}")
        }
    }

    private fun wholeTouchStreamCancellationReleasesBothPads() = checkWholeCancellation(slide = false)

    private fun slideTouchCancellationStillReleasesBothPads() = checkWholeCancellation(slide = true)

    private fun checkWholeCancellation(slide: Boolean) {
        resetTouchFixture()
        android.util.Log.i("TouchCancellation", "wholeTouchStreamCancellation slide=$slide")
        withTouchFixture(slide) { pads, fingers ->
            fingers.down(center(1))
            fingers.down(center(2))
            screen.await("Two looping pads did not start") { audio.plays.size == 2 && (1..2).all { pressed(it) } }
            assertEquals(listOf(-1, -1), audio.loops.toList())
            captureAfterDraw(if (slide) "slide-cancel-before" else "whole-cancel-before")
            val stopCount = audio.stoppedVoices.size
            fingers.cancelRemaining()
            checkThenCapture(if (slide) "slide-cancel-after" else "whole-cancel-after") {
                screen.await("Whole cancellation left lights or LED execution") { (1..2).all { released(pads, it) } }
                assertEquals(setOf(1, 2), audio.stoppedVoices.drop(stopCount).toSet())
                assertEquals(2, audio.stoppedVoices.size - stopCount)
            }
            val after = audio.stoppedVoices.size
            repeatCancelAndUp(fingers)
            assertEquals("Repeated cancel/up requested another stop", after, audio.stoppedVoices.size)
            val id = fingers.down(center(1))
            screen.await("Repress after cancellation did not play") { audio.plays.size == 3 && pressed(1) }
            fingers.up(id)
            screen.await("Repress after cancellation did not release") { released(pads, 1) }
            android.util.Log.i("TouchCancellation", "whole-cancel slide=$slide plays=${audio.plays} stops=${audio.stoppedVoices}")
        }
    }

    private fun withTouchFixture(slide: Boolean, finitePad: Boolean = false, check: (List<PadView>, Fingers) -> Unit) {
        val prefs = PreferenceManager(screen.context)
        val original = prefs.slideMode
        val fingers = Fingers(screen)
        try {
            val pack = screen.pack()
            File(pack, "keySound").writeText("1 1 2 silence.wav 0\n1 1 3 silence2.wav 0\n" +
                if (finitePad) "1 1 4 silence.wav\n" else "")
            for (column in 2..3) File(pack, "keyLED/1 1 $column 0").writeText("o 1 $column a 5\nd 100\n")
            openPlayWithPressLight(prefs, slide)
            check(screen.onMain { screen.views(screen.resumed().window.decorView).filterIsInstance<PadView>() }, fingers)
        } finally {
            try { fingers.cancelRemaining() } finally { prefs.slideMode = original }
        }
    }

    /** After a whole cancel, delivers it again and then a lift of its first finger, as a defensive duplicate. */
    private fun repeatCancelAndUp(fingers: Fingers) {
        fingers.redeliverEnd()
        fingers.redeliverEnd(MotionEvent.ACTION_UP, pointers = 1)
    }

    private fun center(index: Int) = screen.padBounds()[index].let { Point(it.centerX(), it.centerY()) }

    /**
     * The pad shows its press light and its keyLED runs. Every press here waits for this before it
     * lifts: the LED runner starts a pressed pad's keyLED on its next tick, and a lift before that
     * tick does not stop it.
     */
    private fun pressed(index: Int) = screen.onMain {
        screen.vm().channelManager.get(0, index)?.channel == Channel.PRESSED &&
            screen.vm().ledRunner!!.isEventExist(0, index)
    }

    private fun released(pads: List<PadView>, index: Int) = screen.onMain {
        val channel = screen.vm().channelManager.get(0, index)?.channel
        channel != Channel.PRESSED && channel != Channel.LED &&
            !screen.vm().ledRunner!!.isEventExist(0, index) && ledIsOff(pads[index])
    }

    private fun ledIsOff(pad: PadView): Boolean {
        val led = pad.findViewById<View>(com.kimjisub.design.R.id.led)
        return (led.background as? android.graphics.drawable.ColorDrawable)?.color == 0
    }

    /** Runs [check] and captures [name] after it; a failed capture never hides the check's own failure. */
    private fun checkThenCapture(name: String, check: () -> Unit) {
        var failure: Throwable? = null
        try {
            check()
        } catch (e: Throwable) {
            failure = e
            throw e
        } finally {
            try { captureAfterDraw(name) } catch (e: Throwable) { failure?.addSuppressed(e) ?: throw e }
        }
    }

    private fun captureAfterDraw(name: String) {
        val drawn = CountDownLatch(1)
        screen.onMain {
            val decor = screen.resumed().window.decorView
            decor.postOnAnimation { decor.postOnAnimation { drawn.countDown() } }
        }
        assertTrue("The screen did not draw its updated lights", drawn.await(10, TimeUnit.SECONDS))
        screen.capture(name)
    }

    private fun resetTouchFixture() {
        screen.close()
        screen.install()
        audio.resetPlaybackRequests()
    }

    private companion object {
        const val GRID = 8
        const val SETTLE_MS = 300L
        const val CHAIN_RELEASE = "ChainRelease"
    }
}
