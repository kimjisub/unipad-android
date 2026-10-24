package com.kimjisub.launchpad.unipack.runner

import com.kimjisub.launchpad.unipack.UniPack
import com.kimjisub.launchpad.unipack.struct.Sound
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Presses and releases one pad holding several sounds, and checks what the engine is asked to play
 * and stop. A sound that could not be decoded silences only its own turn, and a release stops the
 * infinite loop the press started, whatever the pad's next sound is. A press made before the sounds
 * finish loading still advances the mapping and follows its chain move.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SoundRunnerPressTest {
	private val scheduler = TestCoroutineScheduler()
	private val loadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
	private val chain = ChainObserver().apply { range = 0 until CHAINS }
	private lateinit var engine: RecordingSoundEngine
	private lateinit var runner: SoundRunner

	@Before
	fun setUp() = Dispatchers.setMain(StandardTestDispatcher(scheduler))

	@After
	fun tearDown() {
		if (::runner.isInitialized) runner.destroy()
		loadScope.cancel()
		Dispatchers.resetMain()
	}

	@Test
	fun undecodableSound_silencesOnlyItsTurn() {
		load(listOf(Sound(File("broken.wav"), loop = 0), Sound(File("b.wav"), loop = 0)), undecodable = setOf("broken.wav"))

		tap()
		assertEquals(emptyList<Pair<String, Int>>(), engine.plays)
		tap()
		tap()
		assertEquals(listOf("b.wav" to 0), engine.plays)
	}

	@Test
	fun undecodableSound_stillMovesToItsChain() {
		load(listOf(Sound(File("broken.wav"), loop = 0, wormhole = 1), Sound(File("good.wav"), loop = 0)), undecodable = setOf("broken.wav"))

		tap()
		scheduler.advanceUntilIdle()

		assertEquals(1, chain.value)
		assertEquals(emptyList<Pair<String, Int>>(), engine.plays)
	}

	@Test
	fun pressBeforeTheSoundsLoad_advancesTheMappingAndFollowsItsChain() {
		val gate = CountDownLatch(1)
		val loaded = start(
			listOf(Sound(File("a.wav"), loop = 0, wormhole = 1), Sound(File("b.wav"), loop = 0)),
			RecordingSoundEngine(decodeGate = gate),
		)
		try {
			tap()
			scheduler.advanceUntilIdle()
			assertEquals("The sound's chain move must still happen while loading", 1, chain.value)
			assertEquals(emptyList<Pair<String, Int>>(), engine.plays)
		} finally {
			gate.countDown()
		}
		loaded.await()
		chain.value = 0
		tap()
		assertEquals("The unloaded first sound must have consumed its turn", listOf("b.wav" to 0), engine.plays)
	}

	@Test
	fun rotatingThePadWhileFilesAreCollectedDoesNotBreakLoading() {
		val collecting = CountDownLatch(1)
		val proceed = CountDownLatch(1)
		val first = object : File("a.wav") {
			override fun getAbsolutePath(): String {
				collecting.countDown()
				check(proceed.await(10, TimeUnit.SECONDS))
				return super.getAbsolutePath()
			}
		}
		val loaded = start(listOf(Sound(first, loop = 0), Sound(File("b.wav"), loop = 0)), RecordingSoundEngine())
		try {
			assertTrue("The loader did not begin collecting files", collecting.await(10, TimeUnit.SECONDS))
			tap()
		} finally {
			proceed.countDown()
		}
		loaded.await()
		tap()
		assertEquals(listOf("b.wav" to 0), engine.plays)
	}

	@Test
	fun releasingAnInfiniteLoopFollowedByAOneShot_stopsTheLoop() {
		load(listOf(Sound(File("a.wav"), loop = -1), Sound(File("b.wav"), loop = 0)))

		runner.soundOn(X, Y)
		val stopsBeforeRelease = engine.stops.size
		runner.soundOff(X, Y)

		assertEquals(listOf("a.wav" to -1), engine.plays)
		assertEquals("releasing A must stop A's loop", listOf(1), engine.stops.drop(stopsBeforeRelease))

		runner.soundOn(X, Y)
		val stopsBeforeSecondRelease = engine.stops.size
		runner.soundOff(X, Y)

		assertEquals(listOf("a.wav" to -1, "b.wav" to 0), engine.plays)
		assertTrue("releasing B must let the one-shot B finish", engine.stops.drop(stopsBeforeSecondRelease).isEmpty())
	}

	@Test
	fun releasingAOneShotFollowedByAnInfiniteLoop_letsTheOneShotFinish() {
		load(listOf(Sound(File("a.wav"), loop = 0), Sound(File("b.wav"), loop = -1)))

		runner.soundOn(X, Y)
		val stopsBeforeRelease = engine.stops.size
		runner.soundOff(X, Y)

		assertEquals(listOf("a.wav" to 0), engine.plays)
		assertTrue("releasing A must let the one-shot A finish", engine.stops.drop(stopsBeforeRelease).isEmpty())

		runner.soundOn(X, Y)
		val stopsBeforeSecondRelease = engine.stops.size
		runner.soundOff(X, Y)

		assertEquals(listOf("a.wav" to 0, "b.wav" to -1), engine.plays)
		assertEquals("releasing B must stop B's loop", listOf(2), engine.stops.drop(stopsBeforeSecondRelease))
	}

	@Test
	fun manualChainSelectionDuringWormholeDelayWins() {
		load(listOf(Sound(File("a.wav"), loop = 0, wormhole = 1)))
		tap()
		scheduler.runCurrent()
		scheduler.advanceTimeBy(50)
		chain.value = 0 // Selecting even the current chain is an explicit choice.
		scheduler.advanceUntilIdle()
		assertEquals(0, chain.value)
	}

	@Test
	fun stoppingPlaybackCancelsPendingWormholes() {
		load(listOf(Sound(File("a.wav"), loop = 0, wormhole = 1)))
		tap()
		scheduler.runCurrent()
		runner.stopAll()
		scheduler.advanceUntilIdle()
		assertEquals(0, chain.value)
	}

	@Test
	fun hidingTheScreenCancelsPendingWormholes() {
		load(listOf(Sound(File("a.wav"), loop = 0, wormhole = 1)))
		tap()
		scheduler.runCurrent()
		runner.pauseStream()
		scheduler.advanceUntilIdle()
		assertEquals(0, chain.value)
	}

	@Test
	fun destroyingTheRunnerCancelsPendingWormholes() {
		load(listOf(Sound(File("a.wav"), loop = 0, wormhole = 1)))
		tap()
		runner.destroy()
		scheduler.advanceUntilIdle()
		assertEquals(0, chain.value)
	}

	@Test
	fun adjacentWormholesKeepTheirScheduledOrder() {
		load(listOf(Sound(File("a.wav"), loop = 0, wormhole = 1), Sound(File("b.wav"), loop = 0, wormhole = 0)))
		val moves = mutableListOf<Int>()
		chain.addObserver { curr, _ -> moves += curr }
		tap()
		tap()
		scheduler.advanceUntilIdle()
		assertEquals(listOf(1, 0), moves)
	}

	@Test
	fun aNewPressCanScheduleAWormholeAfterPlaybackWasStopped() {
		load(listOf(Sound(File("a.wav"), loop = 0, wormhole = 1)))
		tap()
		runner.stopAll()
		tap()
		scheduler.advanceUntilIdle()
		assertEquals(1, chain.value)
	}

	@Test
	fun refreshingChainLightsDoesNotCancelPendingWormholes() {
		load(listOf(Sound(File("a.wav"), loop = 0, wormhole = 1)))
		tap()
		scheduler.runCurrent()
		scheduler.advanceTimeBy(50)
		chain.refresh()
		scheduler.advanceUntilIdle()
		assertEquals(1, chain.value)
	}

	private fun tap() {
		runner.soundOn(X, Y)
		runner.soundOff(X, Y)
	}

	/** Loads a pack whose pad ([X], [Y]) on chain 0 holds [sounds] in order; every other pad is empty. */
	private fun load(sounds: List<Sound>, undecodable: Set<String> = emptySet()) =
		start(sounds, RecordingSoundEngine(undecodable)).await()

	/** Starts loading the pack [load] describes into [engine] without waiting for it. */
	private fun start(sounds: List<Sound>, engine: RecordingSoundEngine): Loading {
		sounds.forEachIndexed { i, sound -> sound.num = i }
		val table = Array(CHAINS) { Array(SIZE) { arrayOfNulls<ArrayDeque<Sound>>(SIZE) } }
		table[0][X][Y] = ArrayDeque(sounds)
		this.engine = engine
		val loading = Loading()
		runner = SoundRunner(OnePadPack(table), chain, loading, loadScope, engine)
		return loading
	}

	private class Loading : SoundRunner.LoadingListener {
		private val done = CountDownLatch(1)
		@Volatile private var failure: Throwable? = null

		override fun onStart(soundCount: Int) {}
		override fun onProgressTick() {}
		override fun onEnd() = done.countDown()
		override fun onException(throwable: Throwable) {
			failure = throwable
			done.countDown()
		}

		fun await() {
			assertTrue("Sounds did not finish loading", done.await(10, TimeUnit.SECONDS))
			failure?.let { throw AssertionError("Sound loading failed", it) }
		}
	}

	private class OnePadPack(table: Array<Array<Array<ArrayDeque<Sound>?>>>) : UniPack() {
		init {
			chain = CHAINS
			buttonX = SIZE
			buttonY = SIZE
			soundTable = table
		}

		override val id = "one-pad"
		override fun lastModified() = 0L
		override fun loadInfo() = this
		override fun loadDetail() = this
		override fun checkFile() {}
		override fun delete() = false
		override fun getPathString() = ""
		override fun getByteSize() = 0L
	}

	private companion object {
		const val CHAINS = 2
		const val SIZE = 2
		const val X = 1
		const val Y = 0
	}
}
