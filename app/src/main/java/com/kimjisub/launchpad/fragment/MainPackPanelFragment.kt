package com.kimjisub.launchpad.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.kimjisub.launchpad.adapter.UniPackItem
import com.kimjisub.launchpad.databinding.FragmentMainPackPanelBinding

class MainPackPanelFragment(val unipackItem: UniPackItem) : Fragment() {
	private var _b: FragmentMainPackPanelBinding? = null
	private val b get() = _b!!
	private val model: MainPackPanelViewModel by viewModels()

	override fun onCreateView(
		inflater: LayoutInflater, container: ViewGroup?,
		savedInstanceState: Bundle?,
	): View {
		_b = FragmentMainPackPanelBinding.inflate(inflater, container, false)
		b.apply {
			lifecycleOwner = this@MainPackPanelFragment
			vm = model
		}

		b.title.isSelected = true
		b.subtitle.isSelected = true
		b.path.isSelected = true

		model.setUniPack(unipackItem.unipack)

		return b.root
	}

	override fun onDestroyView() {
		super.onDestroyView()
		_b = null
	}
}
