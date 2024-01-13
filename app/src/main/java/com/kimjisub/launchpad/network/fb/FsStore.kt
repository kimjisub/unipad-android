package com.kimjisub.launchpad.network.fb

import com.google.firebase.database.IgnoreExtraProperties
import java.util.Date

@IgnoreExtraProperties
class FsStore {
	var chainCount: Long = 0
	var code: String? = null
	var description: String? = null
	var difficulty: Long = 0
	var downloadCount: Long = 0

	@JvmField
	var isAutoPlay = false

	@JvmField
	var isLed = false

	@JvmField
	var isNew = false

	@JvmField
	var isProLight = false

	@JvmField
	var isWormhole = false
	var playTime: Long = 0
	var producerName: String? = null
	var rank: Long = 0
	var title: String? = null
	var uploadAt: Date? = null
	var url: String? = null
	var website: String? = null
}