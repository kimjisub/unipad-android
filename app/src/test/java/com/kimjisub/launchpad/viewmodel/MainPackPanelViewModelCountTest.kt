package com.kimjisub.launchpad.viewmodel

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.kimjisub.launchpad.db.repository.UnipackRepository
import com.kimjisub.launchpad.unipack.UniPack
import com.kimjisub.launchpad.unipack.UniPackFolder
import io.mockk.mockk
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.io.File
import java.io.IOException

/**
 * Selecting a pack reads all of it to count its sounds and LEDs.
 *
 * A pack too large for memory threw OutOfMemoryError there, nothing caught it, and the whole app
 * closed before the pack was played.
 *
 * The pack list also keeps the panel model of every pack selected on it, also while one of them is
 * played from its own copy. Each model held the whole pack it had counted, so a played pack was in
 * memory twice and every pack looked at stayed in memory until the list screen closed.
 */
class MainPackPanelViewModelCountTest {

	@get:Rule(order = 0)
	val tmp = TemporaryFolder()
	@get:Rule(order = 1)
	val models = MainViewModelRule()
	private val app = mockk<Application>(relaxed = true)

	@Before
	fun setUp() {
		startKoin { modules(module { single { mockk<UnipackRepository>(relaxed = true) } }) }
	}

	@After
	fun tearDown() {
		stopKoin()
	}

	/** A failing read has read the sound table before it fails, as when keyLed breaks after keySound. */
	private class FailingPack(
		private val detailFailure: Throwable? = null,
		private val sizeFailure: Throwable? = null,
	) : UniPack() {
		override val id = "failing"
		override fun lastModified() = 0L
		override fun loadInfo() = this
		override fun loadDetail(): UniPack {
			detailFailure?.let {
				soundTable = Array(1) { Array(8) { arrayOfNulls(8) } }
				throw it
			}
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
		val panel = models.track(MainPackPanelViewModel.of(models, app, pack))
		runBlocking { panel.viewModelScope.coroutineContext.job.children.toList().joinAll() }
		return panel
	}

	private fun install(name: String): UniPackFolder {
		val root = File(tmp.root, name).apply { mkdirs() }
		File(root, "info").writeText("title=$name\nproducerName=Tester\nbuttonX=8\nbuttonY=8\nchain=1\n")
		File(root, "keySound").writeText("1 1 1 a.wav\n1 2 2 b.wav\n")
		File(root, "sounds").mkdirs()
		File(root, "sounds/a.wav").writeText("")
		File(root, "sounds/b.wav").writeText("")
		File(root, "keyLed").mkdirs()
		File(root, "keyLed/1 1 1").writeText("o 1 1 a 5\nd 100\nf 1 1\n")
		File(root, "autoPlay").writeText("t 1 1\nd 100\nt 2 2\n")
		return UniPackFolder(root).load() as UniPackFolder
	}

	private fun assertNoTables(pack: UniPack) {
		assertNull("sound table", pack.soundTable)
		assertNull("LED table", pack.ledAnimationTable)
		assertNull("autoplay table", pack.autoPlayTable)
		assertFalse("a released or failed read must not pass for a loaded one", pack.detailLoaded)
	}

	@Test
	fun outOfMemoryWhileCountingSoundsAndLeds_doesNotCloseTheApp() {
		openPanel(FailingPack(detailFailure = OutOfMemoryError("keySound")))
	}

	@Test
	fun readErrorWhileCountingSoundsAndLeds_doesNotCloseTheApp() {
		openPanel(FailingPack(detailFailure = IllegalStateException("broken keyLed")))
	}

	@Test
	fun readErrorWhileMeasuringTheFolder_doesNotCloseTheApp() {
		openPanel(FailingPack(sizeFailure = SecurityException("no access")))
	}

	@Test
	fun countsThatCouldNotBeRead_areShownAsUnknownInsteadOfMeasuringForever() {
		val panel = openPanel(FailingPack(detailFailure = OutOfMemoryError("keySound")))

		assertTrue(panel.countsUnknown)
		assertNull(panel.soundCount)
		assertNull(panel.ledCount)
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

	@Test
	fun countedPack_showsItsCountsWithoutKeepingItsTables() {
		val pack = install("Pack")
		val panel = openPanel(pack)

		assertEquals(2, panel.soundCount)
		assertEquals(1, panel.ledCount)
		assertFalse(panel.countsUnknown)
		assertNoTables(pack)
	}

	@Test
	fun everyPackSelectedOnTheList_letsItsTablesGo() {
		val packs = listOf(install("A"), install("B"), install("C"))
		packs.forEach { openPanel(it) }

		packs.forEach(::assertNoTables)
	}

	@Test
	fun releasedPack_canBeReadInFullAgain() {
		val pack = install("Pack")
		openPanel(pack)

		pack.loadDetail()

		assertTrue(pack.detailLoaded)
		assertNotNull(pack.soundTable)
		assertNotNull(pack.ledAnimationTable)
		assertNotNull(pack.autoPlayTable)
		assertEquals(2, pack.soundCount)
	}

	@Test
	fun readThatFailedHalfWay_letsGoOfWhatItHadRead() {
		val pack = FailingPack(detailFailure = OutOfMemoryError("keyLed"))
		val panel = openPanel(pack)

		assertTrue(panel.countsUnknown)
		assertNoTables(pack)
	}
}
