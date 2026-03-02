package com.kimjisub.launchpad.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.kimjisub.launchpad.db.dao.UnipackDao
import com.kimjisub.launchpad.db.ent.Unipack
import com.kimjisub.launchpad.db.util.DateConverter

@TypeConverters(DateConverter::class)
@Database(entities = [Unipack::class], version = 2)
abstract class AppDatabase : RoomDatabase() {
	abstract fun unipackDAO(): UnipackDao

	companion object {
		private const val DATABASE_NAME = "UniPad.db"
		@Volatile
		private var INSTANCE: AppDatabase? = null

		fun getInstance(context: Context): AppDatabase {
			return INSTANCE ?: synchronized(this) {
				INSTANCE ?: Room.databaseBuilder(
					context.applicationContext,
					AppDatabase::class.java, DATABASE_NAME
				).fallbackToDestructiveMigration(dropAllTables = true).build().also {
					INSTANCE = it
				}
			}
		}
	}
}