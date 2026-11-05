package com.kimjisub.launchpad.unipack.runner

import com.kimjisub.launchpad.audio.AudioFileFacts
import com.kimjisub.launchpad.audio.AudioInspection
import com.kimjisub.launchpad.audio.OboeAudioEngine
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
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * When the native audio library cannot be loaded, the first use of the engine fails with
 * UnsatisfiedLinkError and every later use with NoClassDefFoundError. The loader reports that
 * failure; presses, releases, auto play and closing the screen afterwards must not touch the engine.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SoundRunnerMissingEngineTest {
	private val scheduler = TestCoroutineScheduler()
	private val loadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
	private val chain = ChainObserver().apply { range = 0 until CHAINS }
	private lateinit var runner: SoundRunner

	@Before
	fun setUp() = Dispatchers.setMain(StandardTestDispatcher(scheduler))

	@After
	fun tearDown() {
		loadScope.cancel()
		Dispatchers.resetMain()
	}

	@Test
	fun inputAfterTheLibraryFailedToLoad_doesNotReachTheEngine() {
		val engine = MissingLibraryEngine()
		val loading = start(listOf(Sound(File("a.wav"), loop = -1, wormhole = 1), Sound(File("b.wav"), loop = 0)), engine)

		val failure = loading.awaitFailure()
		assertTrue("The loader must report the missing library, got $failure", failure is UnsatisfiedLinkError)
		val touchesAfterFailure = engine.touches.size

		runner.soundOn(X, Y)
		runner.soundOff(X, Y)
		runner.soundOn(X, Y, followWormhole = false)
		runner.soundOff(X, Y)
		runner.stopAll()
		runner.pauseStream()
		runner.resumeStream()
		runner.destroy()

		assertEquals("Nothing may call the engine after it failed to load", emptyList<String>(), engine.touches.drop(touchesAfterFailure))
	}

	@Test
	fun inputAfterTheLibraryFailedToLoad_stillAdvancesTheMappingAndFollowsItsChain() {
		val loading = start(listOf(Sound(File("a.wav"), loop = 0, wormhole = 1), Sound(File("b.wav"), loop = 0)), MissingLibraryEngine())
		loading.awaitFailure()

		runner.soundOn(X, Y)
		runner.soundOff(X, Y)
		scheduler.advanceUntilIdle()

		assertEquals(1, chain.value)
		assertEquals("The press must still use up its sound's turn", 1, pack.soundGet(0, X, Y)?.num)
		runner.destroy()
	}

	@Test
	fun inputBeforeTheEngineStarts_doesNotReachTheEngine() {
		val gate = CountDownLatch(1)
		val engine = MissingLibraryEngine(startGate = gate)
		val loading = start(listOf(Sound(File("a.wav"), loop = 0)), engine)
		try {
			assertTrue("The loader did not reach the engine start", engine.starting.await(10, TimeUnit.SECONDS))
			runner.soundOn(X, Y)
			runner.soundOff(X, Y)
			runner.stopAll()
		} finally {
			gate.countDown()
		}
		loading.awaitFailure()
		runner.destroy()

		assertEquals("Only the loader's start may touch the engine", listOf("start"), engine.touches)
	}

	private lateinit var pack: OnePadPack

	private fun start(sounds: List<Sound>, engine: SoundRunner.Engine): Loading {
		sounds.forEachIndexed { i, sound -> sound.num = i }
		val table = Array(CHAINS) { Array(SIZE) { arrayOfNulls<ArrayDeque<Sound>>(SIZE) } }
		table[0][X][Y] = ArrayDeque(sounds)
		pack = OnePadPack(table)
		val loading = Loading()
		runner = SoundRunner(pack, chain, loading, loadScope, engine)
		return loading
	}

	/**
	 * Behaves like [OboeAudioEngine] whose class initializer could not load its native library:
	 * the first call throws UnsatisfiedLinkError, every later one NoClassDefFoundError.
	 */
	private class MissingLibraryEngine(private val startGate: CountDownLatch? = null) : SoundRunner.Engine {
		val touches = CopyOnWriteArrayList<String>()
		val starting = CountDownLatch(1)

		private fun touch(name: String): Nothing {
			val first = touches.isEmpty()
			touches += name
			if (first) throw UnsatisfiedLinkError("couldn't find \"libunipad_audio.so\"")
			throw NoClassDefFoundError("Could not initialize class com.kimjisub.launchpad.audio.OboeAudioEngine")
		}

		override fun inspect(file: File) = AudioInspection.Readable(AudioFileFacts(44_100, 1, 1))
		override fun start(): Boolean {
			starting.countDown()
			startGate?.await(10, TimeUnit.SECONDS)
			touch("start")
		}
		override fun stop() = touch("stop")
		override fun decode(file: File, interrupted: () -> Boolean): OboeAudioEngine.DecodedAudio? = touch("decode")
		override fun load(decoded: OboeAudioEngine.DecodedAudio): Int = touch("load")
		override fun unloadSound(soundId: Int) = touch("unloadSound")
		override fun unloadAll() = touch("unloadAll")
		override fun play(soundId: Int, volumeL: Float, volumeR: Float, loop: Int): Int = touch("play")
		override fun stopVoice(stopKey: Int) = touch("stopVoice")
		override fun stopAllVoices() = touch("stopAllVoices")
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

		fun awaitFailure(): Throwable {
			assertTrue("Sound loading did not finish", done.await(10, TimeUnit.SECONDS))
			return checkNotNull(failure) { "Sound loading was expected to fail" }
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
