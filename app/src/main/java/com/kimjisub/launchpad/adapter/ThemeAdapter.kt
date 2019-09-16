package com.kimjisub.launchpad.adapter

import android.content.Context
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.kimjisub.launchpad.R.*
import com.kimjisub.launchpad.databinding.ItemThemeBinding
import com.kimjisub.launchpad.manager.ThemeResources
import java.util.*

class ThemeItem(val context: Context, val package_name: String) {
	val res: Resources = context.packageManager.getResourcesForApplication(package_name)

	val icon: Drawable = res.getDrawable(res.getIdentifier("$package_name:drawable/theme_ic", null, null))
	val name: String = res.getString(res.getIdentifier("$package_name:string/theme_name", null, null))
	val author: String = res.getString(res.getIdentifier("$package_name:string/theme_author", null, null))
	val description: String = res.getString(res.getIdentifier("$package_name:string/theme_description", null, null))
	val version: String? = context.packageManager.getPackageInfo(package_name, 0).versionName

	val resources: ThemeResources? = null
}

class ThemeHolder(val binding: ItemThemeBinding)
	: RecyclerView.ViewHolder(binding.root)


class ThemeAdapter(val list: ArrayList<ThemeItem>)
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
				val context = holder.binding.root.context
				binding.icon.background = ContextCompat.getDrawable(context, drawable.theme_add)
				binding.version.text = context.getString(string.themeDownload)
			}
		}
	}

	override fun getItemCount(): Int = list.size + 1
}

