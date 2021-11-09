package com.kimjisub.launchpad.db.ent

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.kimjisub.launchpad.db.util.DateConverter
import java.util.*

@TypeConverters(DateConverter::class)
@Entity
class UniPackOpenENT(
	var path: String,
	var createdAt: Date,
) {
	@PrimaryKey(autoGenerate = true)
	var id: Long = 0

	override fun toString(): String = "UniPackOpenENT(id=$id, path='$path', createdAt=$createdAt)"
}