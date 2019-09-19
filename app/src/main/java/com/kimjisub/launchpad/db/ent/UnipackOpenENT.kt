package com.kimjisub.launchpad.db.ent

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.kimjisub.launchpad.db.converter.DateConverter
import java.util.*

@Entity
@TypeConverters(DateConverter::class)
class UnipackOpenENT(
	var path: String,
	var created_at: Date
) {
	@PrimaryKey(autoGenerate = true)
	var id: Long = 0


	override fun toString(): String = "UnipackOpenENT(id=$id, path='$path', created_at=$created_at)"
}