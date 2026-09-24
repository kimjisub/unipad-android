package com.kimjisub.launchpad.unipack.runner

import com.kimjisub.launchpad.audio.OboeAudioEngine
import com.kimjisub.launchpad.unipack.UniPack
import com.kimjisub.launchpad.unipack.struct.Sound
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class SoundRunnerTest {

	private val uncaught = CopyOnWriteArrayList<Throwable>()
	private val scopeJob = SupervisorJob()

	/** Mirrors viewModelScope: a supervisor scope where an escaping failure reaches the uncaught handler (a crash on Android). */
	private val scope = CoroutineScope(scopeJob + Dispatchers.IO + CoroutineExceptionHandler { _, e -> uncaught.add(e) })

	@After
	fun tearDown() {
		scope.cancel()
	}

	/**
	 * Stands in for the native engine. It tracks how much decoded PCM exists on the Java side
	 * before being handed to load(), which is the memory a large pack has to fit at once.
	 */
	private class FakeEngine(
		private val samplesPerFile: Int = 1_000,
		private val failingFiles: Set<String> = emptySet(),
		private val throwingDecodes: Map<String, Throwable> = emptyMap(),
		private val throwOnLoadCall: Int = -1,
		private val loadFailure: Throwable = RuntimeException("simulated engine registration failure"),
	) : SoundRunner.Engine {
		private val lock = Any()
		private var nextId = 0
		private var pendingBuffers = 0
		private var pendingSamples = 0L

		var peakPendingBuffers = 0
			private set
		var peakPendingSamples = 0L
			private set
		val liveIds = mutableSetOf<Int>()
		val loadCalls = AtomicInteger(0)
		val decodesInFlight = AtomicInteger(0)
		val callsInFlight = AtomicInteger(0)

		@Volatile
		var decodeGate: CountDownLatch? = null
		val decodeEntered = CountDownLatch(1)

		override fun start() = true
		override fun stop() {}

		override fun decode(file: File): OboeAudioEngine.DecodedAudio? {
			decodesInFlight.incrementAndGet()
			callsInFlight.incrementAndGet()
			try {
				decodeEntered.countDown()
				decodeGate?.await(5, TimeUnit.SECONDS)
				throwingDecodes[file.name]?.let { throw it }
				if (file.name in failingFiles) return null
				synchronized(lock) {
					pendingBuffers++
					pendingSamples += samplesPerFile
					peakPendingBuffers = maxOf(peakPendingBuffers, pendingBuffers)
					peakPendingSamples = maxOf(peakPendingSamples, pendingSamples)
				}
				// numFrames carries the size; the array stays tiny so the test itself allocates nothing large.
				return OboeAudioEngine.DecodedAudio(ShortArray(1), samplesPerFile, 1, 44_100)
			} finally {
				decodesInFlight.decrementAndGet()
				callsInFlight.decrementAndGet()
			}
		}

		override fun load(decoded: OboeAudioEngine.DecodedAudio): Int {
			callsInFlight.incrementAndGet()
			try {
				if (loadCalls.incrementAndGet() == throwOnLoadCall) throw loadFailure
				synchronized(lock) {
					pendingBuffers--
					pendingSamples -= decoded.numFrames.toLong() * decoded.channels
					return nextId++.also { liveIds.add(it) }
				}
			} finally {
				callsInFlight.decrementAndGet()
			}
		}

		override fun unloadSound(soundId: Int) {
			synchronized(lock) { liveIds.remove(soundId) }
		}

		override fun unloadAll() {
			synchronized(lock) { liveIds.clear() }
		}

		override fun play(soundId: Int, volumeL: Float, volumeR: Float, loop: Int) = 0
		override fun stopVoice(stopKey: Int) {}
		override fun stopAllVoices() {}
	}

	private class RecordingListener(private val engine: FakeEngine? = null) : SoundRunner.LoadingListener {
		val ticks = AtomicInteger(0)
		val ended = CountDownLatch(1)
		val failed = CountDownLatch(1)
		val failureCount = AtomicInteger(0)
		@Volatile
		var failure: Throwable? = null
		/** Engine calls still running when the failure was reported; loading has not really ended if this is not 0. */
		@Volatile
		var engineCallsAtFailure = -1

		override fun onStart(soundCount: Int) {}
		override fun onProgressTick() {
			ticks.incrementAndGet()
		}
		override fun onEnd() = ended.countDown()
		override fun onException(throwable: Throwable) {
			engineCallsAtFailure = engine?.callsInFlight?.get() ?: -1
			failure = throwable
			failureCount.incrementAndGet()
			failed.countDown()
		}
	}

	/** A 1-chain pack of [fileCount] files laid across an 8x8 grid, each file mapped to [padsPerFile] pads. */
	private fun pack(fileCount: Int, padsPerFile: Int = 1): UniPack {
		val table = Array(1) { Array(8) { arrayOfNulls<ArrayDeque<Sound>>(8) } }
		var pad = 0
		for (f in 0 until fileCount) {
			val file = File("/pack/sounds/$f.wav")
			repeat(padsPerFile) {
				val x = (pad / 8) % 8
				val y = pad % 8
				val cell = table[0][x][y] ?: ArrayDeque<Sound>().also { table[0][x][y] = it }
				cell.add(Sound(file = file, loop = 0))
				pad++
			}
		}
		return mockk<UniPack>(relaxed = true).also {
			every { it.chain } returns 1
			every { it.buttonX } returns 8
			every { it.buttonY } returns 8
			every { it.soundTable } returns table
		}
	}

	private fun UniPack.sounds(): List<Sound> =
		soundTable!!.flatMap { chain -> chain.flatMap { col -> col.flatMap { it ?: emptyList() } } }

	private fun awaitDecodesDrained(engine: FakeEngine) {
		val deadline = System.currentTimeMillis() + 5_000
		while (engine.decodesInFlight.get() > 0 && System.currentTimeMillis() < deadline) Thread.sleep(5)
		// Give a coroutine that just returned from decode the chance to (wrongly) reach load().
		Thread.sleep(100)
	}

	private fun awaitLoadJobsFinished() = runBlocking {
		withTimeout(5_000) { scopeJob.children.toList().joinAll() }
	}

	/**
	 * A failure while loading must end loading the way it always has: reported once to the
	 * listener (which shows a message and closes the screen) and contained in the load job
	 * instead of failing the caller's scope, which crashes the app.
	 */
	private fun assertFailureContainedAndCleanedUp(
		unipack: UniPack,
		engine: FakeEngine,
		listener: RecordingListener,
		runner: SoundRunner,
		expected: Throwable,
	) {
		val reported = listener.failed.await(5, TimeUnit.SECONDS)
		assertTrue("failure was not reported; escaped the load job instead: $uncaught", reported)
		awaitLoadJobsFinished()

		assertTrue("failure escaped the load job: $uncaught", uncaught.isEmpty())
		// Coroutine stack-trace recovery may rethrow a copy, so compare what the user-facing report sees.
		assertEquals(expected.javaClass, listener.failure?.javaClass)
		assertEquals(expected.message, listener.failure?.message)
		assertEquals(1, listener.failureCount.get())
		assertEquals("loading reported success after a failure", 1L, listener.ended.count)
		assertEquals("engine calls still running when the failure was reported", 0, listener.engineCallsAtFailure)

		val loadsBeforeDestroy = engine.loadCalls.get()
		runner.destroy()
		assertTrue("sounds left in the engine: ${engine.liveIds}", engine.liveIds.isEmpty())
		assertTrue(unipack.sounds().all { it.id == -1 })
		awaitDecodesDrained(engine)
		assertEquals("files were loaded after destroy()", loadsBeforeDestroy, engine.loadCalls.get())
	}

	private val decodePermits = Runtime.getRuntime().availableProcessors().coerceIn(2, 8)

	@Test
	fun largePack_holdsOnlyInFlightDecodesOnTheJavaHeap() {
		val fileCount = 60
		val samplesPerFile = 44_100 * 2 * 30 // 30 s stereo, ~5 MB as 16-bit PCM
		val unipack = pack(fileCount, padsPerFile = 2)
		val engine = FakeEngine(samplesPerFile = samplesPerFile)
		val listener = RecordingListener()

		SoundRunner(unipack, ChainObserver(), listener, scope, engine)

		assertTrue("loading did not finish", listener.ended.await(10, TimeUnit.SECONDS))
		println(
			"SoundRunnerTest peak decoded-not-loaded: ${engine.peakPendingBuffers} of $fileCount files, " +
				"${engine.peakPendingSamples * 2 / 1_000_000} MB of ${fileCount.toLong() * samplesPerFile * 2 / 1_000_000} MB " +
				"(permits=$decodePermits)",
		)
		assertTrue(
			"peak ${engine.peakPendingBuffers} decoded buffers waited on the heap; expected at most $decodePermits",
			engine.peakPendingBuffers <= decodePermits,
		)
		assertEquals(null, listener.failure)
		assertEquals(fileCount * 2, listener.ticks.get())
		assertEquals(fileCount, engine.liveIds.size)

		val idsByFile = unipack.sounds().groupBy({ it.file }, { it.id })
		assertEquals(fileCount, idsByFile.size)
		for ((file, ids) in idsByFile) {
			assertTrue("$file was not loaded", ids.all { it >= 0 })
			assertEquals("pads sharing $file must share one engine sound", 1, ids.toSet().size)
		}
		assertEquals(fileCount, idsByFile.values.map { it.first() }.toSet().size)
	}

	@Test
	fun smallPack_loadsEverySound() {
		val unipack = pack(fileCount = 1)
		val engine = FakeEngine()
		val listener = RecordingListener()

		SoundRunner(unipack, ChainObserver(), listener, scope, engine)

		assertTrue(listener.ended.await(5, TimeUnit.SECONDS))
		assertEquals(1, listener.ticks.get())
		assertEquals(setOf(0), unipack.sounds().map { it.id }.toSet())
	}

	@Test
	fun failedDecode_leavesOnlyThatFileSilent() {
		val unipack = pack(fileCount = 3)
		val engine = FakeEngine(failingFiles = setOf("1.wav"))
		val listener = RecordingListener()

		SoundRunner(unipack, ChainObserver(), listener, scope, engine)

		assertTrue(listener.ended.await(5, TimeUnit.SECONDS))
		assertEquals(3, listener.ticks.get())
		val idByName = unipack.sounds().associate { it.file.name to it.id }
		assertEquals(-1, idByName["1.wav"])
		assertTrue(idByName["0.wav"]!! >= 0)
		assertTrue(idByName["2.wav"]!! >= 0)
		assertEquals(2, engine.liveIds.size)
	}

	@Test
	fun destroyWhileDecoding_loadsNothingAfterwardsAndLeavesNoSoundBehind() {
		val unipack = pack(fileCount = 30)
		val engine = FakeEngine().apply { decodeGate = CountDownLatch(1) }
		val listener = RecordingListener()

		val runner = SoundRunner(unipack, ChainObserver(), listener, scope, engine)
		assertTrue("no decode started", engine.decodeEntered.await(5, TimeUnit.SECONDS))

		runner.destroy()
		val loadsAtDestroy = engine.loadCalls.get()
		engine.decodeGate!!.countDown()
		awaitDecodesDrained(engine)

		assertEquals("files were loaded after destroy()", loadsAtDestroy, engine.loadCalls.get())
		assertTrue("sounds left in the engine: ${engine.liveIds}", engine.liveIds.isEmpty())
		assertTrue(unipack.sounds().all { it.id == -1 })
		assertEquals(1L, listener.ended.count)
		assertEquals(null, listener.failure)
	}

	@Test
	fun reopenAfterDestroy_loadsThePackAgain() {
		val unipack = pack(fileCount = 20)
		val engine = FakeEngine().apply { decodeGate = CountDownLatch(1) }

		val first = SoundRunner(unipack, ChainObserver(), RecordingListener(), scope, engine)
		assertTrue(engine.decodeEntered.await(5, TimeUnit.SECONDS))
		first.destroy()
		engine.decodeGate!!.countDown()
		awaitDecodesDrained(engine)
		assertTrue(engine.liveIds.isEmpty())

		engine.decodeGate = null
		val listener = RecordingListener()
		val second = SoundRunner(unipack, ChainObserver(), listener, scope, engine)

		assertTrue(listener.ended.await(5, TimeUnit.SECONDS))
		assertEquals(20, engine.liveIds.size)
		assertTrue(unipack.sounds().all { it.id >= 0 })
		assertNotEquals(0, engine.loadCalls.get())

		second.destroy()
		assertTrue(engine.liveIds.isEmpty())
		assertFalse(unipack.sounds().any { it.id >= 0 })
	}

	@Test
	fun engineRegistrationFailure_isReportedOnceAndDoesNotEscapeTheScope() {
		val unipack = pack(fileCount = 30)
		val failure = RuntimeException("simulated engine registration failure")
		val engine = FakeEngine(throwOnLoadCall = 10, loadFailure = failure)
		val listener = RecordingListener(engine)

		val runner = SoundRunner(unipack, ChainObserver(), listener, scope, engine)

		assertFailureContainedAndCleanedUp(unipack, engine, listener, runner, failure)
	}

	@Test
	fun outOfMemoryWhileDecoding_isReportedOnceAndDoesNotEscapeTheScope() {
		val unipack = pack(fileCount = 30)
		val failure = OutOfMemoryError("simulated decode allocation failure")
		val engine = FakeEngine(throwingDecodes = mapOf("12.wav" to failure))
		val listener = RecordingListener(engine)

		val runner = SoundRunner(unipack, ChainObserver(), listener, scope, engine)

		assertFailureContainedAndCleanedUp(unipack, engine, listener, runner, failure)
	}

	@Test
	fun reopenAfterFailedLoad_loadsThePackAgain() {
		val unipack = pack(fileCount = 20)
		val failing = FakeEngine(throwOnLoadCall = 5)
		val failedListener = RecordingListener(failing)
		val first = SoundRunner(unipack, ChainObserver(), failedListener, scope, failing)
		val reported = failedListener.failed.await(5, TimeUnit.SECONDS)
		assertTrue("failure was not reported; escaped the load job instead: $uncaught", reported)
		awaitLoadJobsFinished()
		first.destroy()
		assertTrue(failing.liveIds.isEmpty())

		val engine = FakeEngine()
		val listener = RecordingListener(engine)
		val second = SoundRunner(unipack, ChainObserver(), listener, scope, engine)

		assertTrue(listener.ended.await(5, TimeUnit.SECONDS))
		assertEquals(null, listener.failure)
		assertEquals(20, engine.liveIds.size)
		assertTrue(unipack.sounds().all { it.id >= 0 })
		second.destroy()
		assertTrue(engine.liveIds.isEmpty())
		awaitLoadJobsFinished()
		assertTrue("failure escaped the load job: $uncaught", uncaught.isEmpty())
	}
}
