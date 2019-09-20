package com.kimjisub.launchpad.unipack

interface AutoPlay{
	class On(
		val x: Int,
		val y: Int,
		val currChain: Int,
		val num: Int
	) : AutoPlay

	class Off(
		val x: Int,
		val y: Int,
		val currChain: Int
	) : AutoPlay

	class Chain(
		val c: Int
	) : AutoPlay

	class Delay(
		val d: Int,
		val currChain: Int
	) : AutoPlay
}