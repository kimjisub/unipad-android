package com.kimjisub.launchpad.unipack.struct

class AutoPlay(
	val elements: ArrayList<Element>,
) {
	sealed interface Element {
		class On(
			val x: Int,
			val y: Int,
			val currChain: Int,
			val num: Int,
		) : Element

		class Off(
			val x: Int,
			val y: Int,
			val currChain: Int,
		) : Element

		/** [scheduled]: a sound's wormhole applied at its song time, not a `chain` line of the file. */
		class Chain(
			val c: Int,
			val scheduled: Boolean = false,
		) : Element

		class Delay(
			var delay: Int,
		) : Element
	}
}