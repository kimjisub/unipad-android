package com.kimjisub.launchpad.activity

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import com.azoft.carousellayoutmanager.CarouselLayoutManager
import com.azoft.carousellayoutmanager.CarouselZoomPostLayoutListener
import com.azoft.carousellayoutmanager.CenterScrollListener
import com.kimjisub.launchpad.R.layout
import com.kimjisub.launchpad.adapter.ThemeAdapter
import com.kimjisub.launchpad.adapter.ThemeItem
import com.kimjisub.launchpad.manager.PreferenceManager.SelectedTheme
import com.kimjisub.manager.Log
import kotlinx.android.synthetic.main.activity_theme.*
import java.util.*

class ThemeActivity : BaseActivity() {
	val themeList: ArrayList<ThemeItem> by lazy { getThemePackList(applicationContext) }

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(layout.activity_theme)
	}

	override fun onResume() {
		super.onResume()
		val manager = CarouselLayoutManager(CarouselLayoutManager.HORIZONTAL, false).apply {
			setPostLayoutListener(CarouselZoomPostLayoutListener())
		}

		RV_list.apply {
			layoutManager = manager
			adapter = ThemeAdapter(this@ThemeActivity, themeList)

			setHasFixedSize(true)
			addOnScrollListener(CenterScrollListener())
		}

		manager.scrollToPosition(getSavedTheme())

		TV_apply.setOnClickListener {
			selectTheme(manager.centerItemPosition)
			finish()
		}
	}

	private fun selectTheme(i: Int) {
		if (themeList.size != i)
			SelectedTheme.save(this@ThemeActivity, themeList[i].package_name)
		else
			startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/search?q=com.kimjisub.launchpad.theme.")))
	}

	private fun getSavedTheme(): Int {
		var ret = 0
		val selectedThemePackageName: String = SelectedTheme.load(this@ThemeActivity)
		var i = 0
		for (themeItem in themeList) {
			Log.log(selectedThemePackageName + ", " + themeItem.package_name)
			if (themeItem.package_name == selectedThemePackageName) {
				ret = i
				break
			}
			i++
		}
		return ret
	}

	private fun getThemePackList(context: Context): ArrayList<ThemeItem> {
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