package com.kimjisub.launchpad.unipack.runner

import java.util.concurrent.CopyOnWriteArrayList

/**
 * Current chain plus its observers. `value` is written from several threads (main, the LED
 * runner via its listener, the wormhole delay in SoundRunner), so the field is volatile and
 * the observer list copy-on-write; observers themselves run on the thread that sets `value`.
 * Selection observers hear assignments, including a reselected chain, but not a light refresh.
 */
class ChainObserver {
	var range: IntRange = Int.MIN_VALUE..Int.MAX_VALUE

	@Volatile
	private var currentValue = 0
	var value: Int
		get() = currentValue
		set(value) = assign(value, selected = true)

	/** A pack command moves the chain without acting as a person's later selection. */
	fun assignFromPack(value: Int) = assign(value, selected = false)

	private fun assign(value: Int, selected: Boolean) {
		val realValue = when {
			range.first > value -> range.first
			range.last < value -> range.last
			else -> value
		}
		val prev = currentValue
		currentValue = realValue
		if (selected) for (observer in selectionObserverList) observer.invoke(realValue, prev)
		refresh(realValue, prev)
	}

	private val observerList = CopyOnWriteArrayList<(curr: Int, prev: Int) -> Unit>()
	private val selectionObserverList = CopyOnWriteArrayList<(curr: Int, prev: Int) -> Unit>()

	fun refresh(curr: Int = value, prev: Int = value) {
		for (observer in observerList)
			observer.invoke(curr, prev)
	}

	fun addObserver(observer: (curr: Int, prev: Int) -> Unit) = observerList.add(observer)

	fun removeObserver(observer: (curr: Int, prev: Int) -> Unit) = observerList.remove(observer)

	fun addSelectionObserver(observer: (curr: Int, prev: Int) -> Unit) = selectionObserverList.add(observer)

	fun removeSelectionObserver(observer: (curr: Int, prev: Int) -> Unit) = selectionObserverList.remove(observer)

	fun clearObserver() {
		observerList.clear()
		selectionObserverList.clear()
	}
}
