package com.kimjisub.launchpad.db

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kimjisub.launchpad.db.repository.UnipackRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Runs the row delete through Room's generated DAO on an in-memory database. */
@RunWith(AndroidJUnit4::class)
class UnipackRepositoryDeleteTest {

	private lateinit var db: AppDatabase
	private lateinit var repo: UnipackRepository

	@Before
	fun setUp() {
		db = Room.inMemoryDatabaseBuilder(
			InstrumentationRegistry.getInstrumentation().targetContext,
			AppDatabase::class.java,
		).allowMainThreadQueries().build()
		repo = UnipackRepository(db.unipackDAO())
	}

	@After
	fun tearDown() {
		db.close()
	}

	private fun history(id: String): Pair<Long, Boolean> =
		db.openHelper.readableDatabase.query("SELECT openCount, bookmark FROM Unipack WHERE id=?", arrayOf(id)).use {
			assertTrue("No row for $id", it.moveToFirst())
			it.getLong(0) to (it.getInt(1) == 1)
		}

	private fun seedHistory(id: String) {
		repo.getOrCreate(id)
		repo.recordOpen(id)
		repo.toggleBookmark(id)
	}

	@Test
	fun delete_removesOnlyThatPacksRow() {
		seedHistory("Pack A")
		seedHistory("Pack B")

		assertTrue(repo.delete("Pack A"))

		assertFalse(db.unipackDAO().exists("Pack A"))
		assertEquals(1L to true, history("Pack B"))
	}

	@Test
	fun reinstallAfterDelete_startsWithoutHistory() {
		seedHistory("Pack A")

		assertTrue(repo.delete("Pack A"))
		repo.getOrCreate("Pack A")

		assertEquals(0L to false, history("Pack A"))
	}

	@Test
	fun delete_missingRowCountsAsDeleted() {
		assertTrue(repo.delete("never installed"))
	}
}
