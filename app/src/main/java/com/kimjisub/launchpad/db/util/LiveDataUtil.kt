package com.kimjisub.launchpad.db.util

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer

fun <T> LiveData<T>.observeOnce(observer: Observer<T>) {
	observeForever(object : Observer<T> {
		override fun onChanged(t: T?) {
			observer.onChanged(t)
			removeObserver(this)
		}
	})
}

fun <T> LiveData<T>.observeRealChange(
	owner: LifecycleOwner,
	realChangeObserver: Observer<T>,
	clone: (T) -> T,
): Observer<T> {
	val observer = object : Observer<T> {
		var prev: T? = null

		override fun onChanged(it: T) {
			if (prev != it) {
				realChangeObserver.onChanged(it)
				prev = clone(it)
			}
		}
	}
	observe(owner, observer)

	return observer
}
