package com.kimjisub.launchpad.adapter

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.kimjisub.launchpad.R.layout
import com.kimjisub.launchpad.databinding.ItemThemeBinding
import com.kimjisub.launchpad.manager.ThemeResources
import java.util.*

class ThemeItem(context: Context, val package_name: String) {
	val res: Resources = context.packageManager.getResourcesForApplication(package_name)

	val icon: Drawable = res.getDrawable(res.getIdentifier("$package_name:drawable/theme_ic", null, null))
	val name: String = res.getString(res.getIdentifier("$package_name:string/theme_name", null, null))
	val author: String = res.getString(res.getIdentifier("$package_name:string/theme_author", null, null))
	val description: String = res.getString(res.getIdentifier("$package_name:string/theme_description", null, null))
	val version: String? = context.packageManager.getPackageInfo(package_name, 0).versionName

	val resources: ThemeResources? = null
}

class ThemeHolder(val binding: ItemThemeBinding) : RecyclerView.ViewHolder(binding.root)


class ThemeAdapter(val list: ArrayList<ThemeItem>) : RecyclerView.Adapter<ThemeHolder>() {

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
				binding.data = null
			}
		}
	}

	override fun getItemCount(): Int = list.size + 1
}

object ThemeTool{
	fun getThemePackList(context: Context): ArrayList<ThemeItem> {
		val ret = ArrayList<ThemeItem>()
		try {
			ret.add(ThemeItem(context, context.packageName))
		} catch (e: Exception) {
			e.printStackTrace()
		}
		val packages: List<ApplicationInfo> = context.packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
		for (applicationInfo in packages) {
			val packageName: String = applicationInfo.packageName
			if (packageName.startsWith("com.kimjisub.launchpad.theme.")) {
				try {
					ret.add(ThemeItem(context, packageName))
				} catch (e: Exception) {
					e.printStackTrace()
				}
			}
		}
		return ret
	}
}