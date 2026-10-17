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
 * infinite loop the press started, whatever the pad's next sound is.
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
		load(listOf(Sound(File("broken.wav"), loop = 0, wormhole = 1)), undecodable = setOf("broken.wav"))

		tap()
		scheduler.advanceUntilIdle()

		assertEquals(1, chain.value)
		assertEquals(emptyList<Pair<String, Int>>(), engine.plays)
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

	private fun tap() {
		runner.soundOn(X, Y)
		runner.soundOff(X, Y)
	}

	/** Loads a pack whose pad ([X], [Y]) on chain 0 holds [sounds] in order; every other pad is empty. */
	private fun load(sounds: List<Sound>, undecodable: Set<String> = emptySet()) {
		sounds.forEachIndexed { i, sound -> sound.num = i }
		val table = Array(CHAINS) { Array(SIZE) { arrayOfNulls<ArrayDeque<Sound>>(SIZE) } }
		table[0][X][Y] = ArrayDeque(sounds)
		val pack = OnePadPack(table)
		engine = RecordingSoundEngine(undecodable)
		val loaded = CountDownLatch(1)
		var failure: Throwable? = null
		runner = SoundRunner(pack, chain, object : SoundRunner.LoadingListener {
			override fun onStart(soundCount: Int) {}
			override fun onProgressTick() {}
			override fun onEnd() = loaded.countDown()
			override fun onException(throwable: Throwable) {
				failure = throwable
				loaded.countDown()
			}
		}, loadScope, engine)
		assertTrue("Sounds did not finish loading", loaded.await(10, TimeUnit.SECONDS))
		failure?.let { throw AssertionError("Sound loading failed", it) }
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
