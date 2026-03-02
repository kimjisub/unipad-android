package com.kimjisub.launchpad.db.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kimjisub.launchpad.db.ent.Unipack
import java.util.Date

@Dao
interface UnipackDao {
	// Get

	@Query("SELECT * FROM Unipack WHERE id=:id")
	fun find(id: String): LiveData<Unipack>

	@Query("SELECT COALESCE(SUM(openCount), 0) FROM Unipack")
	fun totalOpenCount(): LiveData<Long>

	// Update

	@Insert(onConflict = OnConflictStrategy.IGNORE)
	fun insert(item: Unipack): Long

	@Query("UPDATE Unipack SET openCount = openCount + 1 WHERE id=:id")
	fun addOpenCount(id: String): Int

	@Query("UPDATE Unipack SET lastOpenedAt=:lastOpenedAt WHERE id=:id")
	fun setLastOpenedAt(id: String, lastOpenedAt: Date): Int

	@Query("UPDATE Unipack SET bookmark=NOT(bookmark) WHERE id=:id")
	fun toggleBookmark(id: String): Int

	@Query("SELECT openCount FROM Unipack WHERE id=:id")
	fun openCount(id: String): LiveData<Long>

	@Query("SELECT EXISTS(SELECT 1 FROM Unipack WHERE id = :id)")
	fun exists(id: String): Boolean

	@Query("SELECT lastOpenedAt FROM Unipack WHERE id=:id")
	fun lastOpenedAt(id: String): LiveData<Date>
}
