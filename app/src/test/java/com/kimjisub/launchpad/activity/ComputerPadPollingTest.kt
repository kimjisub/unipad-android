package com.kimjisub.launchpad.activity

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ComputerPadPollingTest {
	@After fun tearDown() = Dispatchers.resetMain()

	@Test fun lightsArePolledOnlyWhileTheScreenIsStarted() = runTest {
		Dispatchers.setMain(StandardTestDispatcher(testScheduler))
		val owner = object : LifecycleOwner { override val lifecycle = LifecycleRegistry.createUnsafe(this) }
		owner.lifecycle.currentState = Lifecycle.State.STARTED
		var polls = 0
		val job = backgroundScope.launch { pollWhileStarted(owner.lifecycle, 16) { polls++ } }
		advanceTimeBy(1_000)
		runCurrent()
		val started = polls
		assertEquals(63, started)

		// Home or the lock screen: the activity stops but is not destroyed.
		owner.lifecycle.currentState = Lifecycle.State.CREATED
		runCurrent()
		advanceTimeBy(10_000)
		runCurrent()
		assertEquals(started, polls)

		owner.lifecycle.currentState = Lifecycle.State.STARTED
		advanceTimeBy(160)
		runCurrent()
		assertEquals(started + 11, polls)
		job.cancel()
	}
}
