package com.kimjisub.launchpad.db.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kimjisub.launchpad.db.ent.Unipack
import java.util.*

@Dao
interface UnipackDao {
	// Get

	@Query("SELECT * FROM Unipack WHERE id=:id")
	fun find(id: String): LiveData<Unipack>

	@Query("SELECT SUM(openCount) FROM Unipack")
	fun openCount(): LiveData<Long>

	@Query("SELECT MAX(lastOpenedAt) FROM Unipack")
	fun lastOpenedAt(): LiveData<Date>

	// Update

	@Insert(onConflict = OnConflictStrategy.IGNORE)
	suspend fun insert(item: Unipack)

	@Query("UPDATE Unipack SET openCount = openCount + 1 WHERE id=:id")
	suspend fun addOpenCount(id: String)

	@Query("UPDATE Unipack SET lastOpenedAt=:lastOpenedAt WHERE id=:id")
	suspend fun setLastOpenedAt(id: String, lastOpenedAt: Date)

	@Query("UPDATE Unipack SET bookmark=NOT(bookmark) WHERE id=:id")
	suspend fun toggleBookmark(id: String)

	suspend fun getOrCreate(id: String): LiveData<Unipack> {
		insert(Unipack.create(id))
		return find(id)
	}


	// todo find 로 대체

	@Query("SELECT openCount FROM Unipack WHERE id=:id")
	fun openCount(id: String): Long

	@Query("SELECT lastOpenedAt FROM Unipack WHERE id=:id")
	fun lastOpenedAt(id: String): Date?
}