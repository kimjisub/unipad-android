package com.kimjisub.launchpad.db.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.kimjisub.launchpad.db.ent.UniPackENT
import java.util.*

@Dao
abstract class UniPackDAO {

	/*@Query("SELECT * FROM UniPackENT")
	public abstract List<UniPackENT> getAll();*/

	@Query("SELECT * FROM UniPackENT WHERE path=:path LIMIT 1")
	abstract fun find(path: String): LiveData<UniPackENT>

	/*fun findasdf(id: String):
			LiveData<UniPackENT?> = find_(id).getDistinct()*/

	@Insert(onConflict = OnConflictStrategy.IGNORE)
	abstract fun insert(item: UniPackENT)

	@Update
	abstract fun update(item: UniPackENT?)

	fun getOrCreate(path: String): LiveData<UniPackENT> {
		insert(UniPackENT(path, 0, false, Date()))
		return find(path)
	}
}