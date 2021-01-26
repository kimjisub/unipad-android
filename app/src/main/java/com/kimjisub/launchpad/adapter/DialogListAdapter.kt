package com.kimjisub.launchpad.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import com.kimjisub.launchpad.R.layout
import com.kimjisub.launchpad.databinding.ItemSettingBinding

data class DialogListItem(
	val title: String,
	val subtitle: String,
	val iconResId: Int? = null
)


class DialogListAdapter(
	private val list: Array<DialogListItem>
) : BaseAdapter() {

	override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
		val view = LayoutInflater.from(parent.context).inflate(layout.item_setting, parent, false)
		val bind = ItemSettingBinding.bind(view)

		val item = list[position]

		bind.data = item

		return bind.root
	}

	override fun getCount() = list.size

	override fun getItem(position: Int) = list[position].title

	override fun getItemId(position: Int) = position.toLong()

}