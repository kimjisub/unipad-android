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
			val x: Long,
			val y: Long,
			val color: Int = -1,
			val velocity: Int = DEFAULT_VELOCITY,
		) : LedEvent {
			constructor(x: Int, y: Int, color: Int = -1, velocity: Int = DEFAULT_VELOCITY) :
				this(x.toLong(), y.toLong(), color, velocity)
		}

		class Off(
			val x: Long,
			val y: Long,
		) : LedEvent {
			constructor(x: Int, y: Int) : this(x.toLong(), y.toLong())
		}

		class Delay(
			val delay: Int,
		) : LedEvent

		class Chain(
			val chain: Long,
		) : LedEvent {
			constructor(chain: Int) : this(chain.toLong())
		}
	}
}