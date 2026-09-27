package com.kimjisub.launchpad.viewmodel

import android.os.SystemClock
import com.kimjisub.launchpad.db.repository.UnipackRepository
import com.kimjisub.launchpad.manager.ChannelManager
import com.kimjisub.launchpad.unipack.UniPack
import com.kimjisub.launchpad.unipack.runner.AutoPlayRunner
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
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

		fun runAll(afterEach: () -> Unit = {}) {
			while (true) {
				(queue.poll() ?: return).run()
				afterEach()
			}
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

	private fun setUpRunner(animation: LedAnimation) = setUpRunner { _, _ -> animation }

	private fun setUpRunner(loopDelay: Long = 3_600_000L, animationFor: (x: Int, y: Int) -> LedAnimation) {
		val unipack = mockk<UniPack>(relaxed = true)
		every { unipack.buttonX } returns 8
		every { unipack.buttonY } returns 8
		every { unipack.keyLedExist } returns true
		every { unipack.ledGet(any(), any(), any()) } answers { animationFor(secondArg(), thirdArg()) }
		vm.unipack = unipack
		runner = LedRunner(unipack, vm.ledRunnerListener, vm.chain, loopDelay)
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

	/** Every pad on at velocity 5, then every pad off, [loop] times in one tick. */
	private fun allPadsFlash(loop: Int): LedAnimation {
		val pass = ArrayList<LedEvent>()
		for (x in 0 until 8) for (y in 0 until 8) pass += LedEvent.On(x, y, velocity = 5)
		for (x in 0 until 8) for (y in 0 until 8) pass += LedEvent.Off(x, y)
		return LedAnimation(pass, loop, 0)
	}

	private val flashPass = (0 until 8).flatMap { x -> (0 until 8).map { y -> "$x,$y=5" } } +
		(0 until 8).flatMap { x -> (0 until 8).map { y -> "$x,$y=0" } }
	private val resetSweep = (0 until 8).flatMap { x -> (0 until 8).map { y -> "$x,$y=0" } }

	/** (0,0) flashes every pad [loop] times; (1,1) lights itself and holds; any other pad does nothing. */
	private fun setUpBacklogRunner(loop: Int) = setUpRunner { x, y ->
		when {
			x == 0 && y == 0 -> allPadsFlash(loop)
			x == 1 && y == 1 -> LedAnimation(arrayListOf(LedEvent.On(1, 1), LedEvent.Delay(60_000), LedEvent.Off(1, 1)), 1, 0)
			else -> LedAnimation(arrayListOf(LedEvent.On(x, y, velocity = 7), LedEvent.Delay(60_000)), 1, 0)
		}
	}

	/** A press is picked up by one tick and starts playing on the next. */
	private fun pressAndPlay(x: Int, y: Int) {
		pressOnRunnerThread(x, y)
		tickOnRunnerThread(clock.get() + 4)
	}

	private fun queueBacklog() = pressAndPlay(0, 0)

	/** Runs every waiting main-thread task and returns the most commands any single one sent. */
	private fun runMainTasks(): Int {
		var before = sent.size
		var most = 0
		main.runAll {
			most = maxOf(most, sent.size - before)
			before = sent.size
		}
		return most
	}

	private fun assertEveryLedOff() {
		for (x in 0 until 8) for (y in 0 until 8) assertNull("($x,$y)", vm.channelManager.get(x, y))
	}

	private fun assertOnlyTheResetWasSent(resets: Int = 1) {
		assertEquals(List(resets) { resetSweep }.flatten(), sent)
		assertEquals(0, callsOffMainOrUnderLock)
		assertEveryLedOff()
	}

	// What PlayActivity.onStop does: the runner stops with the screen, then ledInit().
	private fun leaveScreen() {
		vm.screenVisible = false
		vm.ledInit()
	}

	private fun enableLedOption() {
		vm.scbLed.setCheckedSilently(true)
		vm.screenVisible = true
		vm.setupCheckBoxListeners()
	}

	@Test
	fun ledInit_behindALargeBacklog_dropsItAndTurnsEveryLedOffAtOnce() {
		setUpBacklogRunner(loop = 3000)
		queueBacklog()
		pressAndPlay(1, 1)
		runner.stop()

		vm.ledInit()
		val sentByLedInit = sent.toList()
		runMainTasks()

		assertEquals(resetSweep, sentByLedInit)
		assertOnlyTheResetWasSent()
	}

	@Test
	fun ledInitWithNothingWaiting_resetsRightAway() {
		setUpBacklogRunner(loop = 1)
		pressAndPlay(1, 1)
		main.runAll()
		assertEquals(4, vm.channelManager.get(1, 1)?.code)
		val postedBefore = main.posted
		sent.clear()

		vm.ledInit()

		assertEquals(resetSweep, sent)
		assertEquals(postedBefore, main.posted)
		assertEveryLedOff()
	}

	@Test
	fun ledInitTwice_behindABacklog_resetsTwiceAndSendsNothingElse() {
		setUpBacklogRunner(loop = 30)
		queueBacklog()
		runner.stop()

		vm.ledInit()
		vm.ledInit()
		runMainTasks()

		assertOnlyTheResetWasSent(resets = 2)
	}

	@Test
	fun turningTheLedOptionOff_dropsTheBacklog() {
		setUpBacklogRunner(loop = 3000)
		enableLedOption()
		queueBacklog()
		pressAndPlay(1, 1)

		vm.scbLed.setChecked(false)
		runMainTasks()

		assertFalse(runner.active)
		assertOnlyTheResetWasSent()
	}

	@Test
	fun leavingTheScreen_dropsTheBacklogAndAResetAutoplayQueuedBehindIt() {
		setUpBacklogRunner(loop = 3000)
		vm.autoPlayRunner = mockk<AutoPlayRunner>(relaxed = true)
		queueBacklog()
		pressAndPlay(1, 1)

		// Losing audio focus pauses autoplay first, which queues its reset behind the backlog.
		vm.autoPlayPause()
		assertTrue("backlog applied inside autoPlayPause()", sent.none { it.endsWith("=5") || it.endsWith("=4") })
		sent.clear() // padInit() redraws every pad
		leaveScreen()
		runMainTasks()

		assertOnlyTheResetWasSent()
	}

	@Test
	fun tickStillComputingWhenTheScreenIsLeft_neverLightsAPadAfterTheReset() {
		setUpBacklogRunner(loop = 30)
		pressOnRunnerThread(0, 0)
		pressOnRunnerThread(1, 1)
		val computing = CountDownLatch(1)
		val release = CountDownLatch(1)
		every { SystemClock.elapsedRealtime() } answers {
			if (Thread.currentThread().name == "tick") {
				computing.countDown()
				release.await()
			}
			clock.get()
		}
		clock.set(clock.get() + 4)
		val tick = Thread({ loopMethod.invoke(runner) }, "tick").apply { start() }
		computing.await()
		Thread { Thread.sleep(100); release.countDown() }.start()

		leaveScreen()
		tick.join()
		runMainTasks()

		assertOnlyTheResetWasSent()
	}

	@Test
	fun leavingTheScreenWhileTheLoopRuns_neverLightsAPadAfterTheReset() {
		setUpRunner(loopDelay = 1L) { _, _ ->
			LedAnimation(arrayListOf(LedEvent.On(1, 1), LedEvent.Delay(1), LedEvent.Off(1, 1), LedEvent.On(2, 2)), 0, 0)
		}
		every { SystemClock.elapsedRealtime() } answers { clock.incrementAndGet() }
		vm.scbLed.setCheckedSilently(true)
		repeat(30) { round ->
			vm.screenVisible = true
			runner.eventOn(0, 0)
			Thread.sleep(5)
			main.runAll()
			sent.clear()

			leaveScreen()
			Thread.sleep(5)
			runMainTasks()

			assertEquals("round $round", resetSweep, sent)
			assertEveryLedOff()
		}
	}

	@Test
	fun pressRightAfterLeavingTheScreen_isShownAfterTheReset() {
		setUpBacklogRunner(loop = 30)
		queueBacklog()
		leaveScreen()

		runner.launch()
		pressAndPlay(2, 2)
		runMainTasks()

		assertEquals(resetSweep + "2,2=7", sent)
		assertEquals(7, vm.channelManager.get(2, 2)?.code)
		for (x in 0 until 8) for (y in 0 until 8) if (x != 2 || y != 2) assertNull(vm.channelManager.get(x, y))
	}

	@Test
	fun ledOptionOffAndOnAgain_showsTheNewPressOnly() {
		setUpBacklogRunner(loop = 30)
		enableLedOption()
		queueBacklog()

		vm.scbLed.setChecked(false)
		vm.scbLed.setChecked(true)
		assertTrue(runner.active)
		pressAndPlay(2, 2)
		runMainTasks()

		assertEquals(resetSweep + "2,2=7", sent)
		assertEquals(7, vm.channelManager.get(2, 2)?.code)
	}

	private fun pressEndlessBlink() {
		pressAndPlay(3, 3)
		main.runAll()
		assertEquals(listOf("3,3=4"), sent)
		sent.clear()
	}

	private fun playTicksAfterRelaunch() {
		for (i in 1..10) tickOnRunnerThread(clock.get() + 50)
		runMainTasks()
	}

	private fun assertTheOldBlinkNeverRelit() {
		assertEquals(resetSweep, sent.take(64))
		assertEquals(emptyList<String>(), sent.drop(64).filterNot { it.endsWith("=0") })
		assertEquals(0, callsOffMainOrUnderLock)
		assertEveryLedOff()
	}

	private fun setUpEndlessBlinkRunner() = setUpRunner { _, _ ->
		LedAnimation(arrayListOf(LedEvent.On(3, 3), LedEvent.Delay(50), LedEvent.Off(3, 3), LedEvent.Delay(50)), 0, 0)
	}

	@Test
	fun endlessLoopingLed_staysOffAfterLeavingAndComingBack() {
		setUpEndlessBlinkRunner()
		pressEndlessBlink()

		leaveScreen()
		runner.launch()
		playTicksAfterRelaunch()

		assertTheOldBlinkNeverRelit()
	}

	@Test
	fun endlessLoopingLed_staysOffAfterLedOptionOffAndOn() {
		setUpEndlessBlinkRunner()
		enableLedOption()
		pressEndlessBlink()

		vm.scbLed.setChecked(false)
		vm.scbLed.setChecked(true)
		assertTrue(runner.active)
		playTicksAfterRelaunch()

		assertTheOldBlinkNeverRelit()
	}

	/**
	 * (0,0) flashes every pad, moves to chain 1, flashes again and moves to chain 3, [loop] times in one
	 * tick; any other pad flashes every pad 30 times without moving. Returns the chains selected, in order.
	 */
	private fun setUpChainBacklogRunner(loop: Int): MutableList<Int> {
		val flash = allPadsFlash(1).ledEvents
		val chainBacklog = LedAnimation(ArrayList(flash + LedEvent.Chain(1) + flash + LedEvent.Chain(3)), loop, 0)
		setUpRunner { x, y -> if (x == 0 && y == 0) chainBacklog else allPadsFlash(30) }
		val chainsSelected = mutableListOf<Int>()
		vm.chain.addObserver { curr, _ -> chainsSelected += curr }
		return chainsSelected
	}

	@Test
	fun leavingTheScreen_dropsTheLightsButKeepsTheChainTheyEndOn() {
		val chainsSelected = setUpChainBacklogRunner(loop = 3000)
		queueBacklog()

		leaveScreen()
		runMainTasks()

		assertEquals(listOf(3), chainsSelected)
		assertEquals(3, vm.chain.value)
		assertOnlyTheResetWasSent()
	}

	@Test
	fun turningTheLedOptionOff_dropsTheLightsButKeepsTheChainTheyEndOn() {
		val chainsSelected = setUpChainBacklogRunner(loop = 3000)
		enableLedOption()
		queueBacklog()

		vm.scbLed.setChecked(false)
		runMainTasks()

		assertEquals(listOf(3), chainsSelected)
		assertEquals(3, vm.chain.value)
		assertOnlyTheResetWasSent()
	}

	@Test
	fun ledInitWithNoChainChangeWaiting_leavesTheChainAlone() {
		val chainsSelected = setUpChainBacklogRunner(loop = 1)
		queueBacklog()
		main.runAll()
		chainsSelected.clear()
		pressAndPlay(2, 2)
		sent.clear()

		leaveScreen()
		runMainTasks()

		assertEquals(emptyList<Int>(), chainsSelected)
		assertEquals(3, vm.chain.value)
		assertOnlyTheResetWasSent()
	}

	@Test
	fun autoPlayControl_appliesEveryWaitingChainChangeInOrder() {
		val chainsSelected = setUpChainBacklogRunner(loop = 30)
		vm.autoPlayRunner = mockk<AutoPlayRunner>(relaxed = true)
		queueBacklog()

		vm.autoPlayPause()
		runMainTasks()

		assertEquals(List(30) { listOf(1, 3) }.flatten(), chainsSelected)
		assertEquals(128 * 30, sent.count { it.endsWith("=5") })
		assertEquals(resetSweep, sent.takeLast(64))
		assertEveryLedOff()
	}

	@Test
	fun autoPlayControls_resetBehindTheBacklog_andEndWithEveryLedOff() {
		setUpBacklogRunner(loop = 30)
		vm.autoPlayRunner = mockk<AutoPlayRunner>(relaxed = true)
		val controls = listOf<Pair<String, () -> Unit>>(
			"pause" to vm::autoPlayPause,
			"resume" to vm::autoPlayResume,
			"prev" to vm::autoPlayPrev,
			"next" to vm::autoPlayNext,
			"autoplay off" to {
				vm.scbAutoPlay.setCheckedSilently(true)
				vm.switchPlayMode(PlayMode.None)
			},
		)
		for ((name, control) in controls) {
			sent.clear()
			queueBacklog()

			control()
			val backlogSentInline = sent.count { it.endsWith("=5") }
			runMainTasks()

			assertEquals(name, 0, backlogSentInline)
			assertEquals(name, 128 * 30, sent.count { it.endsWith("=5") } * 2)
			assertEquals(name, resetSweep, sent.takeLast(64))
			assertEveryLedOff()
		}
	}
}
