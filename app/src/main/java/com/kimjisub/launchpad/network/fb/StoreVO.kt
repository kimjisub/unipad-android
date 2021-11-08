package com.kimjisub.launchpad.network.fb

import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties
data class StoreVO(
	var code: String? = null,
	var title: String? = null,
	var producerName: String? = null,
	@JvmField
	var isAutoPlay: Boolean = false,
	@JvmField
	var isLED: Boolean = false,
	var downloadCount: Int = 0,
	var URL: String? = null,
)