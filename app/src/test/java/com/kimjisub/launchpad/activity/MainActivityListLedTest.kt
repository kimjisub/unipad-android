package com.kimjisub.launchpad.activity

import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import androidx.lifecycle.lifecycleScope
import com.kimjisub.launchpad.adapter.UniPackItem
import com.kimjisub.launchpad.manager.WorkspaceManager
import com.kimjisub.launchpad.midi.MidiConnection
import com.kimjisub.launchpad.midi.controller.MidiController
import com.kimjisub.launchpad.midi.driver.DriverRef
import com.kimjisub.launchpad.unipack.UniPack
import com.kimjisub.launchpad.viewmodel.MainTotalPanelViewModel
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.Runs
import io.mockk.spyk
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** Exercises the real refresh completion and navigation LED paths with a held folder read. */
@OptIn(ExperimentalCoroutinesApi::class)
class MainActivityListLedTest {
	private lateinit var activity: MainActivity
	private lateinit var driver: DriverRef
	private lateinit var workspace: WorkspaceManager
	private lateinit var mainController: MidiController

	@Before
	fun setUp() {
		ArchTaskExecutor.getInstance().setDelegate(object : TaskExecutor() {
			override fun executeOnDiskIO(runnable: Runnable) = runnable.run()
			override fun postToMainThread(runnable: Runnable) = runnable.run()
			override fun isMainThread() = true
		})
		activity = spyk(MainActivity())
		workspace = mockk()
		every { workspace.migrateOldAppStorageFolder() } just Runs
		every { activity.ws } returns workspace
		field("totalPanelVM").set(activity, mockk<MainTotalPanelViewModel>(relaxed = true))
		mainController = method("getMidiController").invoke(activity) as MidiController
		driver = mockk(relaxed = true)
		mockkObject(MidiConnection)
		every { MidiConnection.driver } returns driver
		MidiConnection.controller = mainController
	}

	@After
	fun tearDown() {
		MidiConnection.controller = null
		unmockkObject(MidiConnection)
		Dispatchers.resetMain()
		ArchTaskExecutor.getInstance().setDelegate(null)
	}

	@Test
	fun refreshCompletingAfterPlayTakesController_doesNotSendListLeds() = runTest {
		Dispatchers.setMain(StandardTestDispatcher(testScheduler))
		val entered = CompletableDeferred<Unit>()
		val release = CompletableDeferred<Unit>()
		val pack = pack("one")
		coEvery { workspace.getUnipacks() } coAnswers {
			entered.complete(Unit)
			release.await()
			arrayListOf(pack)
		}
		try {
			method("update").invoke(activity)
			entered.await()
			MidiConnection.removeController(mainController)
			MidiConnection.controller = mockk<MidiController>(relaxed = true)
		} finally {
			release.complete(Unit)
		}
		activity.lifecycleScope.coroutineContext[Job]!!.children.toList().joinAll()

		assertEquals(listOf(pack), method("getVisibleList").invoke(activity))
		verify(exactly = 0) { driver.sendFunctionKeyLed(any(), any()) }

		MidiConnection.controller = mainController
		method("showSelectLPUI").invoke(activity)
		verify { driver.sendFunctionKeyLed(0, 5); driver.sendFunctionKeyLed(1, 63); driver.sendFunctionKeyLed(2, 0) }
	}

	@Test
	fun refreshCompletingWhilePausedWithoutController_doesNotSendListLeds() = runTest {
		Dispatchers.setMain(StandardTestDispatcher(testScheduler))
		coEvery { workspace.getUnipacks() } coAnswers {
			MidiConnection.removeController(mainController)
			arrayListOf(pack("one"))
		}
		method("update").invoke(activity)
		activity.lifecycleScope.coroutineContext[Job]!!.children.toList().joinAll()
		verify(exactly = 0) { driver.sendFunctionKeyLed(any(), any()) }
	}

	@Test
	fun refreshCompletingWhileActive_updatesListAndLeds() = runTest {
		Dispatchers.setMain(StandardTestDispatcher(testScheduler))
		val pack = pack("one")
		coEvery { workspace.getUnipacks() } returns arrayListOf(pack)
		method("update").invoke(activity)
		activity.lifecycleScope.coroutineContext[Job]!!.children.toList().joinAll()
		assertEquals(listOf(pack), method("getVisibleList").invoke(activity))
		verify { driver.sendFunctionKeyLed(0, 5); driver.sendFunctionKeyLed(1, 63); driver.sendFunctionKeyLed(2, 0) }
	}

	@Test
	fun activeSearchAndNavigation_keepUpdatingResultLeds() {
		@Suppress("UNCHECKED_CAST")
		val list = field("unipackList").get(activity) as MutableList<UniPackItem>
		list.addAll(listOf(pack("one"), pack("two")))
		method("updateSearchQuery", String::class.java).invoke(activity, "one")
		verify { driver.sendFunctionKeyLed(0, 5); driver.sendFunctionKeyLed(1, 63); driver.sendFunctionKeyLed(2, 0) }

		method("next").invoke(activity)
		verify { driver.sendFunctionKeyLed(1, 5); driver.sendFunctionKeyLed(2, 61) }
		method("updateSearchQuery", String::class.java).invoke(activity, "")
		method("next").invoke(activity)
		verify { driver.sendFunctionKeyLed(0, 63) }
		method("prev").invoke(activity)
		assertEquals("one", (method("getSelectedItem").invoke(activity) as UniPackItem).unipack.title)
	}

	private fun pack(path: String): UniPackItem {
		val pack = mockk<UniPack>(relaxed = true)
		every { pack.getPathString() } returns path
		every { pack.title } returns path
		return UniPackItem(pack, mockk(relaxed = true))
	}

	private fun field(name: String) = MainActivity::class.java.getDeclaredField(name).apply { isAccessible = true }
	private fun method(name: String, vararg parameters: Class<*>) =
		MainActivity::class.java.getDeclaredMethod(name, *parameters).apply { isAccessible = true }
}
