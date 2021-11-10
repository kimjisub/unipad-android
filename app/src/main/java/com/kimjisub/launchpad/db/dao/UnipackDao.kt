package com.kimjisub.launchpad.db.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.kimjisub.launchpad.db.ent.Unipack
import java.util.*

@Dao
interface UnipackDao {

	@Query("SELECT * FROM Unipack WHERE id=:id")
	fun find(id: String): LiveData<Unipack>

	@Insert(onConflict = OnConflictStrategy.IGNORE)
	suspend fun insert(item: Unipack)

	@Update
	suspend fun update(item: Unipack?)

	@Query("UPDATE Unipack SET openCount = openCount + 1 WHERE id=:id")
	suspend fun addOpenCount(id: String)

	@Query("SELECT SUM(openCount) FROM Unipack")
	fun openCount(): LiveData<Long>

	@Query("SELECT MAX(lastOpenedAt) FROM Unipack")
	fun lastOpenedAt(): LiveData<Date>

	suspend fun getOrCreate(id: String): LiveData<Unipack> {
		insert(Unipack.create(id))
		return find(id)
	}


	// todo find 로 대체

	@Query("SELECT openCount FROM Unipack WHERE id=:id")
	fun openCount(id: String): LiveData<Long>

	@Query("SELECT lastOpenedAt FROM Unipack WHERE id=:id")
	fun lastOpenedAt(id: String): LiveData<Date>
}