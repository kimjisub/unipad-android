package com.kimjisub.launchpad.fragment

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import com.kimjisub.launchpad.databinding.FragmentMainTotalPanelBinding
import com.kimjisub.launchpad.tool.observeEvent
import com.kimjisub.launchpad.viewmodel.MainTotalPanelViewModel

class MainTotalPanelFragment : BaseFragment() {
	private var _b: FragmentMainTotalPanelBinding? = null
	private val b get() = _b!!
	private val vm: MainTotalPanelViewModel by viewModels()

	var sort: Pair<MainTotalPanelViewModel.SortMethod, Boolean>? = null

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
			callbacks?.onSortChangeListener(it)
		}

		return b.root
	}

	fun update() {
		vm.update()
	}

	private var callbacks: Callbacks? = null

	interface Callbacks {
		fun onSortChangeListener(sort: Pair<MainTotalPanelViewModel.SortMethod, Boolean>)
	}

	override fun onAttach(context: Context) {
		super.onAttach(context)
		callbacks = context as Callbacks?
	}

	override fun onDetach() {
		super.onDetach()
		callbacks = null
	}


	override fun onDestroyView() {
		super.onDestroyView()
		_b = null
	}
}
