package com.kimjisub.design.extra

import androidx.databinding.Observable
import java.util.*


fun <T : Observable> T.addOnPropertyChanged(callback: (T) -> Unit) =
	addOnPropertyChangedCallback(
		object : Observable.OnPropertyChangedCallback() {
			override fun onPropertyChanged(observable: Observable?, i: Int) =
				callback(observable as T)
		})

/**
 * Be sure the array is sort by the comparator you provided.
 * */
fun <T> ArrayList<T>.getVirtualIndexFormSorted(comparator: Comparator<T>, target: T): Int {
	var index = 0
	for ((i, item: T) in this.withIndex()) {
		if (comparator.compare(target, item) < 0) {
			index = i
			break
		}
	}
	return index
}