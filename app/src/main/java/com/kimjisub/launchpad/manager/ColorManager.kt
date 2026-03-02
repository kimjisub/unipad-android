package com.kimjisub.launchpad.manager

import android.content.Context
import androidx.core.content.ContextCompat

class ColorManager(val context: Context) {
	fun get(id: Int) = ContextCompat.getColor(context, id)
}
