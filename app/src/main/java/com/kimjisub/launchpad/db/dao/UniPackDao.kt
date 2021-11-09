package com.kimjisub.launchpad.db.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.kimjisub.launchpad.db.ent.Unipack
import java.util.*

@Dao
interface UniPackDao {

	@Query("SELECT * FROM Unipack WHERE path=:id LIMIT 1")
	fun find(id: String): LiveData<Unipack>

	@Insert(onConflict = OnConflictStrategy.IGNORE)
	fun insert(item: Unipack)

	@Update
	fun update(item: Unipack?)

	fun getOrCreate(id: String): LiveData<Unipack> {
		insert(Unipack(id, 0, false, Date()))
		return find(id)
	}
}