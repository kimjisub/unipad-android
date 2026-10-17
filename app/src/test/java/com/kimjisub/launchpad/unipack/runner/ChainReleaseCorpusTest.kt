package com.kimjisub.launchpad.unipack.runner

import com.kimjisub.design.touch.PadTouchTracker
import com.kimjisub.launchpad.analytics.UsageAnalytics
import com.kimjisub.launchpad.audio.OboeAudioEngine
import com.kimjisub.launchpad.chainrelease.ChainReleaseCorpus
import com.kimjisub.launchpad.chainrelease.ChainReleaseCorpus.Pad
import com.kimjisub.launchpad.chainrelease.ChainReleaseReplay
import com.kimjisub.launchpad.db.repository.UnipackRepository
import com.kimjisub.launchpad.manager.ChannelManager
import com.kimjisub.launchpad.unipack.UniPack
import com.kimjisub.launchpad.unipack.UniPackFolder
import com.kimjisub.launchpad.viewmodel.PlayActivityViewModel
import io.mockk.mockk
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
import java.util.IdentityHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Plays every case of the chain-release corpus through the pad touch tracker, the view model and the
 * sound runner, on a virtual clock, and checks each step's engine requests, chain and press lights.
 * A release must stop the infinite loop its own press started, whatever the chain or the pad's next
 * sound is by then. No audio is decoded or heard: the engine records requests only.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(Parameterized::class)
class ChainReleaseCorpusTest(private val id: String) {
	private val scheduler = TestCoroutineScheduler()
	private val loadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
	private val root: File = Files.createTempDirectory("chain-release").toFile()
	private lateinit var sound: SoundRunner

	@Before
	fun setUp() = Dispatchers.setMain(StandardTestDispatcher(scheduler))

	@After
	fun tearDown() {
		if (::sound.isInitialized) sound.destroy()
		loadScope.cancel()
		Dispatchers.resetMain()
		root.deleteRecursively()
	}

	@Test
	fun case() {
		val case = corpus.cases.first { it.id == id }
		corpus.unpack(case, root)
		val pack = UniPackFolder(root).load().loadDetail()
		val record = ChainReleaseReplay(Driver(pack)).run(case)
		println("suite=${ChainReleaseCorpus.SUITE} platform=android case=$id input=unit (PadTouchTracker, PlayActivityViewModel, SoundRunner)" +
			" evidence=engine requests only, nothing decoded or heard; ${corpus.sha256}")
		record.forEach(::println)
	}

	/** Stands in for the native engine; a voice's stop key is its 1-based position in [plays]. */
	private class RecordingEngine : SoundRunner.Engine {
		private val decoded = IdentityHashMap<OboeAudioEngine.DecodedAudio, String>()
		private val names = HashMap<Int, String>()
		val plays = CopyOnWriteArrayList<Pair<String, Int>>()
		val stops = CopyOnWriteArrayList<Int>()

		override fun start() = true
		override fun stop() {}
		override fun decode(file: File) =
			OboeAudioEngine.DecodedAudio(ShortArray(1), 1, 1, 44_100).also { synchronized(this) { decoded[it] = file.name } }
		override fun load(decoded: OboeAudioEngine.DecodedAudio): Int = synchronized(this) {
			names.size.also { names[it] = this.decoded.getValue(decoded) }
		}
		override fun unloadSound(soundId: Int) {}
		override fun unloadAll() {}
		@Synchronized override fun play(soundId: Int, volumeL: Float, volumeR: Float, loop: Int): Int {
			plays += names.getValue(soundId) to loop
			return plays.size
		}
		override fun stopVoice(stopKey: Int) {
			stops += stopKey
		}
		override fun stopAllVoices() {}
	}

	private inner class Driver(private val pack: UniPack) : ChainReleaseReplay.Driver {
		private val engine = RecordingEngine()
		private val vm = PlayActivityViewModel(mockk<UnipackRepository>(), UsageAnalytics { _, _ -> })
		private val tracker = PadTouchTracker { x, y, down -> vm.padTouch(x, y, down) }
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
			tracker.setGrid(pack.buttonX, pack.buttonY)
		}

		override val virtualTime = true

		override fun advanceTo(atMs: Long) {
			val due = atMs - scheduler.currentTime
			if (due > 0) scheduler.advanceTimeBy(due)
			scheduler.runCurrent()
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
			problem()?.let { throw AssertionError(it) }
		}

		override fun settle() = scheduler.runCurrent()
		override fun chain() = vm.chain.value
		override fun pressedPads() = (0 until pack.buttonX).flatMap { x -> (0 until pack.buttonY).map { y -> Pad(x, y) } }
			.filter { vm.channelManager.get(it.x, it.y)?.channel == ChannelManager.Channel.PRESSED }
			.toSet()
		override fun plays(): List<Pair<String, Int>> = engine.plays.toList()
		override fun stops(): List<Int> = engine.stops.toList()
	}

	companion object {
		private const val CELL = 10

		private val corpus = ChainReleaseCorpus.load { path ->
			checkNotNull(ChainReleaseCorpusTest::class.java.classLoader!!.getResourceAsStream(path)) { "Missing $path" }
		}

		@JvmStatic
		@Parameterized.Parameters(name = "{0}")
		fun ids(): List<String> = corpus.cases.map { it.id }
	}
}
