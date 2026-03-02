package com.kimjisub.launchpad.unipack.struct

import java.io.File

data class Sound(
	val file: File,
	val loop: Int,
	val wormhole: Int = NO_WORMHOLE,
	var num: Int = 0,
	var id: Int = -1,
) {
	companion object {
		const val NO_WORMHOLE = -1
	}
}