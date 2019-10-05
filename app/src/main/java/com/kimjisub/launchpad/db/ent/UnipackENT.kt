package com.kimjisub.launchpad.db.ent

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.kimjisub.launchpad.db.converter.DateConverter
import java.util.*

@Entity
@TypeConverters(DateConverter::class)
class UnipackENT(
	@PrimaryKey var path: String,
	var padTouch: Int,
	var bookmark: Boolean,
	var created_at: Date
) {

	override fun toString(): String = "UnipackENT(path='$path', padTouch=$padTouch, bookmark=$bookmark, created_at=$created_at)"
}