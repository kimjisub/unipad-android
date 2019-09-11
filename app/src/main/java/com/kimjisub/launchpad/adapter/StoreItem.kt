package com.kimjisub.launchpad.adapter

import com.kimjisub.design.PackViewSimple
import com.kimjisub.launchpad.network.fb.StoreVO

class StoreItem(
		var storeVO: StoreVO,
		var isDownloaded: Boolean = false
) {
	var isDownloading: Boolean = false


	var packViewSimple: PackViewSimple? = null
	var isToggle = false

}
