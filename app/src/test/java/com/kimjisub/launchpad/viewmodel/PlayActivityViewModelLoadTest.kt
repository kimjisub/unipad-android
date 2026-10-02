package com.kimjisub.launchpad.viewmodel

import com.kimjisub.launchpad.analytics.UsageAnalytics
import com.kimjisub.launchpad.db.repository.UnipackRepository
import com.kimjisub.launchpad.unipack.UniPackFolder
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.unmockkConstructor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * PlayActivity opens its pack through [PlayActivityViewModel.loadUnipackOnce]. Reading the keyLED files
 * on the main thread made a slow storage an ANR (Crashlytics 03679c09, 4.0.1:
 * UniPackFolder.keyLed from PlayActivity.onCreate).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlayActivityViewModelLoadTest {

	@get:Rule
	val tmp = TemporaryFolder()

	private val main = FakeMainDispatcher()

	@Volatile
	private var readingThread: Thread? = null

	@Before
	fun setUp() {
		Dispatchers.setMain(main)
		mockkConstructor(UniPackFolder::class)
		every { anyConstructed<UniPackFolder>().loadDetailWithProgress(any()) } answers {
			readingThread = Thread.currentThread()
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
		File(root, "keySound").writeText("1 1 1 a.wav\n")
		File(root, "sounds").mkdir()
		File(root, "sounds/a.wav").writeText("")
		File(root, "keyLed").mkdir()
		File(root, "keyLed/1 1 1 1").writeText("o 1 1 3\nd 100\n")
		return root
	}

	@Test
	fun loadUnipackOnce_readsThePackFilesOffTheMainThread() {
		val vm = PlayActivityViewModel(mockk<UnipackRepository>(), UsageAnalytics { _, _ -> })

		val pack = runBlocking { withTimeout(5_000) { vm.loadUnipackOnce(createPack().path).await() } }

		assertEquals(1, pack.ledTableCount)
		assertNotNull(readingThread)
		assertNotSame(main.mainThread, readingThread)
	}
}
