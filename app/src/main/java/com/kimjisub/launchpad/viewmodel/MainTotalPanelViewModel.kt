@file:Suppress("EmptyMethod") // private set on mutableStateOf generates empty setter bytecode

package com.kimjisub.launchpad.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kimjisub.launchpad.BuildConfig
import com.kimjisub.launchpad.R
import com.kimjisub.launchpad.adapter.UniPackItem
import com.kimjisub.launchpad.db.repository.UnipackRepository
import com.kimjisub.launchpad.manager.FileManager
import com.kimjisub.launchpad.manager.PreferenceManager
import com.kimjisub.launchpad.manager.WorkspaceManager
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject


class MainTotalPanelViewModel(
	app: Application,
	private val p: PreferenceManager,
	private val ws: WorkspaceManager,
) : AndroidViewModel(app) {
	private val repo: UnipackRepository by app.inject()

	private val sortMethodList = arrayOf(
		SortMethod(app.getString(R.string.sort_title), false) { a, b ->
			-a.unipack.title.compareTo(b.unipack.title)
		},
		SortMethod(app.getString(R.string.sort_producer), false) { a, b ->
			-a.unipack.producerName.compareTo(b.unipack.producerName)
		},
		SortMethod(app.getString(R.string.sort_download_date), true) { a, b ->
			val aDate = a.unipack.lastModified()
			val bDate = b.unipack.lastModified()
			-aDate.compareTo(bDate)
		}
	)
	val sortMethodTitleList = sortMethodList.map { it.name }

	var version by mutableStateOf("")
		private set
	var premium by mutableStateOf(false)

	var updateAvailable by mutableStateOf(false)

	var unipackCount by mutableStateOf<Int?>(null)
		private set
	var unipackCapacity by mutableStateOf<String?>(null)
		private set
	val openCount = repo.totalOpenCount()

	var sortMethod by mutableIntStateOf(0)
		private set

	var sortOrder by mutableStateOf(true)
		private set

	var onSortChanged: ((SortMethod, Boolean) -> Unit)? = null

	fun updateSortMethod(value: Int) {
		sortMethod = value
		p.sortMethod = value
		sortOrder = sortMethodList[value].defaultOrder
		p.sortOrder = sortOrder
		sortChange()
	}

	fun updateSortOrder(value: Boolean) {
		sortOrder = value
		p.sortOrder = value
		sortChange()
	}

	init {
		version = BuildConfig.VERSION_NAME

		sortMethod = p.sortMethod.coerceAtMost(sortMethodList.size - 1)
		sortOrder = p.sortOrder
	}

	fun update() {
		viewModelScope.launch {
			unipackCapacity = null
			val size = ws.getAvailableWorkspacesSize()
			unipackCapacity = FileManager.byteToMB(size, "%.0f")
		}

		viewModelScope.launch {
			unipackCount = null
			unipackCount = ws.getUnipacks().size
		}
	}

	// sort
	private var prevSortMethod: Int? = null
	private var prevSortOrder: Boolean? = null

	private fun sortChange() {
		if (sortMethod != prevSortMethod || sortOrder != prevSortOrder)
			onSortChanged?.invoke(sortMethodList[sortMethod], sortOrder)

		prevSortMethod = sortMethod
		prevSortOrder = sortOrder

	}

	data class SortMethod(
		val name: String,
		val defaultOrder: Boolean,
		val comparator: Comparator<UniPackItem>,
	)

	class Factory(
		private val app: Application,
		private val p: PreferenceManager,
		private val ws: WorkspaceManager,
	) : ViewModelProvider.Factory {

		@Suppress("UNCHECKED_CAST")
		override fun <T : ViewModel> create(modelClass: Class<T>): T {
			return MainTotalPanelViewModel(app, p, ws) as T
		}
	}
}
