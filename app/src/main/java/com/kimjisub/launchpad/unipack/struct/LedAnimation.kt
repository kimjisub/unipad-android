package com.kimjisub.launchpad.unipack.struct

import java.util.*

class LedAnimation(
	val ledEvents: ArrayList<LedEvent>,
	val loop: Int,
	val num: Int
) {
	interface LedEvent {
		class On(
			val x: Int,
			val y: Int,
			val color: Int = -1,
			val velocity: Int = 4
		) : LedEvent

		class Off(
			val x: Int,
			val y: Int
		) : LedEvent

		class Delay(
			val delay: Int
		) : LedEvent
	}
}