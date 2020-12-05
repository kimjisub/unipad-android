package com.kimjisub.design.panel

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.AdapterView
import android.widget.RelativeLayout
import androidx.databinding.DataBindingUtil
import androidx.databinding.ObservableField
import com.kimjisub.design.R.layout
import com.kimjisub.design.R.styleable
import com.kimjisub.design.databinding.PanelMainTotalBinding
import com.kimjisub.manager.Log
import com.kimjisub.manager.extra.addOnPropertyChanged
import kotlinx.android.synthetic.main.panel_main_total.view.*

class MainTotalPanel
@JvmOverloads
constructor(
	context: Context,
	attrs: AttributeSet? = null,
	defStyleAttr: Int = 0
) : RelativeLayout(context, attrs, defStyleAttr) {

	/*@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	constructor(
		context: Context,
		attrs: AttributeSet?,
		defStyleAttr: Int,
		defStyleRes: Int)
			: super(context, attrs, defStyleAttr, defStyleRes)*/

	private val b: PanelMainTotalBinding =
		DataBindingUtil.inflate(LayoutInflater.from(context), layout.panel_main_total, this, true)
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
		/*LayoutInflater.from(context)
			.inflate(layout.panel_main_total, this, true)
		b = PanelMainTotalBinding.bind(this)*/

		b.data = data

		attrs?.let {
			val typedArray =
				context.obtainStyledAttributes(it, styleable.TotalPanel, defStyleAttr, 0)

			// code

			typedArray.recycle()
		}

		spinner_sort_method.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
			override fun onItemSelected(p0: AdapterView<*>?, p1: View?, sortMethod: Int, p3: Long) {
				setSort(sortMethod)
			}

			override fun onNothingSelected(p0: AdapterView<*>?) {
			}

		}

		switch_sort_type.setOnCheckedChangeListener { _, b ->
			setSort(
				spinner_sort_method.selectedItemPosition,
				b
			)
		}

		data.sortMethod.addOnPropertyChanged {
			Log.test("method changed : ${it.get()}")

		}
		data.sortType.addOnPropertyChanged {
			Log.test("type changed : ${it.get()}")
		}

//		radioButtonGroupTableLayout.onCheckedChangeListener = {
//			data.sortingMethod.set((it.tag as String).toInt())
//		}

		/*themeSpinner.onItemSelectedListener { parent, view, position, id ->
			data.selectedTheme.set(position)
		}

		themeSpinner.setItem*/
	}

	private fun setSort(sortMethod: Int) {
		val defaultSortTypes = arrayOf(true, true, true, false, false)
		val sortType = defaultSortTypes[sortMethod]
		setSort(sortMethod, sortType)
	}

	private fun setSort(sortMethod: Int, sortType: Boolean) {
		data.sortMethod.set(sortMethod)
		data.sortType.set(sortType)
		data.sort.set(sortMethod * 2 + if (sortType) 0 else 1)
	}

}