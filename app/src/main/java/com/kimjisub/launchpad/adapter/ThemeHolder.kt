package com.kimjisub.launchpad.adapter

import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.kimjisub.launchpad.R.id
import com.kimjisub.launchpad.databinding.ItemThemeBinding

class ThemeHolder(val binding: ItemThemeBinding) : RecyclerView.ViewHolder(binding.root) {
	val icon: ImageView = itemView.findViewById(id.theme_icon)
	val version: TextView = itemView.findViewById(id.theme_version)
	val author: TextView = itemView.findViewById(id.theme_author)
}