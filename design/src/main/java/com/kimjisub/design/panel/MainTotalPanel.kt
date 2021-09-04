package com.kimjisub.design.panel

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.RelativeLayout
import androidx.databinding.ObservableField
import com.kimjisub.design.R
import com.kimjisub.design.R.styleable
import com.kimjisub.design.databinding.PanelMainTotalBinding
import com.kimjisub.manager.extra.addOnPropertyChanged
import com.polyak.iconswitch.IconSwitch

class MainTotalPanel
@JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet? = null,
	defStyleAttr: Int = 0
) : RelativeLayout(context, attrs, defStyleAttr) {
	private val b: PanelMainTotalBinding = PanelMainTotalBinding.bind(this)
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

		val sortMethod: ObservableField<Int> = ObservableField(0) // 0~5
		val sortType: ObservableField<Boolean> = ObservableField(false)
		val sort: ObservableField<Int> = ObservableField(0)
	}


	init {
		b.data = data

		attrs?.let {
			val typedArray =
				context.obtainStyledAttributes(it, styleable.TotalPanel, defStyleAttr, 0)

			// code

			typedArray.recycle()
		}

		b.spinnerSortMethod.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
			override fun onItemSelected(p0: AdapterView<*>?, p1: View?, sortMethod: Int, p3: Long) {
				setSort(sortMethod)
			}

			override fun onNothingSelected(p0: AdapterView<*>?) {
			}

		}

		b.sortTypeSwitch.setCheckedChangeListener { current ->
			setSort(
				b.spinnerSortMethod.selectedItemPosition,
				current == IconSwitch.Checked.RIGHT
			)
		}

		data.sortType.addOnPropertyChanged {
			b.sortTypeSwitch.checked =
				if (it.get() == true) IconSwitch.Checked.RIGHT else IconSwitch.Checked.LEFT
		}

		b.themeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
			override fun onItemSelected(
				parent: AdapterView<*>?,
				view: View?,
				position: Int,
				id: Long
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
		//super.addView(child, index, params)
		//		try{
//
//			b.rootView.addView(child, index, params)
//		}catch (e:Exception){
//			super.addView(child, index, params)
//		}
	}

	private fun setSort(sortMethod: Int) {
		val defaultSortTypes = arrayOf(false, false, true, true, true)
		val sortType = defaultSortTypes[sortMethod]
		setSort(sortMethod, sortType)
	}

	private fun setSort(sortMethod: Int, sortType: Boolean) {
		data.sortMethod.set(sortMethod)
		data.sortType.set(sortType)
		data.sort.set(sortMethod * 2 + if (sortType) 0 else 1)
	}

}