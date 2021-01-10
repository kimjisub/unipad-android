package com.kimjisub.design.manage

import android.widget.CheckBox
import android.widget.CompoundButton
import java.util.*

class SyncCheckBox(vararg cbs: CheckBox) {
	private val checkBoxes: ArrayList<CheckBox> = ArrayList()

	init {
		for (cb in cbs)
			addCheckBox(cb)
	}

	fun addCheckBox(vararg cbs: CheckBox) {
		for (cb in cbs) {
			cb.setOnCheckedChangeListener { _: CompoundButton?, b: Boolean -> setChecked(b) }
			cb.setOnLongClickListener {
				onLongClick()
				false
			}
			checkBoxes.add(cb)
		}
	}

	// Checked Manage /////////////////////////////////////////////////////////////////////////////////////////

	private var isChecked = false
	var isLocked: Boolean = false
		set(value) {
			field = value
			for (checkBox in checkBoxes) {
				checkBox.isEnabled = !field
				checkBox.alpha = if (!field) 1f else 0.3f
			}
		}

	fun isChecked() = isChecked

	fun toggleChecked() {
		if (!isLocked)
			forceToggleChecked()
	}


	fun setChecked(b: Boolean) {
		if (!isLocked)
			forceSetChecked(b)
		else
			fix()
	}


	fun forceToggleChecked() = forceSetChecked(!isChecked)

	fun forceSetChecked(b: Boolean) {
		isChecked = b
		fix()
		onCheckedChange(b)
	}

	fun fix() {
		for (checkBox in checkBoxes) {
			if (checkBox.isChecked != isChecked) {
				checkBox.setOnCheckedChangeListener(null)
				checkBox.isChecked = isChecked
				checkBox.setOnCheckedChangeListener { _: CompoundButton?, b: Boolean -> setChecked(b) }
			}
		}
	}


	// view /////////////////////////////////////////////////////////////////////////////////////////


	fun setVisibility(visibility: Int) {
		for (checkBox in checkBoxes) checkBox.visibility = visibility
	}


	// listener /////////////////////////////////////////////////////////////////////////////////////////

	var onCheckedChange: OnCheckedChange? = null

	var onLongClick: OnLongClick? = null

	interface OnCheckedChange {
		fun onCheckedChange(b: Boolean)
	}

	interface OnLongClick {
		fun onLongClick()
	}

	private fun onCheckedChange(bool: Boolean) {
		onCheckedChange?.onCheckedChange(bool)
	}

	private fun onLongClick() {
		onLongClick?.onLongClick()
	}
}