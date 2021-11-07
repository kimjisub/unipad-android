package com.kimjisub.launchpad.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import com.kimjisub.launchpad.databinding.FragmentMainTotalPanelBinding
import com.kimjisub.launchpad.tool.Log
import com.kimjisub.launchpad.tool.observeEvent

class MainTotalPanelFragment : BaseFragment() {
	private var _b: FragmentMainTotalPanelBinding? = null
	private val b get() = _b!!
	private val vm: MainTotalPanelViewModel by viewModels()

	override fun onCreateView(
		inflater: LayoutInflater, container: ViewGroup?,
		savedInstanceState: Bundle?,
	): View {
		_b = FragmentMainTotalPanelBinding.inflate(inflater, container, false)
		b.apply {
			lifecycleOwner = this@MainTotalPanelFragment
			vm = this@MainTotalPanelFragment.vm
		}

		vm.eventSort.observeEvent(viewLifecycleOwner){
			// todo MainActivity에 정렬 요청
			val comparator = it.first.comparator
			val sortOrder = it.second

			Log.test("DO SORT: ${it.first.name}, $sortOrder")
		}


		/*vm.selectedTheme.observe(viewLifecycleOwner) { selectedThemeIndex->
			p.selectedTheme = themeItemList!![selectedThemeIndex].package_name
		}*/

		return b.root
	}






	override fun onDestroyView() {
		super.onDestroyView()
		_b = null
	}
}
