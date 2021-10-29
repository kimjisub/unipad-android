package com.kimjisub.launchpad.activity.settings

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter

class CategoryListAdapter(context: Context, val list: Array<CategoryItemData>) :
	ArrayAdapter<CategoryItemData>(context, 0, list) {
	override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
		var view = convertView
		if (view == null || view !is CategoryItemView) {
			view = CategoryItemView(context)
		}

		val currentItem = list[position]

		val b = view.b

		b.title.text = currentItem.title
		b.icon.setImageResource(currentItem.icon)

		return view
	}
}