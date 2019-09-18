package com.kimjisub.launchpad.api.unipad.vo

data class UnishareVO(
		var _id: String? = null,
		var title: String? = null,
		var producer: String? = null,
		var content: String? = null,
		var website: String? = null,
		var youtube: String? = null,
		var fileSize: Long = 0,
		var isPublic: Boolean = false,//todo remove is
		var password: String? = null,
		var downloadCount: Int = 0
)