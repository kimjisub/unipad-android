package com.kimjisub.launchpad.db.migration

import androidx.room.RoomDatabase
import com.kimjisub.launchpad.db.AppDatabase
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MigrationPolicyTest {

	@Test
	fun `every exported schema version has a migration from the one before`() {
		val versions = ExportedSchemas.versions()
		assertTrue("no exported schemas in ${ExportedSchemas.dir.absolutePath}", versions.isNotEmpty())
		assertEquals("exported schema versions have a gap", (1..versions.last()).toList(), versions)

		val steps = AppDatabase.MIGRATIONS.map { it.startVersion to it.endVersion }.toSet()
		for (from in 1 until versions.last()) {
			assertTrue(
				"schema ${from + 1} is exported but AppDatabase.MIGRATIONS has no $from -> ${from + 1}; " +
					"without it every user on $from crashes on launch",
				(from to from + 1) in steps,
			)
		}
	}

	@Test
	fun `builder registers the migrations and only wipes on downgrade`() {
		// Strict mock: any other builder call, fallbackToDestructiveMigration included, throws.
		val builder = mockk<RoomDatabase.Builder<AppDatabase>>()
		every { builder.addMigrations(*anyVararg()) } returns builder
		every { builder.fallbackToDestructiveMigrationOnDowngrade(any()) } returns builder

		AppDatabase.applyMigrationPolicy(builder)

		verify { builder.addMigrations(*AppDatabase.MIGRATIONS) }
		verify { builder.fallbackToDestructiveMigrationOnDowngrade(true) }
		confirmVerified(builder)
	}
}
