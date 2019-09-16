package com.kimjisub.launchpad.db.dao

import androidx.room.*
import com.kimjisub.launchpad.db.ent.UnipackENT
import java.util.*

@Dao
abstract class UnipackDAO {

	/*@Query("SELECT * FROM UnipackENT")
	public abstract List<UnipackENT> getAll();*/

	@Query("SELECT * FROM UnipackENT WHERE path=:path LIMIT 1")
	abstract fun find(path: String): UnipackENT?

	@Insert(onConflict = OnConflictStrategy.IGNORE)
	abstract fun insert(item: UnipackENT)

	@Update
	abstract fun update(item: UnipackENT?)

	fun getOrCreate(path: String): UnipackENT {
		var item = find(path)
		if (item == null) {
			item = UnipackENT(path, 0, false, false, Date())
			insert(item)
		}
		return item
	}
}