package com.kimjisub.launchpad.db.util

import androidx.room.TypeConverter
import java.util.Date

// https://developer.android.com/training/data-storage/room/referencing-data?hl=ko

class DateConverter {
	@TypeConverter
	fun fromTimestamp(value: Long?): Date? {
		return value?.let { Date(it) }
	}

	@TypeConverter
	fun dateToTimestamp(date: Date?): Long? {
		return date?.time?.toLong()
	}
}