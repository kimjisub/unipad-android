package com.kimjisub.launchpad.di

import android.content.Context
import android.content.SharedPreferences
import com.kimjisub.launchpad.db.AppDatabase
import com.kimjisub.launchpad.db.dao.UnipackDao
import com.kimjisub.launchpad.db.repository.UnipackRepository
import com.kimjisub.launchpad.manager.PreferenceManager
import com.kimjisub.launchpad.manager.WorkspaceManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.test.KoinTest
import org.koin.test.get
import org.koin.test.verify.verify

class KoinModuleTest : KoinTest {

	private lateinit var mockContext: Context
	private lateinit var mockDao: UnipackDao
	private lateinit var mockDb: AppDatabase

	@Before
	fun setUp() {
		mockContext = mockk(relaxed = true)
		val mockSharedPrefs = mockk<SharedPreferences>(relaxed = true)
		every { mockContext.getSharedPreferences(any(), any()) } returns mockSharedPrefs
		every { mockContext.applicationContext } returns mockContext

		mockDao = mockk(relaxed = true)
		mockDb = mockk(relaxed = true)
		mockkObject(AppDatabase.Companion)
		every { AppDatabase.getInstance(any()) } returns mockDb
		every { mockDb.unipackDAO() } returns mockDao
	}

	@After
	fun tearDown() {
		stopKoin()
		unmockkObject(AppDatabase.Companion)
	}

	@OptIn(org.koin.core.annotation.KoinExperimentalAPI::class)
	@Test
	fun `appModule verifies dependency graph`() {
		appModule.verify(
			extraTypes = listOf(
				Context::class,
				UnipackDao::class,
				AppDatabase::class,
				SharedPreferences::class,
			)
		)
	}

	@Test
	fun `all singletons resolve correctly with mocked dependencies`() {
		startKoin {
			androidContext(mockContext)
			modules(appModule)
		}

		val repo: UnipackRepository = get()
		val prefs: PreferenceManager = get()
		val workspace: WorkspaceManager = get()

		assertNotNull(repo)
		assertNotNull(prefs)
		assertNotNull(workspace)
	}

	@Test
	fun `singletons return same instance on multiple retrievals`() {
		startKoin {
			androidContext(mockContext)
			modules(appModule)
		}

		val repo1: UnipackRepository = get()
		val repo2: UnipackRepository = get()
		assertSame("UnipackRepository should be singleton", repo1, repo2)

		val prefs1: PreferenceManager = get()
		val prefs2: PreferenceManager = get()
		assertSame("PreferenceManager should be singleton", prefs1, prefs2)

		val ws1: WorkspaceManager = get()
		val ws2: WorkspaceManager = get()
		assertSame("WorkspaceManager should be singleton", ws1, ws2)
	}

	@Test
	fun `WorkspaceManager receives UnipackRepository via Koin injection`() {
		startKoin {
			androidContext(mockContext)
			modules(appModule)
		}

		val workspace: WorkspaceManager = get()
		val repo: UnipackRepository = get()

		assertNotNull("WorkspaceManager.repo should be injected", workspace.repo)
		assertSame("WorkspaceManager.repo should be same singleton", repo, workspace.repo)
	}
}
