package com.kimjisub.launchpad.tool

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer

open class Event<T>(value: T) {

	var value = value
		private set

	private var isAlreadyHandled = false

	fun isActive(): Boolean = if (isAlreadyHandled) {
		false
	} else {
		isAlreadyHandled = true
		true
	}
}

fun <T> LiveData<Event<T>>.observeEvent(owner: LifecycleOwner, observer: Observer<T>) = observe(owner) {
	if (it.isActive()) {
		observer.onChanged(it.value)
	}
}

fun MutableLiveData<Event<Unit>>.emit() = postValue(Event(Unit))

fun <T> MutableLiveData<Event<T>>.emit(value: T) = postValue(Event(value))