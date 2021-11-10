package com.kimjisub.launchpad.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.kimjisub.launchpad.BuildConfig
import com.kimjisub.launchpad.R
import com.kimjisub.launchpad.adapter.UniPackItem
import com.kimjisub.launchpad.db.repository.UniPackRepository
import com.kimjisub.launchpad.manager.FileManager
import com.kimjisub.launchpad.manager.PreferenceManager
import com.kimjisub.launchpad.manager.WorkspaceManager
import com.kimjisub.launchpad.tool.Event
import com.kimjisub.launchpad.tool.emit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import java.lang.reflect.InvocationTargetException
import java.util.*


class MainTotalPanelViewModel(
	app: Application,
	private val p: PreferenceManager,
	private val ws: WorkspaceManager,
) : AndroidViewModel(app) {
	val repo: UniPackRepository by app.inject()

	private val sortMethodList = arrayOf(
		SortMethod(app.getString(R.string.sort_title), false) { a, b ->
			-a.unipack.title.compareTo(b.unipack.title)
		},
		SortMethod(app.getString(R.string.sort_producer), false) { a, b ->
			-a.unipack.producerName.compareTo(b.unipack.producerName)
		},
		SortMethod(app.getString(R.string.sort_play_count), true) { a, b ->
			val aCount = a.unipackENT.value?.openCount!!//repo.openCountSync(a.unipack.id)
			val bCount = b.unipackENT.value?.openCount!!//repo.openCountSync(b.unipack.id)
			-aCount.compareTo(bCount)
		},
		SortMethod(app.getString(R.string.sort_last_opened_date), true) { a, b ->
			val aDate = a.unipackENT.value?.lastOpenedAt
				?: Date(0) // repo.getLastOpenedDateSync(a.unipack.id)?.createdAt ?: Date(0)
			val bDate = b.unipackENT.value?.lastOpenedAt
				?: Date(0)// repo.getLastOpenedDateSync(b.unipack.id)?.createdAt ?: Date(0)
			-aDate.compareTo(bDate)
		},
		SortMethod(app.getString(R.string.sort_download_date), true) { a, b ->
			val aDate = a.unipack.lastModified()
			val bDate = b.unipack.lastModified()
			-aDate.compareTo(bDate)
		}
	)
	val sortMethodTitleList = sortMethodList.map { return@map it.name }

	val version = MutableLiveData<String>()
	val premium = MutableLiveData<Boolean>()

	val unipackCount = MutableLiveData<Int>()
	val unipackCapacity = MutableLiveData<String>()
	val openCount = repo.openCount()
	val sortMethod = MutableLiveData<Int>()
	val sortOrder = MutableLiveData<Boolean>()
	val eventSort = MutableLiveData<Event<Pair<SortMethod, Boolean>>>()

	init {
		version.value = BuildConfig.VERSION_NAME

		sortMethod.value = p.sortMethod.coerceAtMost(sortMethodList.size - 1)
		sortOrder.value = p.sortOrder

		sortMethod.observeForever {
			p.sortMethod = it
			sortOrder.value = sortMethodList[sortMethod.value!!].defaultOrder

		}
		sortOrder.observeForever {
			p.sortOrder = it
			sortChange()
		}
	}

	fun update() {
		CoroutineScope(Dispatchers.Main).launch {
			val size = ws.getActiveWorkspacesSize()
			unipackCapacity.value = FileManager.byteToMB(size, "%.0f")
		}

		CoroutineScope(Dispatchers.Main).launch {
			unipackCount.value = ws.getUnipacks().size
		}
	}

	// sort
	private var prevSortMethod: Int? = null
	private var prevSortOrder: Boolean? = null

	private fun sortChange() {
		if (sortMethod.value!! != prevSortMethod || sortOrder.value!! != prevSortOrder)
			eventSort.emit(Pair(sortMethodList[sortMethod.value!!], sortOrder.value!!))

		prevSortMethod = sortMethod.value
		prevSortOrder = sortOrder.value

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
	) : ViewModelProvider.NewInstanceFactory() {

		override fun <T : ViewModel> create(modelClass: Class<T>): T {
			if (ViewModel::class.java.isAssignableFrom(modelClass)) {
				try {
					return modelClass.getConstructor(
						Application::class.java,
						PreferenceManager::class.java,
						WorkspaceManager::class.java)
						.newInstance(app, p, ws)
				} catch (e: NoSuchMethodException) {
					throw RuntimeException("Cannot create an instance of $modelClass", e)
				} catch (e: IllegalAccessException) {
					throw RuntimeException("Cannot create an instance of $modelClass", e)
				} catch (e: InstantiationException) {
					throw RuntimeException("Cannot create an instance of $modelClass", e)
				} catch (e: InvocationTargetException) {
					throw RuntimeException("Cannot create an instance of $modelClass", e)
				}
			}
			return super.create(modelClass)
		}
	}


}


