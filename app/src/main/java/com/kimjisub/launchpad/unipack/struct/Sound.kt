package com.kimjisub.launchpad.unipack.struct

import java.io.File

data class Sound(
	val file: File,
	val loop: Int,
	val wormhole: Int = NO_WORMHOLE,
	var num: Int = 0,
	@Volatile var id: Int = NOT_LOADED,
) {
	companion object {
		const val NO_WORMHOLE = -1

		/** [id] of a sound not handed to the audio engine yet. */
		const val NOT_LOADED = -1

		/** [id] of a sound whose file could not be decoded or loaded; its turn plays nothing. */
		const val UNDECODABLE = -2
	}
}