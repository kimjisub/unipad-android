package com.kimjisub.launchpad.activity

import org.junit.Assert.assertEquals
import org.junit.Test

class WindowFocusInterruptionTest {
    private var now = 0L
    private var interruptions = 0
    private val pending = linkedMapOf<Runnable, Long>()
    private val focus = WindowFocusInterruption(
        schedule = { task, delay -> pending[task] = now + delay },
        cancel = { pending.remove(it) },
        interrupt = { interruptions++ },
    )

    private fun advance(ms: Long) {
        now += ms
        pending.filterValues { it <= now }.keys.toList().forEach {
            pending.remove(it)
            it.run()
        }
    }

    @Test fun rotationFocusReturnsBeforeDeadlineWithoutInterrupting() {
        focus.onFocusChanged(false)
        advance(100)
        assertEquals(0, interruptions)
        focus.onFocusChanged(true)
        advance(1000)
        assertEquals(0, interruptions)
        assertEquals(0, pending.size)
    }

    @Test fun coveringWindowInterruptsAtTheDeadlineAndNeverResumes() {
        focus.onFocusChanged(false)
        advance(499)
        assertEquals(0, interruptions)
        advance(1)
        assertEquals(1, interruptions)
        focus.onFocusChanged(true)
        advance(1000)
        assertEquals(1, interruptions)
    }

    @Test fun repeatedLossDoesNotPostponeTheOriginalDeadline() {
        focus.onFocusChanged(false)
        advance(400)
        focus.onFocusChanged(false)
        advance(100)
        assertEquals(1, interruptions)
        assertEquals(0, pending.size)
    }

    @Test fun aNewLossAfterReturningGetsItsOwnDeadline() {
        focus.onFocusChanged(false)
        advance(100)
        focus.onFocusChanged(true)
        advance(100)
        focus.onFocusChanged(false)
        advance(300)
        assertEquals(0, interruptions)
        advance(200)
        assertEquals(1, interruptions)
    }

    @Test fun pausingOrDestroyingCancelsTheDelayedCallback() {
        focus.onFocusChanged(false)
        focus.cancelPending()
        advance(1000)
        assertEquals(0, interruptions)
        assertEquals(0, pending.size)
    }
}
