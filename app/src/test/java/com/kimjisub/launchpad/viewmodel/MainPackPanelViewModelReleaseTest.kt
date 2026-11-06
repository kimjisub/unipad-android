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

/**
 * The pack list keeps a detail panel model for every pack selected on it, also while one of them is
 * played from its own copy. Each model held the whole pack it had counted, so a played pack was in
 * memory twice and every pack looked at stayed in memory until the list screen closed.
 */
class MainPackPanelViewModelReleaseTest {

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
		assertFalse(pack.detailLoaded)
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
		val pack = object : UniPack() {
			override val id = "half"
			override fun lastModified() = 0L
			override fun loadInfo() = this
			override fun loadDetail(): UniPack {
				soundTable = Array(1) { Array(8) { arrayOfNulls(8) } }
				throw OutOfMemoryError("keyLed")
			}
			override fun checkFile() = Unit
			override fun delete() = false
			override fun getPathString() = "/packs/half"
			override fun getByteSize() = 0L
		}
		val panel = openPanel(pack)

		assertTrue(panel.countsUnknown)
		assertNoTables(pack)
	}
}
