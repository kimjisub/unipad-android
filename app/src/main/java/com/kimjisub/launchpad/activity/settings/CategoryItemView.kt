package com.kimjisub.launchpad.activity.settings

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.Checkable
import androidx.constraintlayout.widget.ConstraintLayout
import com.kimjisub.launchpad.databinding.ItemSettingsCategoryBinding

class CategoryItemView
@JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet? = null,
	defStyleAttr: Int = 0
) :
	ConstraintLayout(context, attrs, defStyleAttr), Checkable {
	val b: ItemSettingsCategoryBinding =
		ItemSettingsCategoryBinding.inflate(LayoutInflater.from(context), this, true)

	override fun setChecked(checked: Boolean) {
		if(b.checkbox.isChecked != checked){
			b.checkbox.isChecked = checked
		}
	}

	override fun isChecked(): Boolean {
		return b.checkbox.isChecked
	}

	override fun toggle() {
		isChecked = !b.checkbox.isChecked
	}

}