package com.kimjisub.design.panel

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.RelativeLayout
import androidx.databinding.ObservableField
import com.kimjisub.design.R
import com.kimjisub.design.R.styleable
import com.kimjisub.design.databinding.PanelMainTotalBinding
import com.polyak.iconswitch.IconSwitch

class MainTotalPanel
@JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet? = null,
	defStyleAttr: Int = 0,
) : RelativeLayout(context, attrs, defStyleAttr) {
	private val b: PanelMainTotalBinding =
		PanelMainTotalBinding.inflate(LayoutInflater.from(context), this, true)
	val data = Data()

	class Data {
		val logo: ObservableField<Drawable> = ObservableField()
		val version: ObservableField<String> = ObservableField()
		val premium: ObservableField<Boolean> = ObservableField()

		val unipackCount: ObservableField<String> = ObservableField()
		val unipackCapacity: ObservableField<String> = ObservableField()
		val openCount: ObservableField<String> = ObservableField()
		val themeList: ObservableField<ArrayList<String>> = ObservableField()
		val selectedTheme: ObservableField<Int> = ObservableField()
	}

	var sortMethod: Int = 0
		set(value) {
			field = value
			b.spinnerSortMethod.setSelection(field)
			onSortChangeListener?.onSortMethodChange(field)
		}

	var sortOrder: Boolean = false
		set(value) {
			field = value
			b.sortOrder.checked = if (field) IconSwitch.Checked.RIGHT else IconSwitch.Checked.LEFT
			onSortChangeListener?.onSortOrderChange(field)
		}

	var onSortChangeListener: OnSortChangeListener? = null

	init {
		b.data = data

		attrs?.let {
			val typedArray =
				context.obtainStyledAttributes(it, styleable.TotalPanel, defStyleAttr, 0)

			// code

			typedArray.recycle()
		}

		b.spinnerSortMethod.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
			override fun onItemSelected(p0: AdapterView<*>?, p1: View?, index: Int, p3: Long) {
				sortMethod = index
			}

			override fun onNothingSelected(p0: AdapterView<*>?) {
			}

		}

		b.sortOrder.setCheckedChangeListener { current ->
			sortOrder = current == IconSwitch.Checked.RIGHT
		}

		b.themeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
			override fun onItemSelected(
				parent: AdapterView<*>?,
				view: View?,
				position: Int,
				id: Long,
			) {
				data.selectedTheme.set(position)
			}

			override fun onNothingSelected(parent: AdapterView<*>?) {
			}

		}
	}

	override fun addView(child: View?, index: Int, params: ViewGroup.LayoutParams?) {
		when (child?.id) {
			R.id.rootView -> {
				super.addView(child, index, params)
			}
			else -> {
				b.contentRoot.addView(child, index, params)
			}
		}
		/*super.addView(child, index, params)
				try{

			b.rootView.addView(child, index, params)
		}catch (e:Exception){
			super.addView(child, index, params)
		}*/
	}

	interface OnSortChangeListener {
		fun onSortMethodChange(sortMethod: Int)
		fun onSortOrderChange(sortOrder: Boolean)
	}
}