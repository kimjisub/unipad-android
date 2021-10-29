package com.kimjisub.launchpad.activity.settings

import androidx.preference.PreferenceFragmentCompat

data class CategoryItemData(
	val title: String,
	val icon: Int,
	val FragmentCreator: () -> PreferenceFragmentCompat
)