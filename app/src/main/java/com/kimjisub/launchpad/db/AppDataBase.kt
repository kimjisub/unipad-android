package com.kimjisub.launchpad.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.kimjisub.launchpad.db.dao.UnipackDAO
import com.kimjisub.launchpad.db.dao.UnipackOpenDAO
import com.kimjisub.launchpad.db.ent.UnipackENT
import com.kimjisub.launchpad.db.ent.UnipackOpenENT

@Database(entities = [UnipackENT::class, UnipackOpenENT::class], version = 1)
abstract class AppDataBase : RoomDatabase() {
	abstract fun unipackDAO(): UnipackDAO?
	abstract fun unipackOpenDAO(): UnipackOpenDAO?

	companion object {
		private var INSTANCE: AppDataBase? = null

		fun getInstance(context: Context): AppDataBase? {
			if (INSTANCE == null) {
				synchronized(AppDataBase::class) {
					INSTANCE = Room.databaseBuilder(
						context.applicationContext,
						AppDataBase::class.java, "UniPad.db"
					)
						.build()
				}
			}
			return INSTANCE
		}

		fun destroyInstance() {
			INSTANCE = null
		}
	}
}