package com.kimjisub.launchpad.activity

import android.os.Bundle
import com.azoft.carousellayoutmanager.CarouselLayoutManager
import com.azoft.carousellayoutmanager.CarouselZoomPostLayoutListener
import com.azoft.carousellayoutmanager.CenterScrollListener
import com.kimjisub.launchpad.R.layout
import com.kimjisub.launchpad.adapter.ThemeAdapter
import com.kimjisub.launchpad.adapter.ThemeItem
import com.kimjisub.launchpad.adapter.ThemeTool
import com.kimjisub.manager.Log
import kotlinx.android.synthetic.main.activity_theme.*
import org.jetbrains.anko.browse
import java.util.*

class ThemeActivity : BaseActivity() {
	val list: ArrayList<ThemeItem> by lazy { ThemeTool.getThemePackList(applicationContext) }

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
			adapter = ThemeAdapter(list)

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
		if (list.size != i)
			preference.selectedTheme = list[i].package_name
		else
			browse("https://play.google.com/store/search?q=com.kimjisub.launchpad.theme.")
	}

	private fun getSavedTheme(): Int {
		var ret = 0
		val selectedThemePackageName: String = preference.selectedTheme
		var i = 0
		for (themeItem in list) {
			Log.log(selectedThemePackageName + ", " + themeItem.package_name)
			if (themeItem.package_name == selectedThemePackageName) {
				ret = i
				break
			}
			i++
		}
		return ret
	}
}