package com.kimjisub.launchpad.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.kimjisub.launchpad.db.dao.UniPackDao
import com.kimjisub.launchpad.db.dao.UniPackOpenDao
import com.kimjisub.launchpad.db.ent.UniPackENT
import com.kimjisub.launchpad.db.ent.UniPackOpenENT

@Database(entities = [UniPackENT::class, UniPackOpenENT::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
	abstract fun unipackDAO(): UniPackDao
	abstract fun unipackOpenDAO(): UniPackOpenDao

	companion object {
		private var INSTANCE: AppDatabase? = null

		fun getInstance(context: Context): AppDatabase? {
			if (INSTANCE == null) {
				synchronized(AppDatabase::class) {
					INSTANCE = Room.databaseBuilder(
						context.applicationContext,
						AppDatabase::class.java, "UniPad.db"
					).build()
				}
			}
			return INSTANCE
		}

		fun destroyInstance() {
			INSTANCE = null
		}
	}
}