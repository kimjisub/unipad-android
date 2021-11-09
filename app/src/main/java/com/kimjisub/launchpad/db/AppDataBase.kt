package com.kimjisub.launchpad.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.kimjisub.launchpad.db.dao.UniPackDAO
import com.kimjisub.launchpad.db.dao.UniPackOpenDAO
import com.kimjisub.launchpad.db.ent.UniPackENT
import com.kimjisub.launchpad.db.ent.UniPackOpenENT

@Database(entities = [UniPackENT::class, UniPackOpenENT::class], version = 1)
abstract class AppDataBase : RoomDatabase() {
	abstract fun unipackDAO(): UniPackDAO?
	abstract fun unipackOpenDAO(): UniPackOpenDAO?

	companion object {
		private var INSTANCE: AppDataBase? = null

		fun getInstance(context: Context): AppDataBase? {
			if (INSTANCE == null) {
				synchronized(AppDataBase::class) {
					INSTANCE = Room.databaseBuilder(
						context.applicationContext,
						AppDataBase::class.java, "UniPad.db"
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