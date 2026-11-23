package com.kimjisub.launchpad.basefeatures

import android.app.ActivityManager
import android.os.Process
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import com.kimjisub.design.view.ChainView
import com.kimjisub.launchpad.R
import com.kimjisub.launchpad.activity.PlayActivity
import com.kimjisub.launchpad.manager.ChannelManager
import com.kimjisub.launchpad.viewmodel.PlayActivityViewModel
import com.kimjisub.launchpad.viewmodel.PlayMode
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class TransportAndLifecycleTest : PlaybackScreenTest() {
    private fun hintedPads(): Set<Int> = screen.onMain {
        val lights = screen.vm().channelManager
        (0 until 64).filterTo(sortedSetOf()) { index ->
            val item = lights.get(index / 8, index % 8)
            item?.channel == ChannelManager.Channel.GUIDE && item.code > 0
        }
    }

    private fun hintedPad(): Int? = hintedPads().firstOrNull()

    private fun guidedChain(): Int? = screen.onMain {
        (0 until screen.vm().unipack.chain).firstOrNull {
            screen.vm().channelManager.get(-1, PlayActivityViewModel.CHAIN_INDEX_OFFSET + it)?.channel == ChannelManager.Channel.GUIDE
        }
    }

    private fun transportShown() = screen.has(By.desc(screen.text(R.string.cd_autoplay_play))) ||
        screen.has(By.desc(screen.text(R.string.cd_autoplay_pause)))

    private fun currentProgress() = screen.onMain { screen.vm().autoPlayProgress }

    /** The runner lights the hint before it moves the progress on, so both are awaited together. */
    private fun awaitHint(message: String, pad: Int?, chain: Int?, progress: Int) =
        awaitHints(message, setOfNotNull(pad), chain, progress)

    private fun awaitHints(message: String, pads: Set<Int>, chain: Int?, progress: Int) {
        screen.await({
            "$message: pads ${hintedPads()}, chain ${guidedChain()}, progress ${currentProgress()}, " +
                "mode ${screen.onMain { screen.vm().playMode }} (expected $pads, $chain, $progress)"
        }) {
            hintedPads() == pads && guidedChain() == chain && currentProgress() == progress
        }
    }

    /** Waits briefly so a press the runner should ignore has had its chance to advance practice. */
    private fun assertStillPractising(progress: Int, context: String) {
        SystemClock.sleep(300)
        screen.onMain {
            assertEquals("Practice ended $context", PlayMode.StepPractice, screen.vm().playMode)
            assertTrue("Practice runner stopped $context", screen.vm().autoPlayRunner!!.active)
            assertEquals("Practice moved $context", progress, screen.vm().autoPlayProgress)
        }
        assertTrue("Practice controls disappeared $context", transportShown())
    }

    @Test fun autoplayStartsPausesResumesAndStopsThroughScreenControls() {
        screen.openPlay()
        screen.chooseOption(R.string.autoPlay)
        screen.await("Autoplay did not start") { screen.onMain { screen.vm().isAutoPlayPlaying } && audio.plays.isNotEmpty() }
        screen.clickDescription(R.string.cd_autoplay_pause)
        screen.await("Autoplay did not pause") { screen.onMain { !screen.vm().isAutoPlayPlaying } }
        // Allow the progress callback already posted on the main queue to settle.
        screen.instrumentation.waitForIdleSync()
        SystemClock.sleep(100)
        val paused = screen.onMain { screen.vm().autoPlayProgress }
        val requests = audio.plays.size
        SystemClock.sleep(600)
        assertEquals(paused, screen.onMain { screen.vm().autoPlayProgress })
        assertEquals(requests, audio.plays.size)
        screen.clickDescription(R.string.cd_autoplay_play)
        screen.await("Autoplay did not resume") { screen.onMain { screen.vm().autoPlayProgress > paused } }
        screen.chooseOption(R.string.autoPlay)
        screen.await("Selecting active mode did not stop autoplay") { screen.onMain { screen.vm().playMode == PlayMode.None && !screen.vm().autoPlayRunner!!.active } }
        assertFalse(screen.has(By.desc(screen.text(R.string.cd_autoplay_pause))))
        screen.capture("autoplay-stopped")
    }

    @Test fun seekingInGuidePracticeMovesTheVisibleHintToTheNewPad() {
        File(screen.pack(), "autoPlay").writeText("d 700\no 1 1\n" + "d 500\n".repeat(39) + "d 700\no 1 2\nd 60000\no 1 3\n")
        screen.openPlay()
        screen.onMain { screen.vm().switchPlayMode(PlayMode.GuidePlay) }
        screen.await("Initial guide missing") { hintedPad() == 0 }
        screen.onMain { screen.vm().autoPlayRunner!!.playmode = false }
        screen.capture("seek-initial-guide")
        screen.onMain {
            val runner = screen.vm().autoPlayRunner!!
            runner.progressOffset(41 - runner.progress)
            runner.playmode = true
        }
        // The new hint lasts only the 700 ms until its pad is due, and a screenshot can take longer,
        // so read it first. The post-seek screen is still saved before the assertion, even on failure.
        SystemClock.sleep(150)
        val hinted = hintedPad()
        screen.capture("seek-moved-guide")
        assertEquals("Seeking must guide the pad at the new position", 1, hinted)
    }

    @Test fun manualChainSelectionKeepsItsChainAfterAWormholePad() {
        val keySound = File(screen.pack(), "keySound")
        keySound.writeText(keySound.readText().replaceFirst("1 1 1 silence.wav\n", "1 1 1 silence.wav 1 2\n"))
        screen.openPlay()
        val vm = screen.vm()
        screen.onMain {
            // Dispatch the pad and the real chain button callback in one main-thread task,
            // so the explicit selection definitely precedes the delayed move.
            vm.padTouch(0, 0, true)
            vm.padTouch(0, 0, false)
            val firstChain = screen.views(screen.resumed().window.decorView)
                .filterIsInstance<ChainView>().first { it.width > 0 }
            assertTrue("First chain button did not handle its click", firstChain.click())
        }
        screen.onMain { } // Start any queued wormhole delay before waiting past its deadline.
        SystemClock.sleep(200)
        screen.onMain { } // Drain main-thread work due before this marker.
        screen.capture("wormhole-after-chain-selection")
        assertEquals("A delayed wormhole replaced the selected chain", 0, screen.onMain { vm.chain.value })
    }

    @Test fun guideAndStepPracticeShowHintsAndStepWaitsForPadInput() {
        screen.openPlay()
        screen.chooseOption(R.string.guidePlay)
        screen.await("Guide mode did not start") { screen.onMain { screen.vm().playMode == PlayMode.GuidePlay } }
        screen.await("Guide did not illuminate a pad") { hintedPad() != null }
        screen.capture("guide-hint")
        screen.chooseOption(R.string.guidePlay)
        screen.await("Guide did not stop") { screen.onMain { screen.vm().playMode == PlayMode.None } }
        screen.chooseOption(R.string.stepPractice)
        screen.await("Step mode did not start") { screen.onMain { screen.vm().playMode == PlayMode.StepPractice } }
        screen.await("Step hint did not appear") { hintedPad() != null }
        val hint = hintedPad()!!
        val before = screen.onMain { screen.vm().autoPlayProgress }
        SystemClock.sleep(300)
        assertEquals("Practice advanced without a press", before, screen.onMain { screen.vm().autoPlayProgress })
        screen.tapPad(hint)
        screen.await("Practice did not advance after the expected pad") { screen.onMain { screen.vm().autoPlayProgress > before } }
        screen.capture("step-practice")
    }

    /**
     * The first autoPlay press is pad 1 1, whose sound moves to chain 40 of a two chain pack. That move goes
     * to the last chain, as on iOS and the web, and step practice carries on there. Kept raw, chain 40 lit a
     * chain button past the end of the round button table and closed the app on the next step.
     */
    @Test fun stepPracticeContinuesPastAMoveToAChainThePackLacks() {
        val keySound = File(screen.pack(), "keySound")
        keySound.writeText(keySound.readText().replaceFirst("1 1 1 silence.wav\n", "1 1 1 silence.wav 1 40\n"))
        screen.launch(PlayActivity::class.java) { putExtra("path", screen.pack().path) }
        screen.await("Pack did not load") { screen.ready() }
        if (screen.onMain { screen.vm().unipack.errorDetail != null }) {
            screen.node(By.text(screen.text(R.string.accept)))
            screen.capture("missing-chain-warning")
            screen.clickText(R.string.accept)
            assertTrue(screen.device.wait(Until.gone(By.text(screen.text(R.string.accept))), 5000))
        }
        val lastChain = screen.onMain { screen.vm().unipack.chain } - 1
        screen.chooseOption(R.string.stepPractice)
        screen.await("Step hint did not appear") { hintedPad() == 0 }
        screen.tapPad(0)
        screen.await({ "Step practice did not continue on the last chain, at 2 1: chain ${screen.onMain { screen.vm().chain.value }}, hint ${hintedPad()}" }) {
            hintedPad() == 8 && screen.onMain { screen.vm().chain.value } == lastChain
        }
        screen.await("The last chain's button is not lit as the selected chain") { selectedChainButton() == lastChain }
        assertEquals(
            "keySound : [1 1 1 silence.wav 1 40] wormhole is incorrect",
            screen.onMain { screen.vm().unipack.errorDetail },
        )
        screen.capture("missing-chain-moved-to-last")
        screen.tapPad(8)
        screen.await("Step practice did not move on to 3 1 on the last chain") { hintedPad() == 16 }
        assertEquals(lastChain, screen.onMain { screen.vm().chain.value })
        screen.capture("missing-chain-next-step")
    }

    private fun selectedChainButton(): Int? = screen.onMain {
        (0 until PlayActivityViewModel.MAX_CHAIN_BUTTONS).firstOrNull { c ->
            screen.vm().channelManager.get(-1, PlayActivityViewModel.CHAIN_INDEX_OFFSET + c)?.channel == ChannelManager.Channel.CHAIN
        }
    }

    @Test fun stepPracticeFollowsHintsAcrossChainsAndEndsOnlyAfterTheLastPad() {
        // Two pads on the first chain, then two on the second, one at a time.
        val autoPlay = listOf("o 1 1", "o 1 2", "c 2", "o 2 1", "o 2 2").joinToString("") {
            if (it.startsWith("c ")) "$it\n" else "$it\nd 200\nf ${it.substring(2)}\nd 200\n"
        }
        File(screen.pack(), "autoPlay").writeText(autoPlay)
        val elements = autoPlay.lines().filter(String::isNotEmpty)
        // Hinting a pad moves the progress past its press; a hint on another chain waits on that press.
        fun hinted(press: String) = elements.indexOf(press) + 1
        screen.openPlay()
        screen.chooseOption(R.string.stepPractice)
        screen.await("Step mode did not start") { screen.onMain { screen.vm().playMode == PlayMode.StepPractice } }
        screen.await({ "The short practice sequence was not loaded: ${screen.onMain { screen.vm().autoPlayProgressMax }}" }) {
            screen.onMain { screen.vm().autoPlayProgressMax } == elements.size
        }
        awaitHint("First hint was not the first pad", 0, null, hinted("o 1 1"))
        screen.capture("step-practice-first")
        pressPad(0)
        awaitHint("Second hint was not the second pad", 1, null, hinted("o 1 2"))
        assertStillPractising(hinted("o 1 2"), "after the first pad")

        pressPad(1)
        awaitHint("Second chain was not hinted", null, 1, hinted("o 2 1") - 1)
        pressPad(8)
        assertStillPractising(hinted("o 2 1") - 1, "after pressing the next pad on the wrong chain")
        assertEquals("Pressing on the wrong chain hinted a pad", null, hintedPad())
        assertEquals("Pressing on the wrong chain cleared the chain hint", 1, guidedChain())
        screen.capture("step-practice-chain-hint")

        val second = chainButton(1)
        assertTrue(screen.device.click(second.x, second.y))
        screen.await("Chain button did not switch chain") { screen.onMain { screen.vm().chain.value == 1 } }
        awaitHint("Third hint was not the first pad of the second chain", 8, null, hinted("o 2 1"))
        pressPad(8)
        awaitHint("Last hint was not the second pad of the second chain", 9, null, hinted("o 2 2"))
        pressPad(0)
        assertStillPractising(hinted("o 2 2"), "after pressing an unhinted pad before the last one")
        assertEquals("Pressing an unhinted pad moved the last hint", 9, hintedPad())
        screen.capture("step-practice-last")

        pressPad(9)
        screen.await("Practice did not end after the last pad") {
            screen.onMain {
                val vm = screen.vm()
                vm.playMode == PlayMode.None && !vm.autoPlayRunner!!.active && !vm.autoPlayControlVisible &&
                    vm.autoPlayProgress == elements.size
            }
        }
        screen.await("Practice controls stayed on screen") { !transportShown() }
        assertEquals("A hint stayed lit after practice ended", null, hintedPad())
        screen.capture("step-practice-complete")
    }

    /**
     * Steps through a whole song on a pack the size of a real one: 8 chains and an autoPlay of hundreds
     * of commands (see [LongPracticeSong]). Every hint must match the song's next group of pads on the
     * right chain, a group that opens another chain waits for its chain button, and practice ends only
     * once the last pad is pressed, with the app still running and responsive throughout.
     */
    @Test fun stepPracticeFollowsALongSongOverEightChainsToItsLastPad() {
        val song = LongPracticeSong()
        val pack = screen.pack()
        File(pack, "info").let { it.writeText(it.readText().replace(Regex("(?m)^chain=.*$"), "chain=${LongPracticeSong.CHAINS}")) }
        File(pack, "keySound").appendText(buildString {
            for (c in 3..LongPracticeSong.CHAINS) for (x in 1..8) for (y in 1..8) append("$c $x $y silence.wav\n")
        })
        File(pack, "autoPlay").writeText(song.script)
        val process = Process.myPid()
        val started = SystemClock.elapsedRealtime()
        screen.openPlay()
        assertEquals("The long pack did not load cleanly", null, screen.onMain { screen.vm().unipack.errorDetail })
        screen.chooseOption(R.string.stepPractice)
        screen.await("Step mode did not start") { screen.onMain { screen.vm().playMode == PlayMode.StepPractice } }
        screen.await({ "The long song was not loaded whole: ${screen.onMain { screen.vm().autoPlayProgressMax }} of ${song.elementCount}" }) {
            screen.onMain { screen.vm().autoPlayProgressMax } == song.elementCount
        }
        val last = song.steps.last()
        val lastPad = last.pads.last()
        var slowestHintMs = 0L
        var lastPress = SystemClock.elapsedRealtime()
        fun hinted(message: String, pads: Set<Int>, chain: Int?, progress: Int) {
            awaitHints(message, pads, chain, progress)
            slowestHintMs = maxOf(slowestHintMs, SystemClock.elapsedRealtime() - lastPress)
        }
        for ((number, step) in song.steps.withIndex()) {
            val label = "Step ${number + 1} of ${song.steps.size}"
            step.chainHintProgress?.let { progress ->
                hinted("$label did not hint chain ${step.chain + 1} first", emptySet(), step.chain, progress)
                if (step.chain == LongPracticeSong.CHAINS - 1) screen.capture("long-practice-chain-${step.chain + 1}-hint")
                val button = chainButton(step.chain)
                lastPress = SystemClock.elapsedRealtime()
                assertTrue(screen.device.click(button.x, button.y))
                screen.await({ "$label: chain button did not select chain ${step.chain + 1}: ${screen.onMain { screen.vm().chain.value } + 1}" }) {
                    screen.onMain { screen.vm().chain.value } == step.chain
                }
            }
            val remaining = step.pads.toMutableSet()
            hinted("$label did not hint the song's next pads", remaining, null, step.progress)
            screen.onMain {
                assertEquals("$label is on the wrong chain", step.chain, screen.vm().chain.value)
                assertTrue("$label: practice runner stopped", screen.vm().autoPlayRunner!!.active)
            }
            for (pad in step.pads) {
                if (step === last && pad == lastPad) break
                lastPress = SystemClock.elapsedRealtime()
                pressPad(pad)
                remaining -= pad
                if (remaining.isNotEmpty()) hinted("$label: the rest of the group was not left hinted", remaining, null, step.progress)
            }
        }
        assertStillPractising(last.progress, "before the last pad")
        assertEquals("The last pad is not the only one left", setOf(lastPad), hintedPads())
        screen.capture("long-practice-last")
        pressPad(lastPad)
        screen.await({ "Practice did not end after the last pad: progress ${currentProgress()} of ${song.elementCount}" }) {
            screen.onMain {
                val vm = screen.vm()
                vm.playMode == PlayMode.None && !vm.autoPlayRunner!!.active && !vm.autoPlayControlVisible &&
                    vm.autoPlayProgress == song.elementCount
            }
        }
        screen.await("Practice controls stayed on screen") { !transportShown() }
        assertEquals("A hint stayed lit after practice ended", emptySet<Int>(), hintedPads())
        assertEquals("The app process was replaced during practice", process, Process.myPid())
        val errors = screen.context.getSystemService(ActivityManager::class.java).processesInErrorState.orEmpty()
            .filter { it.pid == process }
        assertTrue("The app crashed or stopped responding: ${errors.map { "${it.condition} ${it.shortMsg}" }}", errors.isEmpty())
        screen.capture("long-practice-complete")
        println(
            "Long step practice: ${song.steps.size} steps, ${song.steps.sumOf { it.pads.size }} pads, " +
                "${song.steps.count { it.chainHintProgress != null }} chain moves, ${song.elementCount} commands, " +
                "slowest hint ${slowestHintMs} ms, ${SystemClock.elapsedRealtime() - started} ms in all"
        )
    }

    /** Drop activity-scoped state before rebuilding the stopped window; instrumentation survives. */
    @Test fun playingReturnsAfterBackgroundActivityDestruction() {
        if (android.os.Build.VERSION.SDK_INT < 33) throw AssertionError("Base features requires API 33+; no tests are skipped")
        screen.openPlay()
        pressPad(0)
        val original = screen.resumed() as PlayActivity
        val vm = screen.vm()
        val pack = screen.onMain { vm.unipack }
        val loadedBefore = audio.loaded.toMap()
        screen.capture("play-before-background-destruction")
        screen.goHome { screen.onMain { !vm.screenVisible } }
        screen.onMain {
            // A configuration recreation alone retains the store. Clear it to exercise the
            // cold viewmodel/pack loading path without killing the instrumented process.
            original.viewModelStore.clear()
            original.recreate()
        }
        screen.await("Stopped play activity was not destroyed behind Home") { screen.onMain { original.isDestroyed } }
        screen.bringToFront(original, expectSameInstance = false)
        val restored = screen.resumed()
        val restoredVm = screen.vm()
        assertNotSame("Returning kept the destroyed activity's viewmodel", vm, restoredVm)
        screen.await("Restored play screen did not finish loading", screen::ready)
        assertNotSame("Returning reused the old pack instead of loading it", pack, screen.onMain { restoredVm.unipack })
        assertEquals(FeatureScreen.TITLE, screen.onMain { restoredVm.unipack.title })
        assertEquals(screen.pack().path, screen.onMain { restored.intent.getStringExtra("path") })
        screen.node(By.desc(screen.text(R.string.menu)))
        screen.await("Restored play screen did not lay out all 64 pads") {
            screen.padBounds().let { pads -> pads.size == 64 && pads.all { it.width() > 0 && it.height() > 0 } }
        }
        screen.freshControls()
        pressPad(0)
        assertEquals(audio.loaded.getValue("silence.wav"), audio.plays.last())
        println("Background destruction returned with a new activity, viewmodel and pack; sounds before=$loadedBefore after=${audio.loaded}")
        screen.capture("play-after-background-destruction")
    }

    @Test fun playingSurvivesHomeAndScreenLockWithoutLosingPackOrChain() {
        if (android.os.Build.VERSION.SDK_INT < 33) throw AssertionError("Base features requires API 33+; no tests are skipped")
        screen.openPlay()
        val process = Process.myPid()
        val vm = screen.vm()
        screen.onMain { vm.chain.value = 1 }
        screen.tapPad(0)
        assertEquals(audio.loaded.getValue("silence2.wav"), audio.plays.last())
        val original = screen.resumed()
        val silenceCount = audio.silences.get()
        val stopCount = audio.stops.get()
        val loadedSounds = audio.loaded.toMap()
        screen.goHome { screen.onMain { !vm.screenVisible } }
        screen.await("Leaving play did not silence voices") { audio.silences.get() > silenceCount }
        // Android 17 mutes, or with strict hardening aborts, an app whose stream stays open unseen.
        screen.await("Leaving play left the audio stream open") { audio.stops.get() > stopCount }
        val startCount = audio.starts.get()
        screen.bringToFront(original)
        screen.await("Play activity did not resume") { screen.onMain { vm.screenVisible } }
        screen.await("Returning did not reopen the audio stream") { audio.starts.get() > startCount }
        assertEquals("Sounds were reloaded on return", loadedSounds, audio.loaded.toMap())
        assertSame(original, screen.resumed())
        assertSame(vm, screen.vm())
        assertEquals(1, screen.onMain { vm.chain.value })
        screen.device.sleep()
        screen.await("Lock did not hide play") { screen.onMain { !vm.screenVisible } }
        screen.device.wakeUp()
        screen.device.executeShellCommand("wm dismiss-keyguard")
        screen.await("Unlock did not return to play") { screen.onMain { vm.screenVisible } }
        assertEquals(process, Process.myPid())
        assertSame(vm, screen.vm())
        assertEquals(1, screen.onMain { vm.chain.value })
        pressPad(1)
        screen.capture("play-after-home-and-lock")
    }
}
