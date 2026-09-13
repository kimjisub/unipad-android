package com.kimjisub.launchpad.db

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kimjisub.launchpad.db.migration.MIGRATION_1_2
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Builds databases from the exported schemas in app/schemas and opens them the way the app does.
 * The JVM Migration1To2Test runs the same SQL without a device.
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

	private val instrumentation = InstrumentationRegistry.getInstrumentation()
	private val context = instrumentation.targetContext

	@get:Rule
	val helper = MigrationTestHelper(instrumentation, AppDatabase::class.java)

	@After
	fun tearDown() {
		context.deleteDatabase(TEST_DB)
	}

	@Test
	fun migrate1To2_keepsBookmarksAndPlayHistory() {
		helper.createDatabase(TEST_DB, 1).use { db ->
			db.execSQL("INSERT INTO UniPackENT (path, padTouch, bookmark, created_at) VALUES ('Pack A', 0, 1, 1000)")
			db.execSQL("INSERT INTO UniPackOpenENT (path, created_at) VALUES ('Pack A', 2000)")
			db.execSQL(
				"INSERT INTO UniPackENT (path, padTouch, bookmark, created_at) " +
					"VALUES ('/storage/emulated/0/Unipad/Pack A', 0, 0, 5000)"
			)
			db.execSQL("INSERT INTO UniPackOpenENT (path, created_at) VALUES ('/storage/emulated/0/Unipad/Pack A', 6000)")
			db.execSQL("INSERT INTO UniPackENT (path, padTouch, bookmark, created_at) VALUES ('Pack B', 0, 0, 7000)")
		}

		// validateDroppedTables = true: the v1 tables must be gone, not left behind.
		helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2).use { db ->
			db.query("SELECT id, bookmark, openCount, lastOpenedAt, createdAt FROM Unipack ORDER BY id").use { c ->
				assertEquals(2, c.count)

				assertTrue(c.moveToNext())
				assertEquals("Pack A", c.getString(0))
				assertEquals(1, c.getInt(1))
				assertEquals(2L, c.getLong(2))
				assertEquals(6000L, c.getLong(3))
				assertEquals(1000L, c.getLong(4))

				assertTrue(c.moveToNext())
				assertEquals("Pack B", c.getString(0))
				assertEquals(0, c.getInt(1))
				assertEquals(0L, c.getLong(2))
				assertTrue(c.isNull(3))
				assertEquals(7000L, c.getLong(4))
			}
		}
	}

	@Test
	fun v1Database_opensThroughTheAppBuilder() {
		helper.createDatabase(TEST_DB, 1).use { db ->
			db.execSQL("INSERT INTO UniPackENT (path, padTouch, bookmark, created_at) VALUES ('Pack A', 0, 1, 1000)")
		}

		val room = AppDatabase.applyMigrationPolicy(
			Room.databaseBuilder(context, AppDatabase::class.java, TEST_DB)
		).build()
		try {
			assertTrue(room.unipackDAO().exists("Pack A"))
		} finally {
			room.close()
		}
	}

	@Test
	fun newerDatabase_isWipedInsteadOfThrowing() {
		helper.createDatabase(TEST_DB, 2).use { db ->
			db.execSQL("INSERT INTO Unipack (id, bookmark, openCount, lastOpenedAt, createdAt) VALUES ('Pack A', 1, 3, NULL, 1000)")
			// As if a later build had moved the schema to version 3.
			db.version = 3
		}

		val room = AppDatabase.applyMigrationPolicy(
			Room.databaseBuilder(context, AppDatabase::class.java, TEST_DB)
		).build()
		try {
			assertFalse(room.unipackDAO().exists("Pack A"))
			assertEquals(2, room.openHelper.readableDatabase.version)
		} finally {
			room.close()
		}
	}

	private companion object {
		const val TEST_DB = "migration-test.db"
	}
}
