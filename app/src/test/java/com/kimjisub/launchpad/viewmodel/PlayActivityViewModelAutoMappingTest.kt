package com.kimjisub.launchpad.viewmodel

import androidx.lifecycle.ViewModelStore
import com.kimjisub.launchpad.R
import com.kimjisub.launchpad.analytics.UsageAnalytics
import com.kimjisub.launchpad.db.repository.UnipackRepository
import com.kimjisub.launchpad.tool.SoundDurationReader
import com.kimjisub.launchpad.tool.UniPackAutoMapper
import com.kimjisub.launchpad.unipack.struct.AutoPlay
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** The auto mapping button on the play screen: the user hears of a failure, and leaving stops it. */
class PlayActivityViewModelAutoMappingTest {

	@get:Rule
	val tmp = TemporaryFolder()

	private val main = FakeMainDispatcher()
	private val ui = mockk<PlayActivityViewModel.UiCallback>(relaxed = true)
	private val store = ViewModelStore()
	private val original = "t 1 1\nd 100\nt 1 1\nd 100\n"
	private lateinit var root: File

	private object FixedLengths : SoundDurationReader {
		override fun durationMs(file: File) = 400
		override fun close() {}
	}

	@Before
	fun setUp() = Dispatchers.setMain(main)

	@After
	fun tearDown() {
		root.setWritable(true)
		store.clear()
		Dispatchers.resetMain()
	}

	private fun openPack(): PlayActivityViewModel {
		root = tmp.newFolder("pack")
		File(root, "info").writeText("title=Test\nproducerName=Tester\nbuttonX=8\nbuttonY=8\nchain=1\n")
		File(root, "keySound").writeText("1 1 1 a.wav\n")
		File(root, "sounds").mkdir()
		File(root, "sounds/a.wav").writeText("")
		File(root, "autoPlay").writeText(original)
		val vm = PlayActivityViewModel(
			mockk<UnipackRepository>(relaxed = true),
			UsageAnalytics { _, _ -> },
			autoMapper = { UniPackAutoMapper(it, { FixedLengths }) },
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
		verify(exactly = 0) { ui.showToast(any()) }
	}

	@Test
	fun aPackThatCannotBeWritten_showsTheFailure() {
		val vm = openPack()
		root.setWritable(false)
		File(root, "autoPlay").setWritable(false)

		vm.autoMapping()
		runUntil { !vm.autoMappingActive }

		verify { ui.showToast(R.string.failed) }
		assertEquals(original, File(root, "autoPlay").readText())
		assertEquals(listOf("autoPlay", "info", "keySound", "sounds"), files())
	}

	@Test
	fun leavingThePlayScreen_stopsTheMappingAndLeavesTheAutoPlay() {
		val vm = openPack()

		vm.autoMapping()
		store.clear()
		runUntil(1_000) { false }

		assertFalse(vm.autoMappingActive)
		assertEquals(original, File(root, "autoPlay").readText())
		assertEquals(listOf("autoPlay", "info", "keySound", "sounds"), files())
	}
}
