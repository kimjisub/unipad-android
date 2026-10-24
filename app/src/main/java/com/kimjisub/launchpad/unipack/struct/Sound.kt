package com.kimjisub.launchpad.unipack.struct

import java.io.File

data class Sound(
	val file: File,
	val loop: Long,
	val wormhole: Int = NO_WORMHOLE,
	var num: Int = 0,
	@Volatile var id: Int = NOT_LOADED,
) {
	constructor(file: File, loop: Int, wormhole: Int = NO_WORMHOLE, num: Int = 0, id: Int = NOT_LOADED) :
		this(file, loop.toLong(), wormhole, num, id)

	companion object {
		const val NO_WORMHOLE = -1

		/** [id] of a sound not handed to the audio engine yet. */
		const val NOT_LOADED = -1

		/** [id] of a sound whose file could not be decoded or loaded; its turn plays nothing. */
		const val UNDECODABLE = -2
	}
}