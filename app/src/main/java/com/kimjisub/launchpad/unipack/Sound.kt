package com.kimjisub.launchpad.unipack

import java.io.File

data class Sound(
	val file: File,
	val loop: Int,
	val wormhole: Int = -1,
	var num: Int = 0,
	var id: Int = -1
)