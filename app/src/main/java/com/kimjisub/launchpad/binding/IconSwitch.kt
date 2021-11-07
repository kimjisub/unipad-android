package com.kimjisub.launchpad.binding

import androidx.databinding.BindingAdapter
import androidx.databinding.InverseBindingAdapter
import androidx.databinding.InverseBindingListener
import com.polyak.iconswitch.IconSwitch

@BindingAdapter("checked")
fun setChecked(view: IconSwitch, checked: Boolean) {
	view.checked = if (checked) IconSwitch.Checked.RIGHT else IconSwitch.Checked.LEFT
}

@BindingAdapter("checkedAttrChanged")
fun setCheckedListeners(
	view: IconSwitch,
	attrChange: InverseBindingListener,
) {
	view.setCheckedChangeListener {
		attrChange.onChange()
	}
}

@InverseBindingAdapter(attribute = "checked", event = "checkedAttrChanged")
fun getChecked(view: IconSwitch): Boolean {
	return view.checked == IconSwitch.Checked.RIGHT
}