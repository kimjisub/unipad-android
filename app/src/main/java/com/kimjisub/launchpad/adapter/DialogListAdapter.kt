package com.kimjisub.launchpad.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import com.kimjisub.launchpad.databinding.ItemSettingBinding

data class DialogListItem(
	val title: String,
	val subtitle: String,
	val iconResId: Int? = null,
)


class DialogListAdapter(
	private val list: Array<DialogListItem>,
) : BaseAdapter() {

	override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
		val bind: ItemSettingBinding = if (convertView != null) {
			ItemSettingBinding.bind(convertView)
		} else {
			ItemSettingBinding.inflate(LayoutInflater.from(parent.context), parent, false)
		}

		val item = list[position]
		bind.title.text = item.title
		bind.subtitle.text = item.subtitle
		if (item.iconResId != null) {
			bind.icon.setImageResource(item.iconResId)
			bind.icon.visibility = View.VISIBLE
		} else {
			bind.icon.setImageDrawable(null)
			bind.icon.visibility = View.GONE
		}

		return bind.root
	}

	override fun getCount() = list.size

	override fun getItem(position: Int) = list[position].title

	override fun getItemId(position: Int) = position.toLong()

}
