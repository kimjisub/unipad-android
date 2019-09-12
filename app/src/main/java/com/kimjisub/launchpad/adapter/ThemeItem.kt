package com.kimjisub.launchpad.adapter

import android.content.Context
import android.content.res.Resources
import android.graphics.drawable.Drawable
import com.kimjisub.launchpad.manager.ThemeResources

class ThemeItem(val context: Context, val package_name: String) {
	val res: Resources = context.packageManager.getResourcesForApplication(package_name)

	val icon: Drawable = res.getDrawable(res.getIdentifier("$package_name:drawable/theme_ic", null, null))
	val name: String = res.getString(res.getIdentifier("$package_name:string/theme_name", null, null))
	val author: String = res.getString(res.getIdentifier("$package_name:string/theme_author", null, null))
	val description: String = res.getString(res.getIdentifier("$package_name:string/theme_description", null, null))
	val version: String? = context.packageManager.getPackageInfo(package_name, 0).versionName

	val resources: ThemeResources? = null
}