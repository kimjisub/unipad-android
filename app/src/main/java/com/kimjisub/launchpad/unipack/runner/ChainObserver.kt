package com.kimjisub.launchpad.unipack.runner

class ChainObserver {
	var range: IntRange = Integer.MIN_VALUE..Integer.MAX_VALUE
	var value: Int = 0
		set(value) {
			val realValue =
				if (range.contains(value)) value
				else
					when {
						range.first > value -> range.first
						range.last < value -> range.last
						else -> throw ArrayIndexOutOfBoundsException("WTF")
					}

			val prev = field
			field = realValue
			for (observer in observerList)
				observer.invoke(field, prev)
		}

	private val observerList: ArrayList<(curr: Int, prev: Int) -> Unit> = ArrayList()

	fun addObserver(observer: (curr: Int, prev: Int) -> Unit) = observerList.add(observer)

	fun removeObserver(observer: (curr: Int, prev: Int) -> Unit) = observerList.remove(observer)

	fun clearObserver() = observerList.clear()
}