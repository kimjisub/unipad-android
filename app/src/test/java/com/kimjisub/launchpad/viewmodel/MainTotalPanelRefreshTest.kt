package com.kimjisub.launchpad.viewmodel

import android.app.Application
import com.kimjisub.launchpad.db.repository.UnipackRepository
import com.kimjisub.launchpad.manager.PreferenceManager
import com.kimjisub.launchpad.manager.WorkspaceManager
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

@OptIn(ExperimentalCoroutinesApi::class)
class MainTotalPanelRefreshTest {
	private val dispatcher = StandardTestDispatcher()
	private val ws = mockk<WorkspaceManager>()
	private lateinit var vm: MainTotalPanelViewModel

	@Before fun setUp() {
		Dispatchers.setMain(dispatcher)
		startKoin { modules(module { single { mockk<UnipackRepository>(relaxed = true) } }) }
		val app = mockk<Application>(relaxed = true)
		val prefs = mockk<PreferenceManager>(relaxed = true)
		every { prefs.sortMethod } returns 0
		coEvery { ws.getUnipacks() } returns arrayListOf()
		vm = MainTotalPanelViewModel(app, prefs, ws)
	}

	@After fun tearDown() {
		Dispatchers.resetMain()
		stopKoin()
	}

	@Test fun olderSizeAndCountCannotOverwriteTheLatestRefresh() = runTest(dispatcher) {
		val oldSize = CompletableDeferred<Long>()
		val oldCount = CompletableDeferred<Unit>()
		var sizeCalls = 0
		var countCalls = 0
		coEvery { ws.getAvailableWorkspacesSize() } coAnswers {
			if (sizeCalls++ == 0) withContext(NonCancellable) { oldSize.await() } else 2L * 1024 * 1024
		}
		coEvery { ws.getUnipacks() } coAnswers {
			val old = countCalls++ == 0
			if (old) withContext(NonCancellable) { oldCount.await() }
			ArrayList(List(if (old) 3 else 1) { mockk<com.kimjisub.launchpad.adapter.UniPackItem>() })
		}
		vm.update()
		runCurrent()
		vm.update()
		runCurrent()
		assertEquals("2", vm.unipackCapacity)
		assertEquals(1, vm.unipackCount)
		oldSize.complete(29L * 1024 * 1024)
		oldCount.complete(Unit)
		runCurrent()
		assertEquals("2", vm.unipackCapacity)
		assertEquals(1, vm.unipackCount)
	}
}
