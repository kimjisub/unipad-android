package com.kimjisub.launchpad.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import com.kimjisub.launchpad.databinding.FragmentMainTotalPanelBinding
import com.kimjisub.launchpad.tool.Log
import com.kimjisub.launchpad.tool.observeEvent

class MainTotalPanelFragment(val sortChangeListener: (Pair<MainTotalPanelViewModel.SortMethod, Boolean>) -> Unit) :
	BaseFragment() {
	private var _b: FragmentMainTotalPanelBinding? = null
	private val b get() = _b!!
	private val vm: MainTotalPanelViewModel by viewModels()

	var sort : Pair<MainTotalPanelViewModel.SortMethod, Boolean>? = null

	override fun onCreateView(
		inflater: LayoutInflater, container: ViewGroup?,
		savedInstanceState: Bundle?,
	): View {
		_b = FragmentMainTotalPanelBinding.inflate(inflater, container, false)
		b.apply {
			lifecycleOwner = this@MainTotalPanelFragment
			vm = this@MainTotalPanelFragment.vm
		}

		vm.eventSort.observeEvent(viewLifecycleOwner) {
			sort = it
			sortChangeListener(it)
		}

		return b.root
	}


	override fun onDestroyView() {
		super.onDestroyView()
		_b = null
	}
}
