package com.kimjisub.launchpad.db.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.kimjisub.launchpad.db.ent.UniPackOpenENT

@Dao
abstract class UniPackOpenDAO {
	@Insert
	abstract fun insert(item: UniPackOpenENT?)

	@get:Query("SELECT COUNT(*) FROM UniPackOpenENT")
	abstract val count: LiveData<Int>

	@Query("SELECT COUNT(*) FROM UniPackOpenENT WHERE path=:path")
	abstract fun getCount(path: String?): LiveData<Int>

	@Query("SELECT COUNT(*) FROM UniPackOpenENT WHERE path=:path")
	abstract fun getCountSync(path: String?): Int

	@Query("SELECT * FROM UniPackOpenENT WHERE path=:path ORDER BY created_at DESC LIMIT 1")
	abstract fun getLastOpenedDate(path: String?): LiveData<UniPackOpenENT>

	@Query("SELECT * FROM UniPackOpenENT WHERE path=:path ORDER BY created_at DESC LIMIT 1")
	abstract fun getLastOpenedDateSync(path: String?): UniPackOpenENT?
}