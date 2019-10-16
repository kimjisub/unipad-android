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

interface ObserverPrev<T> {
	var observer:Observer<T>?

	fun onChanged(curr: T?, prev: T?)
}

fun <T> LiveData<T>.observePrev(owner: LifecycleOwner, observerPrev: ObserverPrev<T>) {
	var prev : T? = null
	observerPrev.observer = Observer {
		observerPrev.onChanged(it, prev)
		prev = it
	}
	observe(owner, observerPrev.observer!!)
}

fun <T> LiveData<T>.removeObserverPrev(observerPrev: ObserverPrev<T>) {
	removeObserver(observerPrev.observer!!)
}