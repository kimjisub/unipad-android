package com.kimjisub.launchpad.unipack

import java.util.*

class Led(
	val syntaxList: ArrayList<Syntax>,
	val loop: Int,
	val num: Int
){
	interface Syntax{
		class On(
			val x:Int,
			val y:Int,
			val color:Int = -1,
			val velo:Int = 4
		) : Syntax

		class Off(
			val x:Int,
			val y:Int
		) : Syntax

		class Delay(
			val delay:Int
		) : Syntax
	}
}