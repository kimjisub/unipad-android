package com.kimjisub.launchpad.tool

object BindUtil {

	fun <T> valueOrDefault(value: T?, defaultValue: T): T {
		return value ?: defaultValue
	}
}