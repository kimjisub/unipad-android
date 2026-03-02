package com.kimjisub.launchpad.unipack.runner

class ChainObserver {
	var range: IntRange = Int.MIN_VALUE..Int.MAX_VALUE
	var value: Int = 0
		set(value) {
			val realValue =
				when {
					range.first > value -> range.first
					range.last < value -> range.last
					else -> value
				}

			val prev = field
			field = realValue
			refresh(field, prev)
		}

	private val observerList: MutableList<(curr: Int, prev: Int) -> Unit> = mutableListOf()

	fun refresh(curr: Int = value, prev: Int = value) {
		for (observer in observerList)
			observer.invoke(curr, prev)
	}

	fun addObserver(observer: (curr: Int, prev: Int) -> Unit) = observerList.add(observer)

	fun removeObserver(observer: (curr: Int, prev: Int) -> Unit) = observerList.remove(observer)

	fun clearObserver() = observerList.clear()
}