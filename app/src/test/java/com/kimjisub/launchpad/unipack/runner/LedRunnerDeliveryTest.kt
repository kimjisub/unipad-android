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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * The listener is the UI thread's side of the runner: it must be called after the runner's lock is
 * released, so eventOn/eventOff from a touch never wait for it, while the changes it receives keep the
 * exact order, timing and repeat count of the animation. Ticks are driven through the private loop()
 * at a controlled clock, as in [LedRunnerToggleTest].
 */
class LedRunnerDeliveryTest {

	private val clock = AtomicLong(1000)
	private val events = mutableListOf<String>()
	private val callsUnderLock = AtomicLong()
	private lateinit var runner: LedRunner
	private val loopMethod = LedRunner::class.java.getDeclaredMethod("loop").apply { isAccessible = true }

	/** Called with every change; lets a test hold the delivery open. */
	@Volatile
	private var onDelivery: () -> Unit = {}

	@Before
	fun setUp() {
		mockkStatic(SystemClock::class)
		every { SystemClock.elapsedRealtime() } answers { clock.get() }
	}

	@After
	fun tearDown() {
		runner.stop()
		unmockkStatic(SystemClock::class)
	}

	private fun setUpRunner(animationFor: (x: Int, y: Int) -> LedAnimation?) {
		val unipack = mockk<UniPack>(relaxed = true)
		every { unipack.buttonX } returns 8
		every { unipack.buttonY } returns 8
		every { unipack.ledGet(any(), any(), any()) } answers { animationFor(secondArg(), thirdArg()) }
		runner = LedRunner(unipack, object : LedRunner.Listener {
			override fun onPadLedTurnOn(x: Int, y: Int, color: Int, velocity: Int) = record("on", x, y)
			override fun onPadLedTurnOff(x: Int, y: Int) = record("off", x, y)
			override fun onChainLedTurnOn(c: Int, color: Int, velocity: Int) = record("con", -1, c)
			override fun onChainLedTurnOff(c: Int) = record("coff", -1, c)
			override fun onChainChange(c: Int) = record("chain", -1, c)
		}, ChainObserver(), loopDelay = 3_600_000L)
		clock.set(1000)
		runner.launch()
		Thread.sleep(150)
	}

	private fun record(type: String, x: Int, y: Int) {
		if (Thread.holdsLock(runner)) callsUnderLock.incrementAndGet()
		onDelivery()
		synchronized(events) { events += "$type($x,$y)@${clock.get()}" }
	}

	private fun tick(t: Long) {
		clock.set(t)
		loopMethod.invoke(runner)
	}

	private fun run(from: Long, to: Long, step: Long = 4) {
		var t = from
		while (t <= to) {
			tick(t)
			t += step
		}
	}

	private fun press(x: Int = 0, y: Int = 0) {
		runner.eventOn(x, y)
		tick(clock.get())
	}

	private fun release(x: Int = 0, y: Int = 0) {
		runner.eventOff(x, y)
		tick(clock.get())
	}

	private fun blink(on: Int, off: Int, loop: Int) = LedAnimation(
		arrayListOf(LedEvent.On(1, 1), LedEvent.Delay(on), LedEvent.Off(1, 1), LedEvent.Delay(off)), loop, 0,
	)

	/** Every pad on, then every pad off, with no wait: one pass is 128 changes played in a single tick. */
	private fun allPadsFlash(loop: Int): LedAnimation {
		val events = ArrayList<LedEvent>()
		for (x in 0 until 8) for (y in 0 until 8) events += LedEvent.On(x, y)
		for (x in 0 until 8) for (y in 0 until 8) events += LedEvent.Off(x, y)
		return LedAnimation(events, loop, 0)
	}

	private fun ons() = events.filter { it.startsWith("on") }

	@Test
	fun listenerIsCalledOutsideTheRunnerLock() {
		setUpRunner { _, _ -> blink(on = 50, off = 50, loop = 0) }
		press()
		run(1000, 1300)
		release()

		assertTrue(events.size > 5)
		assertEquals(0L, callsUnderLock.get())
	}

	@Test
	fun eventOn_doesNotWaitForASlowListener() {
		setUpRunner { _, _ -> allPadsFlash(loop = 1) }
		runner.eventOn(0, 0)
		tick(1000) // moves the press into the playing list; the next tick plays it

		val deliveryStarted = CountDownLatch(1)
		val releaseDelivery = CountDownLatch(1)
		val firstChange = AtomicBoolean(true)
		onDelivery = {
			if (firstChange.getAndSet(false)) {
				deliveryStarted.countDown()
				releaseDelivery.await(1, TimeUnit.SECONDS)
			}
		}
		val ledThread = Thread { tick(1004) }.apply { start() }
		assertTrue(deliveryStarted.await(2, TimeUnit.SECONDS))

		val started = System.nanoTime()
		runner.eventOn(7, 7)
		val waitedMs = (System.nanoTime() - started) / 1_000_000
		onDelivery = {}
		releaseDelivery.countDown()
		ledThread.join()

		assertTrue("eventOn waited ${waitedMs}ms for the listener", waitedMs < 500)
	}

	@Test
	fun blink50ms_withA112msStall_catchesUpInOrderWithoutSkippingOrDoubling() {
		setUpRunner { _, _ -> blink(on = 50, off = 50, loop = 0) }
		press()
		run(1000, 1200)
		run(1312, 1500) // the LED thread was stalled for 112ms

		assertEquals(
			listOf(
				"on(1,1)@1000", "off(1,1)@1052", "on(1,1)@1100", "off(1,1)@1152", "on(1,1)@1200",
				"off(1,1)@1312", "on(1,1)@1312", "off(1,1)@1352", "on(1,1)@1400", "off(1,1)@1452", "on(1,1)@1500",
			),
			events,
		)
	}

	@Test
	fun period6ms_for400ms_keepsTheBeat() {
		setUpRunner { _, _ -> blink(on = 3, off = 3, loop = 0) }
		press()
		run(1000, 1400)
		release()

		val onTimes = ons().map { it.substringAfter('@').toLong() }
		// Scheduled every 6ms from 1000; each plays on the first 4ms tick at or after its time.
		val expected = (0..66).map { 1000L + 6 * it }.map { (it + 3) / 4 * 4 }
		assertEquals(expected, onTimes)
		assertEquals("off(1,1)@1400", events.last())
		assertFalse(runner.isEventExist(0, 0))
	}

	@Test
	fun delaylessInfinite_playsOnePassPerTickAndStopsOnRelease() {
		setUpRunner { _, _ -> LedAnimation(arrayListOf(LedEvent.On(1, 1), LedEvent.Off(1, 1)), 0, 0) }
		press()
		run(1000, 1100)
		val perTick = events.groupingBy { it.substringAfter('@') }.eachCount()
		release()

		// Capped at one pass plus one change per tick, and never skipped.
		assertEquals((1000L..1100L step 4).map { it.toString() }, perTick.keys.toList())
		assertTrue(perTick.toString(), perTick.values.all { it in 1..3 })
		assertTrue(events.last().startsWith("off(1,1)@"))
		assertFalse(runner.isEventExist(0, 0))
	}

	@Test
	fun largeFiniteLoop_deliversEveryChangeInPlayOrder() {
		setUpRunner { _, _ -> allPadsFlash(loop = 3000) }
		press()
		tick(1004)
		tick(1008)

		assertEquals(128 * 3000, events.size)
		val pass = (0 until 8).flatMap { x -> (0 until 8).map { y -> "($x,$y)" } }
		val expectedPass = pass.map { "on$it@1004" } + pass.map { "off$it@1004" }
		for (i in events.indices) assertEquals("change $i", expectedPass[i % 128], events[i])
		assertEquals(0L, callsUnderLock.get())
		assertFalse(runner.isEventExist(0, 0))
	}

	@Test
	fun rapidPressAndRelease_onSeveralPads_endsWithEveryLedOff() {
		setUpRunner { x, y -> LedAnimation(arrayListOf(LedEvent.On(x, y), LedEvent.Delay(50), LedEvent.Off(x, y), LedEvent.Delay(50)), 0, 0) }
		var t = 1000L
		repeat(20) { i ->
			val pad = i % 4
			clock.set(t)
			press(pad, pad)
			run(t + 4, t + 48)
			release(pad, pad)
			t += 52
		}
		run(t, t + 200)

		val lit = mutableSetOf<String>()
		for (e in events) {
			val pad = e.substringAfter('(', "0,0)").substringBefore(')')
			if (e.startsWith("on")) lit += pad else if (e.startsWith("off")) lit -= pad
		}
		assertTrue("still lit: $lit", lit.isEmpty())
		assertEquals(20, ons().size)
		(0 until 4).forEach { assertFalse(runner.isEventExist(it, it)) }
		assertEquals(0L, callsUnderLock.get())
	}

	@Test
	fun stopAndRelaunch_keepsDeliveringOutsideTheLock() {
		setUpRunner { _, _ -> blink(on = 100, off = 100, loop = 3) }
		press()
		run(1000, 1050)
		runner.stop()
		clock.set(5000)
		runner.launch()
		Thread.sleep(150)
		run(5000, 6000)

		assertEquals(
			listOf("on(1,1)@1000", "off(1,1)@5052", "on(1,1)@5152", "off(1,1)@5252", "on(1,1)@5352", "off(1,1)@5452"),
			events,
		)
		assertEquals(0L, callsUnderLock.get())
	}
}
