package com.kimjisub.launchpad.db.repository

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.kimjisub.launchpad.db.dao.UnipackDao
import com.kimjisub.launchpad.db.ent.Unipack
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import java.util.Date

class UnipackRepositoryTest {

	private lateinit var dao: UnipackDao
	private lateinit var repository: UnipackRepository

	@Before
	fun setUp() {
		dao = mockk(relaxed = true)
		repository = UnipackRepository(dao)
	}

	@Test
	fun find_delegatesToDao() {
		val liveData = MutableLiveData<Unipack>()
		every { dao.find("test-id") } returns liveData

		val result = repository.find("test-id")

		assertSame(liveData, result)
		verify { dao.find("test-id") }
	}

	@Test
	fun getOrCreate_insertsWhenNotExists() = runTest {
		every { dao.exists("new-id") } returns false
		val resultLiveData = MutableLiveData<Unipack>()
		every { dao.find("new-id") } returns resultLiveData

		val result = repository.getOrCreate("new-id")

		verify { dao.insert(any()) }
		assertNotNull(result)
	}

	@Test
	fun getOrCreate_skipsInsertWhenExists() = runTest {
		every { dao.exists("existing-id") } returns true
		val liveData = MutableLiveData(Unipack.create("existing-id"))
		every { dao.find("existing-id") } returns liveData

		repository.getOrCreate("existing-id")

		verify(exactly = 0) { dao.insert(any()) }
	}

	@Test
	fun toggleBookmark_delegatesToDao() = runTest {
		repository.toggleBookmark("test-id")

		coVerify { dao.toggleBookmark("test-id") }
	}

	@Test
	fun totalOpenCount_delegatesToDao() {
		val liveData: LiveData<Long> = MutableLiveData(42L)
		every { dao.totalOpenCount() } returns liveData

		val result = repository.totalOpenCount()

		assertSame(liveData, result)
		verify { dao.totalOpenCount() }
	}

	@Test
	fun openCount_delegatesToDao() {
		val liveData: LiveData<Long> = MutableLiveData(5L)
		every { dao.openCount("test-id") } returns liveData

		val result = repository.openCount("test-id")

		assertSame(liveData, result)
		verify { dao.openCount("test-id") }
	}

	@Test
	fun lastOpenedAt_delegatesToDao() {
		val date = Date()
		val liveData: LiveData<Date> = MutableLiveData(date)
		every { dao.lastOpenedAt("test-id") } returns liveData

		val result = repository.lastOpenedAt("test-id")

		assertSame(liveData, result)
		verify { dao.lastOpenedAt("test-id") }
	}

	@Test
	fun recordOpen_incrementsCountAndSetsTimestamp() = runTest {
		val dateSlot = slot<Date>()
		every { dao.addOpenCount("test-id") } returns 1
		every { dao.setLastOpenedAt("test-id", capture(dateSlot)) } returns 1

		val before = Date()
		repository.recordOpen("test-id")
		val after = Date()

		coVerify { dao.addOpenCount("test-id") }
		coVerify { dao.setLastOpenedAt("test-id", any()) }

		// Verify the captured date is approximately now
		val capturedTime = dateSlot.captured.time
		assert(capturedTime >= before.time) { "Timestamp should be >= test start time" }
		assert(capturedTime <= after.time) { "Timestamp should be <= test end time" }
	}
}
