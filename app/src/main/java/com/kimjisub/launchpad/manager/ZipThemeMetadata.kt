package com.kimjisub.launchpad.manager

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ZipThemeMetadata(
	val name: String,
	val author: String,
	val version: String = "1.0",
)

@Serializable
data class ZipThemeColors(
	val checkbox: String? = null,
	@SerialName("trace_log")
	val traceLog: String? = null,
	@SerialName("option_window")
	val optionWindow: String? = null,
	@SerialName("option_window_checkbox")
	val optionWindowCheckbox: String? = null,
)
