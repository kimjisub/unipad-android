package com.kimjisub.launchpad.network.fb

import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties
data class StoreVO(
		var code: String? = null,
		var title: String? = null,
		var producerName: String? = null,
		var isAutoPlay: Boolean = false,
		var isLED: Boolean = false,
		var downloadCount: Int = 0,
		var URL: String? = null
)