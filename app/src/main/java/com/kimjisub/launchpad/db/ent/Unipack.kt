package com.kimjisub.launchpad.db.ent

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity
data class Unipack(
	@PrimaryKey
	val id: String,
	val bookmark: Boolean,
	val openCount: Long,
	val lastOpenedAt: Date?,
	val createdAt: Date,
) {
	companion object {
		fun create(id: String) = Unipack(id, false, 0, null, Date())
	}
}