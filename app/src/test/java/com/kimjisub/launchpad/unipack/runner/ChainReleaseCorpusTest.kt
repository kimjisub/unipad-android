package com.kimjisub.launchpad.unipack.runner

import android.os.SystemClock
import com.kimjisub.design.touch.PadTouchTracker
import com.kimjisub.launchpad.analytics.UsageAnalytics
import com.kimjisub.launchpad.chainrelease.ChainReleaseCorpus
import com.kimjisub.launchpad.chainrelease.ChainReleaseCorpus.Pad
import com.kimjisub.launchpad.chainrelease.ChainReleaseLights
import com.kimjisub.launchpad.chainrelease.ChainReleaseReplay
import com.kimjisub.launchpad.db.repository.UnipackRepository
import com.kimjisub.launchpad.manager.ChannelManager
import com.kimjisub.launchpad.unipack.UniPack
import com.kimjisub.launchpad.unipack.UniPackFolder
import com.kimjisub.launchpad.viewmodel.PlayActivityViewModel
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Plays every case of the chain-release corpus through the pad touch tracker, the view model and the
 * sound and LED runners, on a virtual clock, and checks each step's engine requests, chain, press lights
 * and the cells the pack's LED animations light ([ChainReleaseLights] gives each sound one). A release
 * must stop the infinite loop and the endless animation its own press started, whatever the chain or
 * the pad's next sound is by then. No audio is decoded or heard: the engine records requests only.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(Parameterized::class)
class ChainReleaseCorpusTest(private val id: String) {
	private val scheduler = TestCoroutineScheduler()
	private val loadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
	private val root: File = Files.createTempDirectory("chain-release").toFile()
	private lateinit var sound: SoundRunner
	private lateinit var led: LedRunner

	@Before
	fun setUp() {
		Dispatchers.setMain(StandardTestDispatcher(scheduler))
		mockkStatic(SystemClock::class)
		every { SystemClock.elapsedRealtime() } answers { scheduler.currentTime }
	}

	@After
	fun tearDown() {
		if (::led.isInitialized) led.stop()
		if (::sound.isInitialized) sound.destroy()
		loadScope.cancel()
		unmockkStatic(SystemClock::class)
		Dispatchers.resetMain()
		root.deleteRecursively()
	}

	@Test
	fun case() {
		val case = corpus.cases.first { it.id == id }
		corpus.unpack(case, root)
		val lights = ChainReleaseLights.addTo(root)
		val pack = UniPackFolder(root).load().loadDetail()
		val record = ChainReleaseReplay(Driver(pack), lights).run(case)
		println("suite=${ChainReleaseCorpus.SUITE} platform=android case=$id input=unit (PadTouchTracker, PlayActivityViewModel, SoundRunner, LedRunner)" +
			" evidence=engine requests and LED cells only, nothing decoded or heard; lights are test-side keyLED; ${corpus.sha256}")
		record.forEach(::println)
	}

	private inner class Driver(private val pack: UniPack) : ChainReleaseReplay.Driver {
		private val engine = RecordingSoundEngine()
		private val vm = PlayActivityViewModel(mockk<UnipackRepository>(), UsageAnalytics { _, _ -> })
		private val tracker = PadTouchTracker { pointerId, x, y, down -> vm.padTouch(x, y, down, pointerId) }
		private val pointers = HashMap<String, Long>()

		init {
			vm.unipack = pack
			vm.channelManager = ChannelManager(pack.buttonX, pack.buttonY)
			vm.chain.range = 0 until pack.chain
			vm.scbFeedbackLight.setChecked(true)
			vm.uiCallback = mockk(relaxed = true)
			val loaded = CountDownLatch(1)
			var failure: Throwable? = null
			sound = SoundRunner(pack, vm.chain, object : SoundRunner.LoadingListener {
				override fun onStart(soundCount: Int) {}
				override fun onProgressTick() {}
				override fun onEnd() = loaded.countDown()
				override fun onException(throwable: Throwable) {
					failure = throwable
					loaded.countDown()
				}
			}, loadScope, engine)
			check(loaded.await(10, TimeUnit.SECONDS)) { "Sounds did not finish loading" }
			failure?.let { throw AssertionError("Sound loading failed", it) }
			vm.soundRunner = sound
			// Its coroutine ticks once right after launch and then waits for good, so the LEDs move when the
			// test calls loop(). Should that first tick land late, it plays at the current virtual time like the test's own.
			led = LedRunner(pack, vm.ledRunnerListener, vm.chain, loopDelay = Long.MAX_VALUE).also { it.launch() }
			vm.ledRunner = led
			tracker.setGrid(pack.buttonX, pack.buttonY)
		}

		/** Plays the LED steps due by now and applies them as the play screen would, on main. */
		private fun tickLeds() {
			scheduler.runCurrent()
			loop.invoke(led)
			scheduler.runCurrent()
		}

		override val virtualTime = true

		override fun advanceTo(atMs: Long) {
			val due = atMs - scheduler.currentTime
			if (due > 0) scheduler.advanceTimeBy(due)
			tickLeds()
		}

		// One cell is CELL px square, so its centre lies inside it.
		override fun press(inputId: String, pad: Pad) {
			val pointer = pointers.size.toLong()
			pointers[inputId] = pointer
			tracker.down(pointer, pad.y * CELL + CELL / 2f, pad.x * CELL + CELL / 2f, pack.buttonY * CELL, pack.buttonX * CELL)
		}

		// The play screen hands a cancelled pointer to the tracker as a lift.
		override fun release(inputId: String, cancelled: Boolean, stale: Boolean): Boolean {
			tracker.up(pointers.getValue(inputId))
			return true
		}

		override fun tapChain(chain: Int) {
			vm.chain.value = chain
		}

		override fun await(problem: () -> String?) {
			tickLeds()
			problem()?.let { throw AssertionError(it) }
		}

		override fun settle() = tickLeds()
		override fun chain() = vm.chain.value
		override fun pressedPads() = (0 until pack.buttonX).flatMap { x -> (0 until pack.buttonY).map { y -> Pad(x, y) } }
			.filter { vm.channelManager.get(it.x, it.y)?.channel == ChannelManager.Channel.PRESSED }
			.toSet()
		override fun plays(): List<Pair<String, Int>> = engine.plays.toList()
		override fun stops(): List<Int> = engine.stops.toList()
		override fun litCells() = (0 until pack.buttonX).flatMap { x -> (0 until pack.buttonY).map { y -> Pad(x, y) } }
			.filter { vm.channelManager.get(it.x, it.y)?.channel == ChannelManager.Channel.LED }
			.toSet()
	}

	companion object {
		private const val CELL = 10
		private val loop = LedRunner::class.java.getDeclaredMethod("loop").apply { isAccessible = true }

		private val corpus = ChainReleaseCorpus.load { path ->
			checkNotNull(ChainReleaseCorpusTest::class.java.classLoader!!.getResourceAsStream(path)) { "Missing $path" }
		}

		@JvmStatic
		@Parameterized.Parameters(name = "{0}")
		fun ids(): List<String> = corpus.cases.map { it.id }
	}
}
