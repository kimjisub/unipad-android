package com.kimjisub.launchpad.viewmodel

import android.app.Application
import com.kimjisub.launchpad.R
import com.kimjisub.launchpad.db.repository.UnipackRepository
import io.mockk.mockk
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

/**
 * The main screen's view model survives a language change, so the sort names it hands out must be
 * resource ids the screen reads, not text read once from the application.
 */
class MainTotalPanelViewModelSortNameTest {

	private val app = mockk<Application>(relaxed = true)

	@Before
	fun setUp() {
		startKoin { modules(module { single { mockk<UnipackRepository>(relaxed = true) } }) }
	}

	@After
	fun tearDown() {
		stopKoin()
	}

	private fun viewModel() = MainTotalPanelViewModel(app, mockk(relaxed = true), mockk(relaxed = true))

	@Test
	fun sortNamesAreNotReadFromTheApplication() {
		viewModel()

		verify(exactly = 0) { app.getString(any()) }
	}

	@Test
	fun sortNamesAreStringResources() {
		assertEquals(
			listOf(R.string.sort_title, R.string.sort_producer, R.string.sort_download_date),
			viewModel().sortMethodTitleResList,
		)
	}
}
