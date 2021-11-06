package com.kimjisub.launchpad.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.kimjisub.launchpad.databinding.FragmentMainPackPanelBinding
import com.kimjisub.launchpad.databinding.FragmentMainTotalPanelBinding

class MainTotalPanelFragment : Fragment() {
	private var _b: FragmentMainTotalPanelBinding? = null
	private val b get() = _b!!
	private val model: MainTotalPanelViewModel by viewModels()

	override fun onCreateView(
		inflater: LayoutInflater, container: ViewGroup?,
		savedInstanceState: Bundle?,
	): View {
		_b = FragmentMainTotalPanelBinding.inflate(inflater, container, false)
		b.apply {
			lifecycleOwner = this@MainTotalPanelFragment
			viewModel = model
		}

		return b.root
	}

	override fun onDestroyView() {
		super.onDestroyView()
		_b = null
	}
}
