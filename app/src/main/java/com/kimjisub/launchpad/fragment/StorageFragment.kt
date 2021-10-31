package com.kimjisub.launchpad.fragment

import android.os.Bundle
import androidx.preference.CheckBoxPreference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.children
import com.kimjisub.launchpad.R
import com.kimjisub.launchpad.manager.PreferenceManager
import com.kimjisub.launchpad.manager.WorkspaceManager

class StorageFragment : PreferenceFragmentCompat() {
	private val p by lazy { PreferenceManager(requireContext()) }
	private val ws by lazy { WorkspaceManager(requireContext()) }

	private val storageListPreferenceCategory: PreferenceCategory by lazy { findPreference("storage_list")!! }

	override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
		setPreferencesFromResource(R.xml.preferences_storage, rootKey)


		initStorage()
	}

	private fun initStorage() {
		storageListPreferenceCategory.removeAll()
		ws.availableWorkspaces.forEach {
			val preference = CheckBoxPreference(context).apply {
				title = it.name
				summary = it.file.path
				isIconSpaceReserved = false
				setOnPreferenceChangeListener { _, newValue ->
					val list = p.storageActive.toMutableSet()
					if (newValue == true)
						list.add(it.file.path)
					else if (list.size > 1)
						list.remove(it.file.path)
					p.storageActive = list
					ws.validateWorkspace()
					updateStorage()

					false
				}
			}
			storageListPreferenceCategory.addPreference(preference)
		}
		updateStorage()
	}

	private fun updateStorage() {
		storageListPreferenceCategory.children.forEach {
			(it as CheckBoxPreference).apply {
				isChecked = p.storageActive.contains(it.summary)
			}
		}
	}
}