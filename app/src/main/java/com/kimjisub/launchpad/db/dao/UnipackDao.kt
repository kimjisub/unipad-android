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
	fun totalOpenCount(): LiveData<Long>

	@Query("SELECT MAX(lastOpenedAt) FROM Unipack")
	fun lastOpenedAt(): LiveData<Date>

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

	@Query("SELECT openCount FROM Unipack WHERE id=:id")
	fun openCountSync(id: String): Long

	@Query("SELECT lastOpenedAt FROM Unipack WHERE id=:id")
	fun lastOpenedAt(id: String): LiveData<Date>
}
