package com.kimjisub.launchpad.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import com.kimjisub.launchpad.db.dao.UnipackDao
import com.kimjisub.launchpad.db.ent.Unipack
import com.kimjisub.launchpad.db.migration.MIGRATION_1_2
import com.kimjisub.launchpad.db.util.DateConverter

@TypeConverters(DateConverter::class)
@Database(entities = [Unipack::class], version = 2, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
	abstract fun unipackDAO(): UnipackDao

	companion object {
		private const val DATABASE_NAME = "UniPad.db"

		// One entry per version step. Bumping the version without adding one here fails MigrationPolicyTest.
		val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2)

		@Volatile
		private var INSTANCE: AppDatabase? = null

		fun getInstance(context: Context): AppDatabase {
			return INSTANCE ?: synchronized(this) {
				INSTANCE ?: applyMigrationPolicy(
					Room.databaseBuilder(
						context.applicationContext,
						AppDatabase::class.java, DATABASE_NAME
					)
				).build().also {
					INSTANCE = it
				}
			}
		}

		// Upgrades never fall back to wiping: a missing migration has to fail in tests, not delete every
		// user's bookmarks and play history in production.
		//
		// Downgrades do wipe. They happen when older code opens a newer database: a rollback shipped as the
		// old code under a higher versionCode, or an older APK sideloaded over a newer one. Room cannot migrate
		// down, so the alternative is every query throwing IllegalStateException, starting with the ones the main
		// screen runs at launch (pack list, total play count). The only way out of that is clearing app data,
		// which loses the same rows.
		internal fun applyMigrationPolicy(
			builder: RoomDatabase.Builder<AppDatabase>,
		): RoomDatabase.Builder<AppDatabase> =
			builder
				.addMigrations(*MIGRATIONS)
				.fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
	}
}
