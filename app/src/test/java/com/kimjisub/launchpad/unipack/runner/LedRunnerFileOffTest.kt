package com.kimjisub.launchpad.unipack.runner

import android.os.SystemClock
import com.kimjisub.launchpad.unipack.UniPack
import com.kimjisub.launchpad.unipack.struct.LedAnimation
import com.kimjisub.launchpad.unipack.struct.LedAnimation.LedEvent
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/** A later file on the same pad and chain can turn off a light left by an earlier file. */
class LedRunnerFileOffTest {
	private val clock = AtomicLong(1000)
	private val events = mutableListOf<String>()
	private lateinit var runner: LedRunner
	private val loopMethod = LedRunner::class.java.getDeclaredMethod("loop").apply { isAccessible = true }
	private val presses = AtomicInteger(0)
	private val chain = ChainObserver().apply { range = 0..1 }

	@Before fun setUp() {
		mockkStatic(SystemClock::class)
		every { SystemClock.elapsedRealtime() } answers { clock.get() }
	}
	@After fun tearDown() { runner.stop(); unmockkStatic(SystemClock::class) }

	private fun setUpRunner(lightX: Int = 1, lightY: Int = 1) {
		val unipack = mockk<UniPack>(relaxed = true)
		every { unipack.buttonX } returns 8
		every { unipack.buttonY } returns 8
		every { unipack.ledGet(any(), any(), any()) } answers {
			if (presses.getAndIncrement() % 2 == 0) LedAnimation(arrayListOf(LedEvent.On(lightX, lightY)), 1, 0)
			else LedAnimation(arrayListOf(LedEvent.Off(lightX, lightY)), 1, 0)
		}
		runner = LedRunner(unipack, object : LedRunner.Listener {
			override fun onPadLedTurnOn(x: Int, y: Int, color: Int, velocity: Int) = record("on")
			override fun onPadLedTurnOff(x: Int, y: Int) = record("off")
			override fun onChainLedTurnOn(c: Int, color: Int, velocity: Int) = record("on")
			override fun onChainLedTurnOff(c: Int) = record("off")
			override fun onChainChange(c: Int) {}
		}, chain, loopDelay = 3_600_000L)
		runner.launch()
		Thread.sleep(150)
	}
	private fun record(type: String) = synchronized(events) { events += type }
	private fun run(from: Long, to: Long) { var t = from; while (t <= to) { clock.set(t); loopMethod.invoke(runner); t += 10 } }

	@Test fun secondFileTurnsOffWhatTheFirstLeftOn_afterTheFirstFinished() {
		setUpRunner()
		clock.set(1000); runner.eventOn(0, 0); run(1000, 4000)
		clock.set(4000); runner.eventOn(0, 0); run(4000, 6000)
		assertEquals(listOf("on", "off"), events)
	}

	@Test fun secondFileTurnsOffWhatTheFirstLeftOn_immediately() {
		setUpRunner()
		clock.set(1000); runner.eventOn(0, 0); run(1000, 1100)
		clock.set(1100); runner.eventOn(0, 0); run(1100, 2000)
		assertEquals(listOf("on", "off"), events)
	}
	@Test fun laterFileAlsoTurnsOffTheFirstFilesChainLight() {
		setUpRunner(-1, 0)
		runner.eventOn(0, 0); run(1000, 4000)
		runner.eventOn(0, 0); run(4000, 6000)
		assertEquals(listOf("on", "off"), events)
	}

	@Test fun laterFileFromAnotherInputCanTurnOffTheSamePadAndChain() {
		setUpRunner()
		runner.eventOn(0, 0, owner = "first"); run(1000, 4000)
		runner.eventOn(0, 0, owner = "second"); run(4000, 6000)
		assertEquals(listOf("on", "off"), events)
	}

	@Test fun offFileOnAnotherChainCannotEraseTheEarlierChainsLight() {
		setUpRunner()
		runner.eventOn(0, 0); run(1000, 4000)
		chain.value = 1
		runner.eventOn(0, 0); run(4000, 6000)
		assertEquals(listOf("on"), events)
	}

	@Test fun offFileOnAnotherPadCannotEraseTheEarlierPadsLight() {
		setUpRunner()
		runner.eventOn(0, 0); run(1000, 4000)
		runner.eventOn(1, 0); run(4000, 6000)
		assertEquals(listOf("on"), events)
	}

	@Test fun clearingAnInputStillOnlyClearsTheLightItOwns() {
		setUpRunner()
		runner.eventOn(0, 0, owner = "first"); run(1000, 4000)
		runner.clearOwner("second")
		assertEquals(listOf("on"), events)
		runner.clearOwner("first")
		assertEquals(listOf("on", "off"), events)
	}

}
