package com.kimjisub.launchpad.view.preference

import android.content.Context
import android.util.AttributeSet
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.kimjisub.launchpad.R.layout
import com.kimjisub.launchpad.databinding.PreferenceIconsBinding

class IconsPreference
@JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet? = null,
	defStyleAttr: Int = 0,
) : Preference(context, attrs, defStyleAttr) {

	lateinit var b: PreferenceIconsBinding

	init {
		widgetLayoutResource = layout.preference_icons
	}

	override fun onBindViewHolder(holder: PreferenceViewHolder) {
		super.onBindViewHolder(holder)
		b = PreferenceIconsBinding.bind(holder.itemView)


	}
}