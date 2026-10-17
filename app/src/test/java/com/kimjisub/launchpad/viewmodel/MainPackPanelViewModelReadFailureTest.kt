package com.kimjisub.launchpad.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewModelScope
import com.kimjisub.launchpad.db.repository.UnipackRepository
import com.kimjisub.launchpad.unipack.UniPack
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.io.IOException
import java.util.Collections

/**
 * Selecting a pack reads all of it to count its sounds and LEDs. A pack too large for memory threw
 * OutOfMemoryError there, nothing caught it, and the whole app closed before the pack was played.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainPackPanelViewModelReadFailureTest {

	private val app = mockk<Application>(relaxed = true)
	private val owner = object : ViewModelStoreOwner {
		override val viewModelStore = ViewModelStore()
	}
	private val uncaught: MutableList<Throwable> = Collections.synchronizedList(mutableListOf())
	private var previousHandler: Thread.UncaughtExceptionHandler? = null

	@Before
	fun setUp() {
		Dispatchers.setMain(UnconfinedTestDispatcher())
		startKoin { modules(module { single { mockk<UnipackRepository>(relaxed = true) } }) }
		previousHandler = Thread.getDefaultUncaughtExceptionHandler()
		Thread.setDefaultUncaughtExceptionHandler { _, e -> uncaught += e }
	}

	@After
	fun tearDown() {
		Thread.setDefaultUncaughtExceptionHandler(previousHandler)
		owner.viewModelStore.clear()
		stopKoin()
		Dispatchers.resetMain()
	}

	private class FailingPack(
		private val detailFailure: Throwable? = null,
		private val sizeFailure: Throwable? = null,
	) : UniPack() {
		override val id = "failing"
		override fun lastModified() = 0L
		override fun loadInfo() = this
		override fun loadDetail(): UniPack {
			detailFailure?.let { throw it }
			soundCount = 3
			ledTableCount = 2
			detailLoaded = true
			return this
		}
		override fun checkFile() = Unit
		override fun delete() = false
		override fun getPathString() = "/packs/failing"
		override fun getByteSize(): Long = sizeFailure?.let { throw it } ?: 0L
	}

	/** The panel with every read it started finished, including the handling of any failure. */
	private fun openPanel(pack: UniPack): MainPackPanelViewModel {
		val panel = MainPackPanelViewModel.of(owner, app, pack)
		runBlocking { panel.viewModelScope.coroutineContext.job.children.toList().joinAll() }
		return panel
	}

	@Test
	fun outOfMemoryWhileCountingSoundsAndLeds_doesNotCloseTheApp() {
		openPanel(FailingPack(detailFailure = OutOfMemoryError("keySound")))

		assertEquals(emptyList<Throwable>(), uncaught.toList())
	}

	@Test
	fun readErrorWhileCountingSoundsAndLeds_doesNotCloseTheApp() {
		openPanel(FailingPack(detailFailure = IllegalStateException("broken keyLed")))

		assertEquals(emptyList<Throwable>(), uncaught.toList())
	}

	@Test
	fun readErrorWhileMeasuringTheFolder_doesNotCloseTheApp() {
		openPanel(FailingPack(sizeFailure = SecurityException("no access")))

		assertEquals(emptyList<Throwable>(), uncaught.toList())
	}

	@Test
	fun countsThatCouldNotBeRead_areShownAsUnknownInsteadOfMeasuringForever() {
		val pack = FailingPack(detailFailure = OutOfMemoryError("keySound"))
		val panel = openPanel(pack)

		assertTrue(panel.countsUnknown)
		assertNull(panel.soundCount)
		assertNull(panel.ledCount)
		assertFalse("a failed read must not pass for a finished one", pack.detailLoaded)
	}

	@Test
	fun folderSizeThatCouldNotBeRead_staysEmpty() {
		val panel = openPanel(FailingPack(sizeFailure = IOException("gone")))

		assertNull(panel.fileSize)
		assertEquals(3, panel.soundCount)
		assertFalse(panel.countsUnknown)
	}

	@Test
	fun countsReadNormally_areShown() {
		val panel = openPanel(FailingPack())

		assertEquals(3, panel.soundCount)
		assertEquals(2, panel.ledCount)
		assertFalse(panel.countsUnknown)
	}
}
