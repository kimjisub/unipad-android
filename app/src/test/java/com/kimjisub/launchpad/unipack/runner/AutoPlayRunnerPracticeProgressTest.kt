package com.kimjisub.launchpad.unipack.runner

import android.os.SystemClock
import com.kimjisub.launchpad.unipack.UniPack
import com.kimjisub.launchpad.unipack.UniPackFolder
import com.kimjisub.launchpad.unipack.struct.AutoPlay
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.coroutines.CoroutineContext

/** Runs the real loop on a controlled clock, including its idle wakeups and final callbacks. */
@OptIn(ExperimentalCoroutinesApi::class)
class AutoPlayRunnerPracticeProgressTest {
    @get:Rule val tmp = TemporaryFolder()
    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val chain = ChainObserver().apply { range = 0 until 3 }
    private lateinit var runner: AutoPlayRunner
    private val hints = mutableSetOf<Pair<Int, Int>>()
    private val velocities = mutableMapOf<Pair<Int, Int>, Int>()
    private val guideEvents = mutableListOf<Triple<Int, Int, Long>>()
    private val chainHints = mutableListOf<Int>()
    private val presses = mutableListOf<Pair<Int, Int>>()
    /** Guide hints as (x, y, target time, time the hint was given). */
    private val guideLog = mutableListOf<List<Long>>()
    /** Controller guide lights as (x, y, velocity, time sent). */
    private val ledLog = mutableListOf<List<Long>>()
    private var ended = false
    /** Moves the wall clock without running the loop, as when its next pass is late. */
    private var clockSkew = 0L
    private val now get() = scheduler.currentTime + clockSkew

    @Before fun setUp() {
        mockkStatic(SystemClock::class)
        every { SystemClock.elapsedRealtime() } answers { now }
    }

    @After fun tearDown() {
        if (::runner.isInitialized) runner.stop()
        scheduler.runCurrent()
        scope.cancel()
        unmockkStatic(SystemClock::class)
    }

    private fun start(elements: List<AutoPlay.Element>, step: Boolean = false, delivery: CoroutineDispatcher = dispatcher) {
        val pack = mockk<UniPack>(relaxed = true)
        every { pack.autoPlayTable } returns AutoPlay(ArrayList(elements))
        start(pack, step, delivery)
    }

    private fun start(pack: UniPack, step: Boolean = false, delivery: CoroutineDispatcher = dispatcher) {
        val listener = object : AutoPlayRunner.Listener {
            override fun onStart() {}
            override fun onPadTouchOn(x: Int, y: Int, c: Int, num: Int, atMs: Long) { presses += x to y }
            override fun onPadTouchOff(x: Int, y: Int, atMs: Long) {}
            override fun onChainChange(c: Int) { chain.value = c }
            override fun onGuidePadOn(x: Int, y: Int, targetWallTimeMs: Long) {
                hints += x to y
                guideEvents += Triple(x, y, targetWallTimeMs)
                guideLog += listOf(x.toLong(), y.toLong(), targetWallTimeMs, now)
            }
            override fun onGuidePadOff(x: Int, y: Int) { hints -= x to y }
            override fun onGuideLedUpdate(x: Int, y: Int, velocity: Int) {
                velocities[x to y] = velocity
                ledLog += listOf(x.toLong(), y.toLong(), velocity.toLong(), now)
            }
            override fun onGuideChainOn(c: Int) { chainHints += c }
            override fun onRemoveGuide() { hints.clear() }
            override fun chainButsRefresh() {}
            override fun onProgressUpdate(progress: Int) {}
            override fun onEnd() { ended = true }
        }
        runner = AutoPlayRunner(pack, listener, chain, eventDispatcher = delivery)
        // Only execution scheduling is replaced; the parser input, loop and all callbacks stay real.
        AutoPlayRunner::class.java.getDeclaredField("scope").apply { isAccessible = true }.set(runner, scope)
        runner.practiceGuide = true
        runner.stepMode = step
        runner.playmode = !step
        runner.launch()
        tick(10)
    }

    private fun tick(ms: Long = 1) {
        scheduler.advanceTimeBy(ms)
        scheduler.runCurrent()
    }

    private fun on(x: Int, c: Int = 0) = AutoPlay.Element.On(x, 0, c, 0)

    @Test fun aFinalTouchWaitsUntilItsPadIsPlayed() {
        start(listOf(on(0)), step = true)
        assertEquals(setOf(0 to 0), hints)
        assertFalse("Lighting the last pad must not end practice", ended)
        assertTrue(runner.active)
        runner.stepPadPressed(0, 0)
        tick()
        assertTrue("Practice must end after the last pad is played", ended)
        assertTrue(hints.isEmpty())
        assertEquals(0, velocities[0 to 0])
    }

    @Test fun theFinalChordWaitsForEveryPadAndIgnoresOtherPads() {
        start(listOf(on(0), on(1)), step = true)
        runner.stepPadPressed(2, 0)
        tick()
        assertFalse("An unrelated pad must not finish the last chord", ended)
        assertEquals(setOf(0 to 0, 1 to 0), hints)
        runner.stepPadPressed(0, 0)
        tick()
        assertFalse("One pad must not finish a two-pad chord", ended)
        assertEquals(setOf(1 to 0), hints)
        runner.stepPadPressed(1, 0)
        tick()
        assertTrue(ended)
        assertTrue(hints.isEmpty())
        assertTrue(velocities.values.all { it == 0 })
    }

    @Test fun aTrailingReleaseStillWaitsForTheFinalPress() {
        start(listOf(on(0), AutoPlay.Element.Off(0, 0, 0)), step = true)
        assertFalse("A trailing release is not proof the last pad was played", ended)
        runner.stepPadPressed(0, 0)
        tick()
        assertTrue(ended)
        assertTrue(hints.isEmpty())
    }

    @Test fun stoppingStepPracticeClearsTheUnplayedHintAndControllerLight() {
        start(listOf(on(0), AutoPlay.Element.Delay(1000), on(1)), step = true)
        assertEquals(setOf(0 to 0), hints)
        runner.stop()
        tick()
        assertTrue(ended)
        assertTrue("Stopping must clear the outstanding screen hint", hints.isEmpty())
        assertEquals("Stopping must send controller light off", 0, velocities[0 to 0])
    }

    private fun song() = listOf(
        AutoPlay.Element.Delay(200), on(0), AutoPlay.Element.Off(0, 0, 0),
        AutoPlay.Element.Delay(1000), on(1), AutoPlay.Element.Off(1, 0, 0),
        AutoPlay.Element.Delay(1000), on(2), AutoPlay.Element.Delay(60000), AutoPlay.Element.Off(2, 0, 0),
    )

    @Test fun seekingForwardRebuildsTheGuideAtTheNewSongTime() {
        start(song())
        assertEquals(setOf(0 to 0), hints)
        val previous = guideEvents.size
        runner.progressOffset(5) // From the delay's next command (1) to delay (6), at song time 1200.
        tick()
        assertTrue("The old position must no longer be hinted", hints.isEmpty())
        tick(200)
        assertEquals(listOf(Triple(2, 0, 1010L)), guideEvents.drop(previous))
        assertEquals(setOf(2 to 0), hints)
        assertEquals(7, runner.progress)
    }

    @Test fun seekingBackwardRestartsGuideTimingFromTheChosenPosition() {
        start(song())
        tick(2500)
        assertTrue("Seeking is exercised while practice is still running", runner.active)
        val previous = guideEvents.size
        val seekTime = scheduler.currentTime
        runner.progressOffset(-runner.progress)
        tick()
        assertEquals(listOf(Triple(0, 0, seekTime + 200)), guideEvents.drop(previous))
        assertEquals(setOf(0 to 0), hints)
    }

    @Test fun seekingAwayFromAChainWaitGuidesTheNewPosition() {
        start(listOf(on(0, c = 1), AutoPlay.Element.Delay(2000), on(1), AutoPlay.Element.Delay(60000)))
        assertEquals(listOf(1), chainHints)
        runner.progressOffset(2)
        tick()
        assertEquals("The old chain wait must not block the new guide", listOf(Triple(1, 0, 10L)), guideEvents)
        assertTrue(hints.isEmpty()) // The press at the selected position is already due.
    }
    @Test fun seekingWhileDeliveryWaitsDoesNotSkipTheSelectedDelay() {
        val deliveryClock = TestCoroutineScheduler()
        val delivery = StandardTestDispatcher(deliveryClock)
        start(song(), delivery = delivery)
        deliveryClock.runCurrent()
        scheduler.runCurrent()
        tick(200)
        assertEquals("The first press is waiting for delivery", 1, runner.progress)
        runner.progressOffset(-runner.progress)
        deliveryClock.runCurrent()
        scheduler.runCurrent()
        tick()
        deliveryClock.runCurrent()
        scheduler.runCurrent()
        assertEquals("The selected delay must be consumed on its rebuilt clock", 1, runner.progress)
        assertEquals(Triple(0, 0, 410L), guideEvents.last())
    }
    @Test fun resumingAnEndedRunnerDoesNotKeepTheNextPracticeAlive() {
        start(listOf(on(0)), step = true)
        runner.stepPadPressed(0, 0)
        tick()
        assertTrue(ended)
        runner.resume()
        runner.playmode = false
        runner.stepMode = true
        runner.launch()
        tick()
        assertEquals(setOf(0 to 0), hints)
        assertTrue(runner.active)
        runner.stepPadPressed(0, 0)
        tick()
        assertFalse("A resume after completion must not leak into the next launch", runner.active)
    }

    @Test fun redundantResumeKeepsTheExistingChainWaitTime() {
        start(listOf(AutoPlay.Element.Delay(500), on(0, c = 1), AutoPlay.Element.Delay(60000)))
        assertEquals(listOf(1), chainHints)
        tick(2000)
        runner.resume()
        tick()
        val selectionTick = scheduler.currentTime + 1
        chain.value = 1
        tick()
        tick()
        assertEquals("Repeated resume must preserve time already spent waiting for the chain",
            selectionTick + 500, guideEvents.last().third)
    }

    /** A 2x2, one-chain pack with a silent sound on each pad the song uses, read by the real parser. */
    private fun guideSongPack(): UniPack {
        val root = tmp.newFolder()
        File(root, "info").writeText("title=Guide\nproducerName=Test\nbuttonX=2\nbuttonY=2\nchain=1\nsquareButton=true\n")
        File(root, "sounds").mkdir()
        File(root, "sounds/silent.wav").writeBytes(byteArrayOf(0))
        File(root, "keySound").writeText("1 1 1 silent.wav\n1 1 2 silent.wav\n1 2 1 silent.wav\n")
        File(root, "autoPlay").writeText("d 1000\no 1 1\nf 1 1\nd 1000\no 1 2\nf 1 2\nd 1000\no 2 1\nf 2 1\nd 1000\n")
        return UniPackFolder(root).load().loadDetail()
    }

    private val songNotes = listOf(Triple(0, 0, 1000L), Triple(0, 1, 2000L), Triple(1, 0, 3000L))

    /**
     * Pauses timed guidance at [pauseAt] song time, which shows the next note as a single step, then
     * resumes after 2000 ms, with or without that note pressed first. When [loopRanUntilPause] is false
     * the clock jumps there without a loop pass, so a note already due has not been handled yet.
     */
    private fun pauseGuideAndResume(pauseAt: Long, loopRanUntilPause: Boolean, pressed: Boolean) {
        start(guideSongPack())
        assertEquals("The first delay has been read", 1, runner.progress)
        if (loopRanUntilPause) tick(pauseAt - now) else clockSkew = pauseAt - scheduler.currentTime
        // The screen's pause control: timed guidance becomes step practice.
        runner.playmode = false
        runner.stepMode = true
        tick()
        val shown = songNotes.first { it.third > pauseAt || !loopRanUntilPause }
        val step = shown.first to shown.second
        assertEquals("The paused step shows the next unplayed note", setOf(step), hints)
        assertEquals(21, velocities[step])
        if (pressed) {
            runner.stepPadPressed(step.first, step.second)
            scheduler.runCurrent()
            assertTrue("The pressed step leaves the screen", step !in hints)
            assertEquals("The pressed step's light goes out", 0, velocities[step])
        }
        tick(pauseAt + 2000 - now)
        val resumedAt = now
        assertEquals(pauseAt + 2000, resumedAt)
        val logged = guideLog.size
        runner.resume()
        scheduler.runCurrent()
        val immediate = guideLog.drop(logged)
        repeat(4000) { tick() }

        val owedFrom = songNotes.indexOfFirst { it.first to it.second == step } + if (pressed) 1 else 0
        // A pressed note anchors the written gap after it to the resume; an unpressed one keeps the paused song time.
        val songAtResume = if (pressed) songNotes[owedFrom - 1].third else pauseAt
        // A note already due is shown at once, for one light update interval, without moving the song clock.
        val expected = songNotes.drop(owedFrom).map { (x, y, at) ->
            listOf(x.toLong(), y.toLong(), maxOf(resumedAt + at - songAtResume, resumedAt + LATE_HINT_MS))
        }
        assertEquals("Hints after resume, in order (x, y, target)", expected, guideLog.drop(logged).map { it.take(3) })
        val overdue = songNotes.drop(owedFrom).filter { resumedAt + it.third - songAtResume <= resumedAt }
        assertEquals("Only the first note is late, and only when it was neither handled nor pressed",
            pauseAt > 1000 && !loopRanUntilPause && !pressed, overdue.isNotEmpty())
        assertEquals("A note already due is hinted on the resume pass itself",
            expected.take(overdue.size), immediate.map { it.take(3) }.filter { it in expected.take(overdue.size) })
        overdue.forEach { (x, y) -> assertLateHintShown(x, y, resumedAt) }
        assertTrue("Timed guidance never sounds a pad", presses.isEmpty())
        assertTrue("The song ends", ended)
        assertTrue("No screen hint is left", hints.isEmpty())
        assertTrue("Every controller light is off: $velocities", velocities.values.all { it == 0 })
    }

    /** The late pad's light is sent lit on the resume pass and goes out one update interval later. */
    private fun assertLateHintShown(x: Int, y: Int, resumedAt: Long) {
        val lights = ledLog.filter { it[0] == x.toLong() && it[1] == y.toLong() && it[3] >= resumedAt }
        val lit = lights.firstOrNull { it[2] > 0 }
        assertEquals("The late pad is lit on the resume pass: $lights", resumedAt, lit?.get(3))
        val off = lights.first { it[2] == 0L && it[3] > resumedAt }
        assertEquals("The late pad goes out one update interval after resume: $lights", resumedAt + LATE_HINT_MS, off[3])
    }

    @Test fun resumingBeforeTheNoteKeepsTheUnpressedNote() = pauseGuideAndResume(500, loopRanUntilPause = true, pressed = false)
    @Test fun resumingBeforeTheNoteAfterPressingItKeepsTheGapAfterIt() = pauseGuideAndResume(500, loopRanUntilPause = true, pressed = true)
    @Test fun resumingBeforeAnUnshownNoteKeepsTheUnpressedNote() = pauseGuideAndResume(500, loopRanUntilPause = false, pressed = false)
    @Test fun resumingBeforeAnUnshownNoteAfterPressingItKeepsTheGapAfterIt() = pauseGuideAndResume(500, loopRanUntilPause = false, pressed = true)
    @Test fun resumingPastALateNoteHintsItAtOnce() = pauseGuideAndResume(1500, loopRanUntilPause = false, pressed = false)
    @Test fun resumingPastALateNoteAfterPressingItKeepsTheGapAfterIt() = pauseGuideAndResume(1500, loopRanUntilPause = false, pressed = true)
    @Test fun resumingAfterAHandledNoteKeepsTheNextUnpressedNote() = pauseGuideAndResume(1500, loopRanUntilPause = true, pressed = false)
    @Test fun resumingAfterAHandledNoteAndPressingTheNextKeepsTheGapAfterIt() = pauseGuideAndResume(1500, loopRanUntilPause = true, pressed = true)

    /**
     * The last note is late when guidance resumes, and nothing after it keeps the song running: its hint
     * is still shown for an update interval before the song's end clears the screen.
     */
    private fun lateLastNoteIsShownBeforeTheEnd(trailingDelayMs: Int?) {
        start(listOfNotNull(AutoPlay.Element.Delay(1000), on(0), AutoPlay.Element.Off(0, 0, 0), trailingDelayMs?.let { AutoPlay.Element.Delay(it) }))
        clockSkew = 1500 - scheduler.currentTime
        runner.playmode = false
        runner.stepMode = true
        tick()
        assertEquals(setOf(0 to 0), hints)
        tick(2000)
        val resumedAt = now
        runner.resume()
        scheduler.runCurrent()
        assertEquals("The late last note is on screen after the resume pass", setOf(0 to 0), hints)
        assertFalse("The song must not end while the late hint is shown", ended)
        tick(LATE_HINT_MS - 1)
        assertEquals(setOf(0 to 0), hints)
        tick()
        assertLateHintShown(0, 0, resumedAt)
        assertEquals(listOf(listOf(0L, 0L, resumedAt + LATE_HINT_MS)), guideLog.filter { it[3] >= resumedAt }.map { it.take(3) })
        tick()
        assertTrue("The song ends after the late hint", ended)
        assertTrue(hints.isEmpty())
        assertTrue(velocities.values.all { it == 0 })
        assertTrue(presses.isEmpty())
    }

    @Test fun aLateLastNoteWithoutATrailingDelayIsShownBeforeTheEnd() = lateLastNoteIsShownBeforeTheEnd(null)
    @Test fun aLateLastNoteWhoseTrailingDelayHasPassedIsShownBeforeTheEnd() = lateLastNoteIsShownBeforeTheEnd(100)

    /** Forwards to the test dispatcher except while held, as when the event thread is busy. */
    private class HeldDispatcher(private val target: CoroutineDispatcher) : CoroutineDispatcher() {
        var held = false
        private val queued = mutableListOf<Pair<CoroutineContext, Runnable>>()
        override fun dispatch(context: CoroutineContext, block: Runnable) {
            if (held) queued += context to block else target.dispatch(context, block)
        }
        fun release() {
            held = false
            val blocks = queued.toList()
            queued.clear()
            blocks.forEach { (context, block) -> target.dispatch(context, block) }
        }
    }

    @Test fun aPressedPadOfAPartlyPlayedChordIsNotShownAgainWhenPausedRightAfterResume() {
        val delivery = HeldDispatcher(dispatcher)
        start(listOf(AutoPlay.Element.Delay(1000), on(0), on(1), AutoPlay.Element.Delay(1000), on(2), AutoPlay.Element.Delay(1000)), delivery = delivery)
        tick(500 - now)
        runner.playmode = false
        runner.stepMode = true
        tick()
        assertEquals(setOf(0 to 0, 1 to 0), hints)
        runner.stepPadPressed(1, 0)
        tick()
        assertEquals("Only the unpressed pad of the chord is left", setOf(0 to 0), hints)
        val afterPress = guideLog.size
        fun pause() {
            runner.playmode = false
            runner.stepMode = true
        }
        // Paused again before the loop's first pass after resume, three times.
        repeat(3) {
            runner.resume()
            pause()
            tick()
            assertEquals("Pause before the resume pass, round ${it + 1}", setOf(0 to 0), hints)
        }
        // Paused again after the resume pass but before its commands are handled on the event thread.
        delivery.held = true
        runner.resume()
        scheduler.runCurrent()
        pause()
        delivery.release()
        tick()
        assertEquals("Pause before the resume pass's commands were handled", setOf(0 to 0), hints)
        assertTrue("The pressed pad is never shown again: ${guideLog.drop(afterPress)}",
            guideLog.drop(afterPress).none { it[0] == 1L && it[1] == 0L })
        tick(2000)
        val resumedAt = now
        val logged = guideLog.size
        runner.resume()
        repeat(3000) { tick() }
        // The pressed pad sets the song time to the chord; the unpressed pad is due at once.
        assertEquals(listOf(listOf(0L, 0L, resumedAt + LATE_HINT_MS), listOf(2L, 0L, resumedAt + 1000)),
            guideLog.drop(logged).map { it.take(3) })
        assertTrue(ended)
        assertTrue(hints.isEmpty())
        assertTrue(velocities.values.all { it == 0 })
        assertTrue(presses.isEmpty())
    }

    private companion object {
        /** The runner's guide light update interval. */
        const val LATE_HINT_MS = 50L
    }
}
