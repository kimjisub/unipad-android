package com.kimjisub.launchpad.unipack.struct

import java.util.*

class LedAnimation(
	val elements: ArrayList<Element>,
	val loop: Int,
	val num: Int
){
	interface Element{
		class On(
			val x:Int,
			val y:Int,
			val color:Int = -1,
			val velo:Int = 4
		) : Element

		class Off(
			val x:Int,
			val y:Int
		) : Element

		class Delay(
			val delay:Int
		) : Element
	}
}