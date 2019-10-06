package com.kimjisub.launchpad.db.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.kimjisub.launchpad.db.ent.UnipackOpenENT

@Dao
abstract class UnipackOpenDAO {
	@Insert
	abstract fun insert(item: UnipackOpenENT?)

	@get:Query("SELECT COUNT(*) FROM UnipackOpenENT")
	abstract val count: LiveData<Int>

	@Query("SELECT COUNT(*) FROM UnipackOpenENT WHERE path=:path")
	abstract fun getCount(path: String?): LiveData<Int>
}