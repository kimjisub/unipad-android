package com.kimjisub.launchpad.db.ent

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.kimjisub.launchpad.db.util.DateConverter
import java.util.*

@Entity
@TypeConverters(DateConverter::class)
class UnipackENT(
	@PrimaryKey
	var path: String,
	var padTouch: Int,
	var bookmark: Boolean,
	var created_at: Date
) {

	override fun toString(): String = "UnipackENT(path='$path', padTouch=$padTouch, bookmark=$bookmark, created_at=$created_at)"

	override fun equals(other: Any?): Boolean {
		if (this === other) return true
		if (javaClass != other?.javaClass) return false

		other as UnipackENT

		if (path != other.path) return false
		if (padTouch != other.padTouch) return false
		if (bookmark != other.bookmark) return false
		if (created_at != other.created_at) return false

		return true
	}

	fun clone(): UnipackENT {
		return UnipackENT(path, padTouch, bookmark, created_at)
	}

}