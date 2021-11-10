package com.kimjisub.launchpad.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.kimjisub.launchpad.db.dao.UnipackDao
import com.kimjisub.launchpad.db.ent.Unipack
import com.kimjisub.launchpad.db.util.DateConverter

@TypeConverters(DateConverter::class)
@Database(entities = [Unipack::class], version = 2)
abstract class AppDatabase : RoomDatabase() {
	abstract fun unipackDAO(): UnipackDao

	companion object {
		private var INSTANCE: AppDatabase? = null

		private val MIGRATION_1_2 = object : Migration(1, 2) {
			override fun migrate(database: SupportSQLiteDatabase) {
				database.execSQL("DROP TABLE UniPackENT")
				database.execSQL("DROP TABLE UniPackOpenENT")
			}
		}

		fun getInstance(context: Context): AppDatabase? {
			if (INSTANCE == null) {
				synchronized(AppDatabase::class) {
					INSTANCE = Room.databaseBuilder(
						context.applicationContext,
						AppDatabase::class.java, "UniPad.db"
					).addMigrations(MIGRATION_1_2).build()
				}
			}
			return INSTANCE
		}

		fun destroyInstance() {
			INSTANCE = null
		}
	}
}