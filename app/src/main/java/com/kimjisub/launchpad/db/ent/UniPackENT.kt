package com.kimjisub.launchpad.db.ent

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.kimjisub.launchpad.db.util.DateConverter

@Entity
@TypeConverters(DateConverter::class)
class UniPackENT(
	@PrimaryKey
	@ColumnInfo(name = "path")
	var id: String,
	var padTouch: Int,
	var bookmark: Boolean,
) : BaseEntity() {

	override fun toString(): String =
		"UniPackENT(id='$id', padTouch=$padTouch, bookmark=$bookmark, created_at=$created_at)"

	override fun equals(other: Any?): Boolean {
		if (this === other) return true
		if (javaClass != other?.javaClass) return false

		other as UniPackENT

		if (id != other.id) return false

		return true
	}

	fun clone(): UniPackENT {
		return UniPackENT(id, padTouch, bookmark)
	}

}