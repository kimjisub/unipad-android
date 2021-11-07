package com.kimjisub.launchpad.fragment

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import com.kimjisub.launchpad.BuildConfig
import com.kimjisub.launchpad.R
import com.kimjisub.launchpad.adapter.UniPackItem
import com.kimjisub.launchpad.db.AppDataBase
import com.kimjisub.launchpad.manager.FileManager
import com.kimjisub.launchpad.manager.PreferenceManager
import com.kimjisub.launchpad.manager.WorkspaceManager
import com.kimjisub.launchpad.tool.Event
import com.kimjisub.launchpad.tool.Log
import com.kimjisub.launchpad.tool.emit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*


class MainTotalPanelViewModel(val app: Application) : AndroidViewModel(app) {
	private val db: AppDataBase by lazy { AppDataBase.getInstance(app.baseContext)!! }
	private val p by lazy { PreferenceManager(app.baseContext) }
	private val ws by lazy { WorkspaceManager(app.baseContext) }


	private val sortMethodList = arrayOf(
		SortMethod(string(R.string.sort_title), false) { a, b ->
			-a.unipack.title.compareTo(b.unipack.title)
		},
		SortMethod(string(R.string.sort_producer), false) { a, b ->
			-a.unipack.producerName.compareTo(b.unipack.producerName)
		},
		SortMethod(string(R.string.sort_play_count), true) { a, b ->
			val aCount = db.unipackOpenDAO()!!.getCountSync(a.unipack.id)
			val bCount = db.unipackOpenDAO()!!.getCountSync(b.unipack.id)
			-aCount.compareTo(bCount)
		},
		SortMethod(string(R.string.sort_last_opened_date), true) { a, b ->
			val aDate = db.unipackOpenDAO()!!
				.getLastOpenedDateSync(a.unipack.id)?.created_at ?: Date(0)
			val bDate = db.unipackOpenDAO()!!
				.getLastOpenedDateSync(b.unipack.id)?.created_at ?: Date(0)
			-aDate.compareTo(bDate)
		},
		SortMethod(string(R.string.sort_download_date), true) { a, b ->
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
	val openCount = MutableLiveData<Int>()
	val themeList = MutableLiveData<ArrayList<String>>()
	val selectedTheme = MutableLiveData<Int>()
	val sortMethod = MutableLiveData<Int>()
	val sortOrder = MutableLiveData<Boolean>()
	val eventSort = MutableLiveData<Event<Pair<SortMethod, Boolean>>>()

	var sortLoaded = false

	init {
		Log.test("vm init")
		version.value = BuildConfig.VERSION_NAME
		unipackCount.value = ws.getUnipacks().size
		db.unipackOpenDAO()!!.count.observeForever {
			openCount.value = it ?: 0
		}
		CoroutineScope(Dispatchers.IO).launch {
			val size = FileManager.getFolderSize(
				ws.mainWorkspace.file // todo 여러 workspace의 용량 계산, WorkspaceManager로 이동하기
			)

			withContext(Dispatchers.Main) {
				unipackCapacity.value = FileManager.byteToMB(size, "%.0f")
			}
		}

		// sort

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


		// theme
	}

	// sort

	var prevSortMethod: Int? = null
	var prevSortOrder: Boolean? = null

	private fun sortChange() {
		if (sortMethod.value!! != prevSortMethod || sortOrder.value!! != prevSortOrder) {
			Log.test("${p.sortMethod}, ${p.sortOrder}")
			eventSort.emit(Pair(sortMethodList[sortMethod.value!!], sortOrder.value!!))
		}
		prevSortMethod = sortMethod.value
		prevSortOrder = sortOrder.value

	}

	// theme

	/*var themeItemList: ArrayList<ThemeItem>? = null
	var themeNameList: ArrayList<String>? = null
	private fun updateThemeList() {
		themeItemList = ThemeTool.getThemePackList(requireContext())
		themeNameList = ArrayList()
		for (item: ThemeItem in themeItemList!!)
			themeNameList!!.add(item.name)
	}*/

	fun update() {
		// updateThemeList()

		/*try {
			val index = themeItemList!!.indexOfFirst { it.package_name == p.selectedTheme }
			b.totalPanel.data.selectedTheme.set(index)
		} catch (e: Exception) {
			b.totalPanel.data.selectedTheme.set(0)
		}*/

	}


	fun string(id: Int): String = app.getString(id)


	data class SortMethod(
		val name: String,
		val defaultOrder: Boolean,
		val comparator: Comparator<UniPackItem>,
	)
}
