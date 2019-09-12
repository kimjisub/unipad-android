package com.kimjisub.launchpad.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.kimjisub.launchpad.R.*
import com.kimjisub.launchpad.databinding.ItemThemeBinding
import java.util.*

class ThemeAdapter(val context: Context, val list: ArrayList<ThemeItem>)
	: RecyclerView.Adapter<ThemeHolder>() {

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ThemeHolder {
		val view = LayoutInflater.from(parent.context)
				.inflate(layout.item_theme, parent, false)

		return ThemeHolder(ItemThemeBinding.bind(view))
	}

	override fun onBindViewHolder(holder: ThemeHolder, position: Int) {

		holder.apply {
			if (position < list.size)
				binding.data = list[position]
			else {
				icon.background = ContextCompat.getDrawable(context, drawable.theme_add)
				version.text = context.getString(string.themeDownload)
			}
		}
	}

	override fun getItemCount(): Int = list.size + 1
}


