package com.kimjisub.launchpad.db.ent

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.*

@Entity
class Unipack(
	@PrimaryKey
	var id: String,
	var bookmark: Boolean,
	var openCount: Long,
	var lastOpenedAt: Date?,
	var createdAt: Date,
) {
	fun clone(): Unipack {
		return Unipack(
			id,
			bookmark,
			openCount,
			lastOpenedAt,
			createdAt,
		)
	}

	override fun toString(): String {
		return "Unipack(id='$id', bookmark=$bookmark, openCount=$openCount, lastOpenedAt=$lastOpenedAt, createdAt=$createdAt)"
	}

	override fun equals(other: Any?): Boolean {
		if (this === other) return true
		if (javaClass != other?.javaClass) return false

		other as Unipack

		if (id != other.id) return false
		if (bookmark != other.bookmark) return false
		if (openCount != other.openCount) return false
		if (lastOpenedAt != other.lastOpenedAt) return false
		if (createdAt != other.createdAt) return false

		return true
	}

	override fun hashCode(): Int {
		var result = id.hashCode()
		result = 31 * result + bookmark.hashCode()
		result = 31 * result + openCount.hashCode()
		result = 31 * result + lastOpenedAt.hashCode()
		result = 31 * result + createdAt.hashCode()
		return result
	}

	companion object {
		fun create(id: String) = Unipack(id, false, 0, null, Date())
	}
}