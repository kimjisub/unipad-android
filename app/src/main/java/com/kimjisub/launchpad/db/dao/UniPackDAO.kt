package com.kimjisub.launchpad.db.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.kimjisub.launchpad.db.ent.UniPackENT
import java.util.*

@Dao
abstract class UniPackDAO {

	@Query("SELECT * FROM UniPackENT WHERE path=:id LIMIT 1")
	abstract fun find(id: String): LiveData<UniPackENT>

	@Insert(onConflict = OnConflictStrategy.IGNORE)
	abstract fun insert(item: UniPackENT)

	@Update
	abstract fun update(item: UniPackENT?)

	fun getOrCreate(id: String): LiveData<UniPackENT> {
		insert(UniPackENT(id, 0, false, Date()))
		return find(id)
	}
}