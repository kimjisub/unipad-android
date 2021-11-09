package com.kimjisub.launchpad.db.ent

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.kimjisub.launchpad.db.util.DateConverter
import java.util.*

@TypeConverters(DateConverter::class)
@Entity
class Unipack(
	@PrimaryKey
	@ColumnInfo(name = "path")
	var id: String,
	var padTouch: Int,
	var bookmark: Boolean,
	var createdAt: Date
) {

	override fun toString(): String =
		"UniPackENT(id='$id', padTouch=$padTouch, bookmark=$bookmark, createdAt=$createdAt)"

	override fun equals(other: Any?): Boolean {
		if (this === other) return true
		if (javaClass != other?.javaClass) return false

		other as Unipack

		if (id != other.id) return false

		return true
	}

	fun clone(): Unipack {
		return Unipack(id, padTouch, bookmark, createdAt)
	}

}