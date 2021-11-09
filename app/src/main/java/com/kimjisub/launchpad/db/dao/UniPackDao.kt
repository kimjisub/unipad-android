package com.kimjisub.launchpad.db.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.kimjisub.launchpad.db.ent.UniPackENT

@Dao
interface UniPackDao {

	@Query("SELECT * FROM UniPackENT WHERE path=:id LIMIT 1")
	fun find(id: String): LiveData<UniPackENT>

	@Insert(onConflict = OnConflictStrategy.IGNORE)
	fun insert(item: UniPackENT)

	@Update
	fun update(item: UniPackENT?)

	fun getOrCreate(id: String): LiveData<UniPackENT> {
		insert(UniPackENT(id, 0, false))
		return find(id)
	}
}