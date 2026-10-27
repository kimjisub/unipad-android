package com.kimjisub.launchpad.viewmodel

import androidx.lifecycle.ViewModelStore
import com.kimjisub.launchpad.R
import com.kimjisub.launchpad.analytics.UsageAnalytics
import com.kimjisub.launchpad.db.repository.UnipackRepository
import com.kimjisub.launchpad.tool.SoundDurationReader
import com.kimjisub.launchpad.tool.UniPackAutoMapper
import com.kimjisub.launchpad.unipack.UniPackFolder
import com.kimjisub.launchpad.unipack.struct.AutoPlay
import com.kimjisub.launchpad.unipack.runner.SoundRunner
import com.kimjisub.launchpad.audio.AudioFileFacts
import com.kimjisub.launchpad.audio.AudioInspection
import com.kimjisub.launchpad.audio.OboeAudioEngine
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.unmockkConstructor
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * The auto mapping button on the play screen: the user hears of a failure, leaving stops it, and the
 * rewritten autoPlay is read back off the main thread (packs with thousands of lines froze the screen).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlayActivityViewModelAutoMappingTest {

	@get:Rule
	val tmp = TemporaryFolder()

	private val main = FakeMainDispatcher()
	private val ui = mockk<PlayActivityViewModel.UiCallback>(relaxed = true)
	private val store = ViewModelStore()
	private val original = "t 1 1\nd 100\nt 1 1\nd 100\n"
	private lateinit var root: File

	@Volatile
	private var reloadThread: Thread? = null

	private object FixedLengths : SoundDurationReader {
		override fun durationMs(file: File) = 400.0
		override fun close() {}
	}

	/** Holds the first sound's reading until [release], so the screen can be left while it runs. */
	private class HeldLengths : SoundDurationReader {
		val reading = CountDownLatch(1)
		val release = CountDownLatch(1)
		override fun durationMs(file: File): Double {
			reading.countDown()
			release.await(5, TimeUnit.SECONDS)
			return 400.0
		}
		override fun close() {}
	}

	@Before
	fun setUp() {
		Dispatchers.setMain(main)
		mockkConstructor(UniPackFolder::class)
		every { anyConstructed<UniPackFolder>().reloadAutoPlay() } answers {
			reloadThread = Thread.currentThread()
			callOriginal()
		}
	}

	@After
	fun tearDown() {
		root.setWritable(true)
		store.clear()
		unmockkConstructor(UniPackFolder::class)
		Dispatchers.resetMain()
	}

	private fun openPack(lengths: SoundDurationReader = FixedLengths, engine: SoundRunner.Engine = SoundRunner.OboeEngine): PlayActivityViewModel {
		root = tmp.newFolder("pack")
		File(root, "info").writeText("title=Test\nproducerName=Tester\nbuttonX=8\nbuttonY=8\nchain=1\n")
		File(root, "keySound").writeText("1 1 1 a.wav\n")
		File(root, "sounds").mkdir()
		File(root, "sounds/a.wav").writeText("")
		File(root, "autoPlay").writeText(original)
		val vm = PlayActivityViewModel(
			mockk<UnipackRepository>(relaxed = true),
			UsageAnalytics { _, _ -> },
			autoMapper = { UniPackAutoMapper(it, { lengths }) },
			soundEngine = engine,
		)
		store.put("play", vm)
		vm.uiCallback = ui
		runBlocking { withTimeout(5_000) { vm.loadUnipackOnce(root.path).await() } }
		return vm
	}

	/** Runs what reaches the main thread, as the looper would, until [done] or the timeout. */
	private fun runUntil(timeoutMs: Long = 5_000, done: () -> Boolean) {
		val deadline = System.nanoTime() + timeoutMs * 1_000_000
		while (!done() && System.nanoTime() < deadline) {
			main.runAll()
			Thread.sleep(5)
		}
		main.runAll()
	}

	private fun files() = root.list()!!.sorted()

	@Test
	fun mapping_rewritesTheAutoPlayAndLoadsIt() {
		val vm = openPack()

		vm.autoMapping()
		runUntil { !vm.autoMappingActive }

		assertEquals("d 1000\nt 1 1\nd 400\nt 1 1\n", File(root, "autoPlay").readText())
		assertEquals(2, vm.unipack.autoPlayTable!!.elements.count { it is AutoPlay.Element.Delay })
		verify(exactly = 1) { ui.showToast(R.string.autoMappingSaved) }
	}

	@Test
	fun mapping_readsTheRewrittenAutoPlayOffTheMainThread() {
		val vm = openPack()

		vm.autoMapping()
		runUntil { reloadThread != null && !vm.autoMappingActive }

		assertNotNull(reloadThread)
		assertNotSame(main.mainThread, reloadThread)
		assertFalse(vm.autoMappingActive)
	}

	@Test
	fun aReadBackFailure_showsTheFailureInsteadOfCrashing() {
		every { anyConstructed<UniPackFolder>().reloadAutoPlay() } answers {
			reloadThread = Thread.currentThread()
			throw IllegalStateException("read back failed")
		}
		val vm = openPack()

		vm.autoMapping()
		runUntil { reloadThread != null && !vm.autoMappingActive }

		assertNotNull(reloadThread)
		assertFalse(vm.autoMappingActive)
		verify { ui.showToast(R.string.autoMappingFailed) }
	}

	@Test
	fun aPackThatCannotBeWritten_showsTheFailure() {
		val vm = openPack()
		root.setWritable(false)
		File(root, "autoPlay").setWritable(false)

		vm.autoMapping()
		runUntil { !vm.autoMappingActive }

		verify { ui.showToast(R.string.autoMappingFailed) }
		assertEquals(original, File(root, "autoPlay").readText())
		assertEquals(listOf("autoPlay", "info", "keySound", "sounds"), files())
	}

	@Test
	fun leavingThePlayScreen_stopsTheMappingAndLeavesTheAutoPlay() {
		val lengths = HeldLengths()
		val vm = openPack(lengths)

		vm.autoMapping()
		assertTrue(lengths.reading.await(5, TimeUnit.SECONDS))
		store.clear()
		lengths.release.countDown()
		runUntil(1_000) { false }

		assertFalse(vm.autoMappingActive)
		assertEquals(original, File(root, "autoPlay").readText())
		assertEquals(listOf("autoPlay", "info", "keySound", "sounds"), files())
	}
	@Test
	fun aCompletedSaveNotifiesTheUser() {
		val vm = openPack()
		vm.autoMapping()
		runUntil { !vm.autoMappingActive }
		verify(exactly = 1) { ui.showToast(any()) }
	}

	@Test
	fun repeatedRequestsCannotSaveOrRestoreWhileMappingIsRunning() {
		val lengths = HeldLengths()
		val vm = openPack(lengths)
		vm.autoMapping()
		assertTrue(lengths.reading.await(5, TimeUnit.SECONDS))
		vm.autoMapping()
		vm.restoreAutoPlay()
		lengths.release.countDown()
		runUntil { !vm.autoMappingActive }
		assertTrue(vm.autoPlayBackupAvailable)
		assertEquals(original, File(root, "autoPlay_previous").readText())
		verify(exactly = 1) { ui.showToast(R.string.autoMappingSaved) }
		vm.restoreAutoPlay()
		runUntil { !vm.autoMappingActive }
		assertEquals(original, File(root, "autoPlay").readText())
		assertTrue(vm.autoPlayBackupAvailable)
		verify(exactly = 1) { ui.showToast(R.string.autoPlayRestored) }
	}

	private class RecordingEngine : SoundRunner.Engine {
		val voices = mutableSetOf<Int>()
		var plays = 0
		override fun inspect(file: File) = AudioInspection.Readable(AudioFileFacts(44_100, 1, 1))
		override fun start() = true
		override fun stop() {}
		override fun decode(file: File) = OboeAudioEngine.DecodedAudio(shortArrayOf(1), 1, 1, 44100)
		override fun load(decoded: OboeAudioEngine.DecodedAudio) = 1
		override fun unloadSound(soundId: Int) {}
		override fun unloadAll() {}
		override fun play(soundId: Int, volumeL: Float, volumeR: Float, loop: Int) = (++plays).also { voices.add(it) }
		override fun stopVoice(stopKey: Int) { voices.remove(stopKey) }
		override fun stopAllVoices() { voices.clear() }
	}

	@Test
	fun savingStopsManualSoundAndRejectsNewPressesUntilItFinishes() {
		val lengths = HeldLengths()
		val engine = RecordingEngine()
		val vm = openPack(lengths, engine)
		vm.initState()
		vm.initPlayback()
		vm.requestAudioFocus = { true }
		runUntil { vm.unipack.soundGet(0, 0, 0)?.id == 1 }
		vm.padTouch(0, 0, true)
		assertTrue(engine.voices.isNotEmpty())
		vm.autoMapping()
		try {
			assertTrue(lengths.reading.await(5, TimeUnit.SECONDS))
			assertTrue("Saving must stop the sound already playing", engine.voices.isEmpty())
			val played = engine.plays
			vm.padTouch(0, 0, true, 7)
			assertEquals("New presses must wait until saving finishes", played, engine.plays)
		} finally {
			lengths.release.countDown()
			runUntil { !vm.autoMappingActive }
		}
	}

}
