package com.kimjisub.launchpad.viewmodel

import android.app.Application
import com.kimjisub.launchpad.db.repository.UnipackRepository
import com.kimjisub.launchpad.unipack.UniPackFolder
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.io.File

/**
 * A pack deleted and installed again under the same name lands in the same folder path. The detail
 * panel kept the first copy's model for that path and showed its title, size and counts.
 */
class MainPackPanelViewModelReinstallTest {

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

	private fun openPanel(pack: UniPackFolder) = models.track(MainPackPanelViewModel.of(models, app, pack))

	private fun install(root: File, title: String, folderTime: Long): UniPackFolder {
		root.deleteRecursively()
		root.mkdirs()
		File(root, "info").writeText("title=$title\nproducerName=Tester\nbuttonX=8\nbuttonY=8\nchain=1\n")
		File(root, "keySound").writeText("1 1 1 a.wav\n")
		root.setLastModified(folderTime)
		return UniPackFolder(root).load() as UniPackFolder
	}

	@Test
	fun reinstalledPackUnderTheSameName_getsItsOwnDetail() {
		val root = File(tmp.root, "Pack")
		val old = install(root, "Old", 1_700_000_000_000)
		assertEquals("Old", openPanel(old).unipack.title)

		val new = install(root, "New", 1_700_000_100_000)
		assertEquals(old.getPathString(), new.getPathString())

		val panel = openPanel(new)
		assertSame(new, panel.unipack)
		assertEquals("New", panel.unipack.title)
	}

	@Test
	fun samePackReadAgain_keepsItsDetail() {
		val root = File(tmp.root, "Pack")
		val first = openPanel(install(root, "Same", 1_700_000_000_000))

		val again = UniPackFolder(root).load() as UniPackFolder

		assertSame(first, openPanel(again))
	}
}
