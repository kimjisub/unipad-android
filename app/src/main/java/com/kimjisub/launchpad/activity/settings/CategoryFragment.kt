package com.kimjisub.launchpad.activity.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ListView
import androidx.fragment.app.ListFragment
import androidx.preference.PreferenceFragmentCompat
import com.kimjisub.launchpad.R
import com.kimjisub.launchpad.databinding.FragmentSettingsCategoryBinding
import com.kimjisub.launchpad.tool.Log

class CategoryFragment(val launchPreference: (PreferenceFragmentCompat) -> Unit) :
	ListFragment() {
	private lateinit var b: FragmentSettingsCategoryBinding

	private val itemList = arrayOf(
		CategoryItemData("asdf", R.drawable.ic_chain_24dp) { SettingsActivity.MessagesFragment() },
		CategoryItemData("asdf", R.drawable.ic_chain_24dp) { SettingsActivity.SyncFragment() },
	)

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		val b = FragmentSettingsCategoryBinding.inflate(inflater)

		listAdapter = CategoryListAdapter(
			requireContext(),
			itemList
		)

		return b.root
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		listView.choiceMode = ListView.CHOICE_MODE_SINGLE
	}

	override fun onListItemClick(l: ListView, v: View, position: Int, id: Long) {
		val currentItem = itemList[position]

		launchPreference(currentItem.FragmentCreator())


		super.onListItemClick(l, v, position, id)
	}

	override fun onContextItemSelected(item: MenuItem): Boolean {
		Log.test("onContextItemSelected: ${listView.checkedItemPosition}")

		return true
	}

	override fun onOptionsItemSelected(item: MenuItem): Boolean {
		Log.test("onOptionsItemSelected: ${listView.checkedItemPosition}")
		return true
	}

}