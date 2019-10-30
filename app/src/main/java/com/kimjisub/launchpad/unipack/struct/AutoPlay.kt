package com.kimjisub.launchpad.unipack.struct

class AutoPlay(
	val elements: ArrayList<Element>
) {
	interface Element {
		class On(
			val x: Int,
			val y: Int,
			val currChain: Int,
			val num: Int
		) : Element

		class Off(
			val x: Int,
			val y: Int,
			val currChain: Int
		) : Element

		class Chain(
			val c: Int
		) : Element

		class Delay(
			var delay: Int,
			val currChain: Int
		) : Element
	}
}