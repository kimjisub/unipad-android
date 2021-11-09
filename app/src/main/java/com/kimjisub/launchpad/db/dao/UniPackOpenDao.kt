package com.kimjisub.launchpad.db.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.kimjisub.launchpad.db.ent.UniPackOpenENT

@Dao
interface UniPackOpenDao {
	@Insert
	fun insert(item: UniPackOpenENT?)

	@get:Query("SELECT COUNT(*) FROM UniPackOpenENT")
	val count: LiveData<Int>

	@Query("SELECT COUNT(*) FROM UniPackOpenENT WHERE path=:path")
	fun getCount(path: String?): LiveData<Int>

	@Query("SELECT COUNT(*) FROM UniPackOpenENT WHERE path=:path")
	fun getCountSync(path: String?): Int

	@Query("SELECT * FROM UniPackOpenENT WHERE path=:path ORDER BY created_at DESC LIMIT 1")
	fun getLastOpenedDate(path: String?): LiveData<UniPackOpenENT>

	@Query("SELECT * FROM UniPackOpenENT WHERE path=:path ORDER BY created_at DESC LIMIT 1")
	fun getLastOpenedDateSync(path: String?): UniPackOpenENT?
}