package com.kimjisub.launchpad.adapter

import com.kimjisub.design.PackView
import com.kimjisub.launchpad.network.fb.StoreVO

class StoreItem(
		var storeVO: StoreVO,
		var isDownloaded: Boolean = false
) {
	var isDownloading: Boolean = false


	var packView: PackView? = null
	var isToggle = false

}
