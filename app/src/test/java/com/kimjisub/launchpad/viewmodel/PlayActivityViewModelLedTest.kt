package com.kimjisub.launchpad.viewmodel

import android.os.SystemClock
import com.kimjisub.launchpad.db.repository.UnipackRepository
import com.kimjisub.launchpad.manager.ChannelManager
import com.kimjisub.launchpad.unipack.UniPack
import com.kimjisub.launchpad.unipack.runner.LedRunner
import com.kimjisub.launchpad.unipack.struct.LedAnimation
import com.kimjisub.launchpad.unipack.struct.LedAnimation.LedEvent
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.MainCoroutineDispatcher
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.CoroutineContext

/**
 * The LED runner's changes reach the screen and the Launchpad through the view model. The fake UI
 * below behaves like PlayActivity: each setLedPad sends the pad's current LED code to the Launchpad
 * and redraws the pad in `lifecycleScope.launch` (Main.immediate), which posts a main-thread task when
 * called from the runner thread and runs in place when called on main.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlayActivityViewModelLedTest {

	/** A main looper stand-in: tasks run only in [runAll], on the thread that calls it. */
	private class FakeMain : MainCoroutineDispatcher() {
		private val queue = ConcurrentLinkedQueue<Runnable>()
		@Volatile var mainThread: Thread = Thread.currentThread()
		var posted = 0
		var maxQueued = 0

		override val immediate: MainCoroutineDispatcher = object : MainCoroutineDispatcher() {
			override val immediate: MainCoroutineDispatcher get() = this
			override fun isDispatchNeeded(context: CoroutineContext) = Thread.currentThread() !== mainThread
			override fun dispatch(context: CoroutineContext, block: Runnable) = this@FakeMain.dispatch(context, block)
		}

		@Synchronized
		override fun dispatch(context: CoroutineContext, block: Runnable) {
			posted++
			queue.add(block)
			maxQueued = maxOf(maxQueued, queue.size)
		}

		fun runAll() {
			while (true) (queue.poll() ?: return).run()
		}
	}

	private val main = FakeMain()
	private val clock = AtomicLong(1000)
	private lateinit var vm: PlayActivityViewModel
	private lateinit var runner: LedRunner
	private val loopMethod = LedRunner::class.java.getDeclaredMethod("loop").apply { isAccessible = true }

	/** Launchpad commands in the order they were sent: "x,y=code". */
	private val sent = mutableListOf<String>()
	private var padRedraws = 0
	private var callsOffMainOrUnderLock = 0

	@Before
	fun setUp() {
		Dispatchers.setMain(main)
		mockkStatic(SystemClock::class)
		every { SystemClock.elapsedRealtime() } answers { clock.get() }
		vm = PlayActivityViewModel(mockk<UnipackRepository>())
		vm.channelManager = ChannelManager(8, 8)
		vm.chain.range = 0 until 4
		vm.uiCallback = FakeActivity()
	}

	@After
	fun tearDown() {
		runner.stop()
		unmockkStatic(SystemClock::class)
		Dispatchers.resetMain()
	}

	private inner class FakeActivity : PlayActivityViewModel.UiCallback {
		private val lifecycleScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

		override fun setLedPad(x: Int, y: Int) {
			if (Thread.currentThread() !== main.mainThread || Thread.holdsLock(runner)) callsOffMainOrUnderLock++
			sent += "$x,$y=${vm.channelManager.get(x, y)?.code ?: 0}"
			lifecycleScope.launch { padRedraws++ }
		}

		override fun setLedChain(c: Int) {}
		override fun updateTraceLogOverlay() {}
		override fun showToast(resId: Int) {}
		override fun finishActivity() {}
		override fun copyToClipboard(text: String) {}
		override fun setChainViewVisibility(index: Int, visibility: Int) {}
		override fun startGuideAnimation(x: Int, y: Int, targetWallTimeMs: Long) {}
		override fun stopGuideAnimation(x: Int, y: Int) {}
		override fun sendGuideLedToLaunchpad(x: Int, y: Int, velocity: Int) {}
		override fun onRequestRelayout() {}
	}

	private fun setUpRunner(animation: LedAnimation) {
		val unipack = mockk<UniPack>(relaxed = true)
		every { unipack.buttonX } returns 8
		every { unipack.buttonY } returns 8
		every { unipack.keyLedExist } returns true
		every { unipack.ledGet(any(), any(), any()) } returns animation
		vm.unipack = unipack
		runner = LedRunner(unipack, vm.ledRunnerListener, vm.chain, loopDelay = 3_600_000L)
		vm.ledRunner = runner
		runner.launch()
		Thread.sleep(150)
	}

	/** Runs one runner tick on its own thread, as the runner's coroutine does. */
	private fun tickOnRunnerThread(t: Long) {
		clock.set(t)
		Thread { loopMethod.invoke(runner) }.apply { start() }.join()
	}

	private fun pressOnRunnerThread(x: Int, y: Int) {
		runner.eventOn(x, y)
		tickOnRunnerThread(clock.get())
	}

	@Test
	fun largeBacklog_reachesMainInFewTasks_withEveryCommandInOrder() {
		val pass = ArrayList<LedEvent>()
		for (x in 0 until 8) for (y in 0 until 8) pass += LedEvent.On(x, y, velocity = 5)
		for (x in 0 until 8) for (y in 0 until 8) pass += LedEvent.Off(x, y)
		setUpRunner(LedAnimation(pass, 3000, 0))
		pressOnRunnerThread(0, 0)
		tickOnRunnerThread(1004)

		main.runAll()

		val changes = 128 * 3000
		val expectedPass = (0 until 8).flatMap { x -> (0 until 8).map { y -> "$x,$y=5" } } +
			(0 until 8).flatMap { x -> (0 until 8).map { y -> "$x,$y=0" } }
		assertEquals(changes, sent.size)
		for (i in sent.indices) assertEquals("command $i", expectedPass[i % 128], sent[i])
		assertEquals(changes, padRedraws)
		assertEquals(0, callsOffMainOrUnderLock)
		// One task per LED_CHANGES_PER_DRAIN changes, and never more than one waiting.
		assertTrue("posted ${main.posted}", main.posted <= changes / PlayActivityViewModel.LED_CHANGES_PER_DRAIN + 1)
		assertEquals(1, main.maxQueued)
		for (x in 0 until 8) for (y in 0 until 8) assertNull(vm.channelManager.get(x, y))
	}

	@Test
	fun ticksWhileMainIsBusy_shareOneWaitingTask() {
		setUpRunner(LedAnimation(arrayListOf(LedEvent.On(1, 1), LedEvent.Delay(5), LedEvent.Off(1, 1), LedEvent.Delay(5)), 0, 0))
		pressOnRunnerThread(0, 0)
		for (t in 1000L..1400L step 4) tickOnRunnerThread(t)

		assertEquals(1, main.posted)
		main.runAll()

		assertEquals(81, sent.size)
		assertTrue(sent.withIndex().all { (i, s) -> s == if (i % 2 == 0) "1,1=4" else "1,1=0" })
		assertEquals(0, callsOffMainOrUnderLock)
	}

	@Test
	fun ledInit_appliesWaitingChangesBeforeTheReset() {
		setUpRunner(LedAnimation(arrayListOf(LedEvent.On(1, 1), LedEvent.Delay(10_000), LedEvent.Off(1, 1)), 1, 0))
		pressOnRunnerThread(0, 0)
		tickOnRunnerThread(1004)
		runner.stop()

		vm.ledInit()
		main.runAll()

		assertEquals("1,1=4", sent.first())
		assertEquals("1,1=0", sent.last { it.startsWith("1,1=") })
		assertNull(vm.channelManager.get(1, 1))
	}

	@Test
	fun chainEvent_isAppliedOnMainBetweenTheLedsAroundIt() {
		setUpRunner(LedAnimation(arrayListOf(LedEvent.On(1, 1), LedEvent.Chain(2), LedEvent.On(2, 2)), 1, 0))
		var sentWhenChainChanged = -1
		vm.chain.addObserver { _, _ -> sentWhenChainChanged = sent.size }
		pressOnRunnerThread(0, 0)
		tickOnRunnerThread(1004)
		assertEquals(0, vm.chain.value)

		main.runAll()

		assertEquals(2, vm.chain.value)
		assertEquals(1, sentWhenChainChanged)
		assertEquals(listOf("1,1=4", "2,2=4"), sent)
	}
}
