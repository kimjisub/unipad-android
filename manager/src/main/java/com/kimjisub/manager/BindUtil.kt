package com.kimjisub.manager

object BindUtil {

	fun <T> valueOrDefault(value: T?, defaultValue: T): T {
		return value ?: defaultValue
	}
}