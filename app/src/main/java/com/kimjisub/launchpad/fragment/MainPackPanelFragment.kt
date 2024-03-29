package com.kimjisub.launchpad.fragment

import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.ViewModelProvider
import com.kimjisub.launchpad.R
import com.kimjisub.launchpad.adapter.UniPackItem
import com.kimjisub.launchpad.databinding.FragmentMainPackPanelBinding
import com.kimjisub.launchpad.tool.observeEvent
import com.kimjisub.launchpad.viewmodel.MainPackPanelViewModel

class MainPackPanelFragment(private val unipackItem: UniPackItem) : BaseFragment() {
	private var _b: FragmentMainPackPanelBinding? = null
	private val b get() = _b!!
	private lateinit var vm: MainPackPanelViewModel

	private var callbacks: Callbacks? = null

	interface Callbacks {
		fun onDelete()
	}

	override fun onCreateView(
		inflater: LayoutInflater, container: ViewGroup?,
		savedInstanceState: Bundle?,
	): View {
		vm = ViewModelProvider(this,
			MainPackPanelViewModel.Factory(
				requireActivity().application,
				unipackItem.unipack
			))[MainPackPanelViewModel::class.java]
		_b = FragmentMainPackPanelBinding.inflate(inflater, container, false)
		b.apply {
			lifecycleOwner = this@MainPackPanelFragment
			vm = this@MainPackPanelFragment.vm
		}

		vm.eventDelete.observeEvent(viewLifecycleOwner) {

			AlertDialog.Builder(requireContext())
				.setTitle(R.string.warning)
				.setMessage(R.string.doYouWantToDeleteUniPack)
				.setPositiveButton(R.string.accept) { _: DialogInterface?, _: Int ->
					unipackItem.unipack.delete()
					callbacks?.onDelete()
				}.setNegativeButton(R.string.cancel, null)
				.show()
		}

		// 텍스트가 흘러갈 수 있도록 선택
		b.title.isSelected = true
		b.subtitle.isSelected = true
		b.path.isSelected = true

		return b.root
	}

	// Lifecycle

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
