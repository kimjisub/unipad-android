package com.kimjisub.launchpad.activity

import android.os.Bundle
import com.azoft.carousellayoutmanager.CarouselLayoutManager
import com.azoft.carousellayoutmanager.CarouselZoomPostLayoutListener
import com.azoft.carousellayoutmanager.CenterScrollListener
import com.kimjisub.launchpad.adapter.ThemeAdapter
import com.kimjisub.launchpad.adapter.ThemeItem
import com.kimjisub.launchpad.adapter.ThemeTool
import com.kimjisub.launchpad.databinding.ActivityThemeBinding
import com.kimjisub.launchpad.tool.Log
import com.kimjisub.launchpad.tool.splitties.browse

class ThemeActivity : BaseActivity() {
	private lateinit var b: ActivityThemeBinding
	val list: ArrayList<ThemeItem> by lazy { ThemeTool.getThemePackList(applicationContext) }

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		b = ActivityThemeBinding.inflate(layoutInflater)
		setContentView(b.root)
	}

	override fun onResume() {
		super.onResume()
		val manager = CarouselLayoutManager(CarouselLayoutManager.HORIZONTAL, false).apply {
			setPostLayoutListener(CarouselZoomPostLayoutListener())
		}

		b.list.apply {
			layoutManager = manager
			adapter = ThemeAdapter(list)

			setHasFixedSize(true)
			addOnScrollListener(CenterScrollListener())
		}

		manager.scrollToPosition(getSavedTheme())

		b.apply.setOnClickListener {
			selectTheme(manager.centerItemPosition)
			finish()
		}
	}

	private fun selectTheme(i: Int) {
		if (list.size != i)
			p.selectedTheme = list[i].package_name
		else
			browse("https://play.google.com/store/search?q=com.kimjisub.launchpad.theme.")
	}

	private fun getSavedTheme(): Int {
		var ret = 0
		val selectedThemePackageName: String = p.selectedTheme
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