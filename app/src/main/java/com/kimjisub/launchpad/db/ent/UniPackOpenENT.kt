package com.kimjisub.launchpad.db.ent

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.kimjisub.launchpad.db.util.DateConverter

@Entity
@TypeConverters(DateConverter::class)
class UniPackOpenENT(
	var path: String,
) : BaseEntity() {
	@PrimaryKey(autoGenerate = true)
	var id: Long = 0

	override fun toString(): String = "UniPackOpenENT(id=$id, path='$path', created_at=$created_at)"
}