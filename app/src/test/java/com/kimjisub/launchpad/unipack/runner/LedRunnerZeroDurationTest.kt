package com.kimjisub.launchpad.unipack.runner

import com.kimjisub.launchpad.unipack.UniPack
import com.kimjisub.launchpad.unipack.struct.LedAnimation
import com.kimjisub.launchpad.unipack.struct.LedAnimation.LedEvent
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Test

class LedRunnerZeroDurationTest {
    private fun state(loop: Int, vararg events: LedEvent): LedRunner.LedAnimationState {
        val pack = mockk<UniPack>(relaxed = true)
        every { pack.buttonX } returns 8
        every { pack.buttonY } returns 8
        every { pack.ledGet(any(), any(), any()) } returns LedAnimation(arrayListOf(*events), loop, 0)
        val runner = LedRunner(pack, mockk(relaxed = true), ChainObserver())
        return runner.LedAnimationState(0, 0, 1000, 1)
    }

    // Advance a bounded number of steps: a regression must fail without spinning billions of times.
    private fun play(state: LedRunner.LedAnimationState, count: Int, now: Long = 1000): List<LedRunner.LedChange> {
        val changes = mutableListOf<LedRunner.LedChange>()
        repeat(count) {
            val step = requireNotNull(state.nextStep(now)) { "Missing event $it" }
            state.playEvent(step.at, changes)
        }
        return changes
    }

    @Test
    fun maximumRepeatSampleCompletesAndDeliversItsFinalStateInOneTick() {
        val pack = mockk<UniPack>(relaxed = true)
        every { pack.buttonX } returns 8
        every { pack.buttonY } returns 8
        every { pack.ledGet(any(), any(), any()) } returns LedAnimation(
            arrayListOf(LedEvent.On(0, 0, velocity = 5), LedEvent.Off(0, 0)), Int.MAX_VALUE, 0)
        val delivered = mutableListOf<LedRunner.LedChange>()
        val listener = mockk<LedRunner.Listener>(relaxed = true)
        every { listener.onLedChanges(any()) } answers { delivered.addAll(firstArg()) }
        val runner = LedRunner(pack, listener, ChainObserver())
        val state = runner.LedAnimationState(0, 0, 0, 1)
        val pending = LedRunner::class.java.getDeclaredField("ledAnimationStatesAdd").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        (pending.get(runner) as MutableList<LedRunner.LedAnimationState>).add(state)
        LedRunner::class.java.getDeclaredMethod("loop").apply { isAccessible = true }.invoke(runner)
        assertFalse(state.isPlaying)
        assertEquals(listOf(LedRunner.LedChange.PadOn(0, 0, -1, 5), LedRunner.LedChange.PadOff(0, 0)), delivered)
    }

    @Test
    fun maximumFiniteRepeatWithoutDelayFinishesInOneTickWithFinalPadAndChainLights() {
        val state = state(Int.MAX_VALUE, LedEvent.On(0, 0, velocity = 5), LedEvent.Off(0, 0),
            LedEvent.On(-1, 0, velocity = 9), LedEvent.On(1, 1, velocity = 13))
        assertEquals(listOf(LedRunner.LedChange.PadOn(0, 0, -1, 5), LedRunner.LedChange.PadOff(0, 0),
            LedRunner.LedChange.ChainLedOn(0, -1, 9), LedRunner.LedChange.PadOn(1, 1, -1, 13)), play(state, 4))
        assertFalse(state.isPlaying)
        assertNull(state.nextStep(1000))
    }

    @Test
    fun maximumFiniteRepeatWithZeroDelayFinishesAfterOnePass() {
        val state = state(Int.MAX_VALUE, LedEvent.On(0, 0, velocity = 5), LedEvent.Delay(0), LedEvent.Off(0, 0))
        assertEquals(listOf(LedRunner.LedChange.PadOn(0, 0, -1, 5), LedRunner.LedChange.PadOff(0, 0)), play(state, 3))
        assertFalse(state.isPlaying)
        assertNull(state.nextStep(1000))
    }

    @Test
    fun maximumFiniteRepeatWithOnlyZeroDelaysFinishesAfterOnePass() {
        val state = state(Int.MAX_VALUE, LedEvent.Delay(0))
        assertTrue(play(state, 1).isEmpty())
        assertFalse(state.isPlaying)
        assertNull(state.nextStep(1000))
    }

    @Test
    fun threeTimedRepeatsPlayEveryPassEvenWhenTheTickIsLate() {
        val state = state(3, LedEvent.On(0, 0, velocity = 5), LedEvent.Delay(10), LedEvent.Off(0, 0))
        val changes = mutableListOf<LedRunner.LedChange>()
        val times = mutableListOf<Long>()
        repeat(9) {
            val step = requireNotNull(state.nextStep(2000))
            times += step.at
            state.playEvent(step.at, changes)
        }
        assertEquals(listOf(1000L, 1000L, 1010L, 1010L, 1010L, 1020L, 1020L, 1020L, 1030L), times)
        assertEquals(List(3) { listOf(LedRunner.LedChange.PadOn(0, 0, -1, 5), LedRunner.LedChange.PadOff(0, 0)) }.flatten(), changes)
        assertFalse(state.isPlaying)
        assertNull(state.nextStep(2000))
    }

    @Test
    fun largeTimedRepeatStillPlaysItsFirstPassWithoutOverflowingTheTickLimit() {
        val state = state(Int.MAX_VALUE, LedEvent.On(0, 0), LedEvent.Delay(10))
        assertEquals(listOf(LedRunner.LedChange.PadOn(0, 0, -1, 4)), play(state, 2))
        assertTrue(state.isPlaying)
        assertNull(state.nextStep(1000))
        assertEquals(1010L, state.nextStep(1010)?.at)
    }

    @Test
    fun pausedZeroDurationPassStillFinishesAfterResume() {
        val state = state(3, LedEvent.On(0, 0), LedEvent.Off(0, 0))
        play(state, 1)
        state.resume(1000, 5000)
        assertEquals(listOf(LedRunner.LedChange.PadOff(0, 0)), play(state, 1, now = 6000))
        assertFalse(state.isPlaying)
        assertNull(state.nextStep(6000))
    }

    @Test
    fun endlessZeroDurationRepeatStillYieldsAndCanBeReleased() {
        val state = state(0, LedEvent.On(0, 0))
        assertEquals(2, play(state, 2).size)
        assertTrue(state.isPlaying)
        assertNull(state.nextStep(1000))
        state.shutDownAt(1000, 2)
        assertTrue(requireNotNull(state.nextStep(1000)).isShutdown)
        val changes = mutableListOf<LedRunner.LedChange>()
        state.shutDown(changes)
        assertEquals(listOf(LedRunner.LedChange.PadOff(0, 0)), changes)
    }
}
