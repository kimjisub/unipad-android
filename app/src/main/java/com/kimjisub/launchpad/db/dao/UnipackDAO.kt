package com.kimjisub.launchpad.db.dao

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.Observer
import androidx.room.*
import com.kimjisub.launchpad.db.ent.UnipackENT
import com.kimjisub.manager.Log
import java.util.*

@Dao
abstract class UnipackDAO {

	/*@Query("SELECT * FROM UnipackENT")
	public abstract List<UnipackENT> getAll();*/

	@Query("SELECT * FROM UnipackENT WHERE path=:path LIMIT 1")
	abstract fun find(path: String): LiveData<UnipackENT>

	/*fun findasdf(id: String):
			LiveData<UnipackENT?> = find_(id).getDistinct()*/

	@Insert(onConflict = OnConflictStrategy.IGNORE)
	abstract fun insert(item: UnipackENT)

	@Update
	abstract fun update(item: UnipackENT?)

	fun getOrCreate(path: String): LiveData<UnipackENT> {
		insert(UnipackENT(path, 0, false, Date()))
		return find(path)
	}
}