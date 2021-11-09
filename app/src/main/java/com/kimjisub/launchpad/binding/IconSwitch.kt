package com.kimjisub.launchpad.binding

import androidx.databinding.BindingAdapter
import androidx.databinding.InverseBindingAdapter
import androidx.databinding.InverseBindingListener
import com.polyak.iconswitch.IconSwitch

@BindingAdapter("checkedBoolean")
fun setChecked(view: IconSwitch, checked: Boolean) {
	view.checked = if (checked) IconSwitch.Checked.RIGHT else IconSwitch.Checked.LEFT
}

@BindingAdapter("checkedBooleanAttrChanged")
fun setCheckedListeners(
	view: IconSwitch,
	attrChange: InverseBindingListener,
) {
	view.setCheckedChangeListener {
		attrChange.onChange()
	}
}

@InverseBindingAdapter(attribute = "checkedBoolean", event = "checkedBooleanAttrChanged")
fun getChecked(view: IconSwitch): Boolean {
	return view.checked == IconSwitch.Checked.RIGHT
}