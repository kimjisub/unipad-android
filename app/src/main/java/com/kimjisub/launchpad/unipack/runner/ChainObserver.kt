package com.kimjisub.launchpad.unipack.runner

import java.util.concurrent.CopyOnWriteArrayList

/**
 * Current chain plus its observers. `value` is written from several threads (main, the LED
 * runner via its listener, the wormhole delay in SoundRunner), so the field is volatile and
 * the observer list copy-on-write; observers themselves run on the thread that sets `value`.
 */
class ChainObserver {
	var range: IntRange = Int.MIN_VALUE..Int.MAX_VALUE

	@Volatile
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

	private val observerList = CopyOnWriteArrayList<(curr: Int, prev: Int) -> Unit>()

	fun refresh(curr: Int = value, prev: Int = value) {
		for (observer in observerList)
			observer.invoke(curr, prev)
	}

	fun addObserver(observer: (curr: Int, prev: Int) -> Unit) = observerList.add(observer)

	fun removeObserver(observer: (curr: Int, prev: Int) -> Unit) = observerList.remove(observer)

	fun clearObserver() = observerList.clear()
}
