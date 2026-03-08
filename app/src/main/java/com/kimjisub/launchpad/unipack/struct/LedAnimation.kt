package com.kimjisub.launchpad.unipack.struct

class LedAnimation(
	val ledEvents: ArrayList<LedEvent>,
	val loop: Int,
	val num: Int,
) {
	companion object {
		const val DEFAULT_VELOCITY = 4
	}

	sealed interface LedEvent {
		class On(
			val x: Int,
			val y: Int,
			val color: Int = -1,
			val velocity: Int = DEFAULT_VELOCITY,
		) : LedEvent

		class Off(
			val x: Int,
			val y: Int,
		) : LedEvent

		class Delay(
			val delay: Int,
		) : LedEvent

		class Chain(
			val chain: Int,
		) : LedEvent
	}
}