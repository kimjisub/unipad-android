package com.kimjisub.launchpad.fragment

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import com.kimjisub.launchpad.BuildConfig


class MainTotalPanelViewModel(application: Application) : AndroidViewModel(application) {
	var count = MutableLiveData<Int>()
	var version = MutableLiveData<String>()
	var premium = MutableLiveData<Boolean>()

	var unipackCount = MutableLiveData<String>()
	var unipackCapacity = MutableLiveData<String>()
	var openCount = MutableLiveData<String>()
	var themeList = MutableLiveData<ArrayList<String>>()
	var selectedTheme = MutableLiveData<Int>()

	init {
		count.value = 0
		version.value = BuildConfig.VERSION_NAME
	}

	fun increase() {
		count.value = count.value?.plus(1)
	}

	fun decrease() {
		count.value = count.value?.minus(1)
	}

}