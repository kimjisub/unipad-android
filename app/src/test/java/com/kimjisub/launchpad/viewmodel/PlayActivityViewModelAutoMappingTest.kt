package com.kimjisub.launchpad.viewmodel

import com.kimjisub.launchpad.R.string
import com.kimjisub.launchpad.analytics.UsageAnalytics
import com.kimjisub.launchpad.db.repository.UnipackRepository
import com.kimjisub.launchpad.unipack.UniPackFolder
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Auto mapping rewrites the autoPlay file and then reads it back. Reading it back on the main thread
 * froze the play screen for packs whose autoPlay file has thousands of lines.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlayActivityViewModelAutoMappingTest {

	@get:Rule
	val tmp = TemporaryFolder()

	private val main = FakeMainDispatcher()

	@Volatile
	private var reloadThread: Thread? = null

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
		unmockkConstructor(UniPackFolder::class)
		Dispatchers.resetMain()
	}

	private fun createPack(): File {
		val root = tmp.newFolder("pack")
		File(root, "info").writeText("title=Test\nproducerName=Tester\nbuttonX=8\nbuttonY=8\nchain=1\n")
		File(root, "keySound").writeText("1 1 1 a.wav\n1 1 2 a.wav\n")
		File(root, "sounds").mkdir()
		File(root, "sounds/a.wav").writeText("")
		File(root, "autoPlay").writeText("o 1 1\nd 100\nf 1 1\no 1 2\nf 1 2\n")
		return root
	}

	@Test
	fun autoMapping_readsTheRewrittenAutoPlayFileOffTheMainThread() {
		val vm = PlayActivityViewModel(mockk<UnipackRepository>(), UsageAnalytics { _, _ -> })
		val pack = runBlocking { withTimeout(5_000) { vm.loadUnipackOnce(createPack().path).await() } }

		vm.autoMapping()
		val deadline = System.currentTimeMillis() + 5_000
		while (System.currentTimeMillis() < deadline) {
			main.runAll()
			if (reloadThread != null && !vm.autoMappingActive) break
			Thread.sleep(5)
		}

		assertNotNull(reloadThread)
		assertNotSame(main.mainThread, reloadThread)
		assertFalse(vm.autoMappingActive)
		// Rewritten as "d, t 1 1, d, t 1 2": each touch reads back as an on and an off.
		assertEquals(6, pack.autoPlayTable?.elements?.size)
	}

	@Test
	fun autoMapping_reportsAnyReadBackFailureInsteadOfCrashing() {
		every { anyConstructed<UniPackFolder>().reloadAutoPlay() } answers {
			reloadThread = Thread.currentThread()
			throw IllegalStateException("read back failed")
		}
		val vm = PlayActivityViewModel(mockk<UnipackRepository>(), UsageAnalytics { _, _ -> })
		val ui = mockk<PlayActivityViewModel.UiCallback>(relaxed = true)
		vm.uiCallback = ui
		runBlocking { withTimeout(5_000) { vm.loadUnipackOnce(createPack().path).await() } }

		vm.autoMapping()
		val deadline = System.currentTimeMillis() + 5_000
		while (System.currentTimeMillis() < deadline) {
			main.runAll()
			if (reloadThread != null && !vm.autoMappingActive) break
			Thread.sleep(5)
		}
		main.runAll()

		assertNotNull(reloadThread)

		assertFalse(vm.autoMappingActive)
		verify { ui.showToast(string.failed) }
	}
}
