package com.kimjisub.launchpad.db.converter

import androidx.room.TypeConverter
import java.util.*

class DateConverter {
	@TypeConverter
	fun toDate(dateLong: Long?): Date? {
		return if (dateLong == null) null else Date(dateLong)
	}

	@TypeConverter
	fun fromDate(date: Date?): Long? {
		return date?.time
	}
}